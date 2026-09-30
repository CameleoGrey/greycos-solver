package greycos.solver.core.impl.localsearch.decider.gls;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureConsumer;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureSession;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureUpdater;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.WorkingSolutionMutationObserver;

/** Maintains features independently of the business score backend, for one score director. */
public final class GuidedLocalSearchFeatureTracker<Solution_, Key_>
    implements WorkingSolutionMutationObserver<Solution_> {

  // Only accessed at attachment/close, never on the move-evaluation path.
  private static final Map<GuidedLocalSearchFeatureSession<?, ?>, Boolean> ACTIVE_SESSIONS =
      new IdentityHashMap<>();

  private final InnerScoreDirector<Solution_, ?> scoreDirector;
  private final GuidedLocalSearchFeatureProvider<Solution_, Key_> provider;
  private final GuidedLocalSearchFeatureSession<Solution_, Key_> session;
  private final Map<Key_, GuidedLocalSearchNumber> activeFeatures = new HashMap<>();
  private final Map<Key_, GuidedLocalSearchNumber> readOnlyActiveFeatures =
      Collections.unmodifiableMap(activeFeatures);
  private final GuidedLocalSearchFeatureUpdater<Key_> updater = new Updater();
  private GuidedLocalSearchPenaltyTable.Snapshot<Key_> penalties;
  private GuidedLocalSearchNumber aggregate = GuidedLocalSearchNumber.ZERO;
  private boolean initialized;
  private boolean closed;

  private GuidedLocalSearchFeatureTracker(
      InnerScoreDirector<Solution_, ?> scoreDirector,
      GuidedLocalSearchFeatureProvider<Solution_, Key_> provider,
      GuidedLocalSearchPenaltyTable.Snapshot<Key_> penalties) {
    this.scoreDirector = Objects.requireNonNull(scoreDirector);
    this.provider = Objects.requireNonNull(provider);
    this.penalties = Objects.requireNonNull(penalties);
    session =
        Objects.requireNonNull(
            provider.newSession(),
            () ->
                "The GLS feature provider (%s) returned a null session."
                    .formatted(provider.getClass().getName()));
    synchronized (ACTIVE_SESSIONS) {
      if (ACTIVE_SESSIONS.putIfAbsent(session, Boolean.TRUE) != null) {
        throw new IllegalStateException(
            "The GLS feature provider (%s) reused an active session. newSession() must return a fresh instance for every score director."
                .formatted(provider.getClass().getName()));
      }
    }
  }

  public static <Solution_, Key_> GuidedLocalSearchFeatureTracker<Solution_, Key_> attach(
      InnerScoreDirector<Solution_, ?> scoreDirector,
      GuidedLocalSearchFeatureProvider<Solution_, Key_> provider,
      GuidedLocalSearchPenaltyTable.Snapshot<Key_> penalties) {
    if (scoreDirector.getWorkingSolutionMutationObserver() != null) {
      throw new IllegalStateException("A working solution mutation observer is already attached.");
    }
    var tracker = new GuidedLocalSearchFeatureTracker<>(scoreDirector, provider, penalties);
    try {
      scoreDirector.setWorkingSolutionMutationObserver(tracker);
    } catch (RuntimeException | Error failure) {
      try {
        tracker.close();
      } catch (RuntimeException | Error closeFailure) {
        if (closeFailure != failure) {
          failure.addSuppressed(closeFailure);
        }
      }
      throw failure;
    }
    return tracker;
  }

  /** Returns the exact unscaled sum of active feature cost times penalty count. */
  public GuidedLocalSearchNumber aggregate() {
    flush();
    return aggregate;
  }

  /** Read-only current features. The view must not be retained across working-state changes. */
  public Map<Key_, GuidedLocalSearchNumber> activeFeatures() {
    flush();
    return readOnlyActiveFeatures;
  }

  public long penaltyVersion() {
    return penalties.version();
  }

  /**
   * Refreshes a generation at the worker barrier. Reweight cached features before flushing dirty
   * cost replacements, so old costs cannot be subtracted using the new counts from an old total.
   */
  public void updatePenalties(GuidedLocalSearchPenaltyTable.Snapshot<Key_> penalties) {
    ensureOpen();
    this.penalties = Objects.requireNonNull(penalties);
    aggregate = sum(activeFeatures);
  }

  public void reset() {
    ensureOpen();
    workingSolutionChanged();
  }

  @Override
  public void workingSolutionChanged() {
    initialized = false;
    activeFeatures.clear();
    aggregate = GuidedLocalSearchNumber.ZERO;
  }

  private void flush() {
    ensureOpen();
    scoreDirector.updateShadowVariables();
    if (!scoreDirector.isLastVariableUpdateSuccessful()) {
      throw new IllegalStateException(
          "Cannot extract GLS features while shadow variables are inconsistent.");
    }
    try {
      if (!initialized) {
        session.resetWorkingSolution(Objects.requireNonNull(scoreDirector.getWorkingSolution()));
        initialized = true;
      }
      session.flushChanges(updater);
    } catch (RuntimeException | Error failure) {
      // A session can emit several changes and then fail. Never expose its partial state later.
      workingSolutionChanged();
      throw failure;
    }
  }

  public void assertFromScratch() {
    flush();
    var expected = new HashMap<Key_, GuidedLocalSearchNumber>();
    try {
      provider.extractFeatures(
          scoreDirector.getWorkingSolution(),
          new GuidedLocalSearchFeatureConsumer<>() {
            @Override
            public void accept(Key_ key, long cost) {
              put(key, GuidedLocalSearchNumber.of(cost));
            }

            @Override
            public void accept(Key_ key, BigDecimal cost) {
              put(key, decimalCost(key, cost));
            }

            private void put(Key_ key, GuidedLocalSearchNumber cost) {
              validateFeature(key, cost);
              if (expected.putIfAbsent(key, cost) != null) {
                throw new IllegalStateException(
                    "GLS feature provider (%s) extracted duplicate key (%s). Give separate occurrences distinct keys."
                        .formatted(provider.getClass().getName(), key));
              }
            }
          });
    } catch (RuntimeException | Error failure) {
      workingSolutionChanged();
      throw failure;
    }
    if (!expected.equals(activeFeatures)) {
      throw new IllegalStateException(
          "GLS feature corruption in provider (%s): incremental features (%s) differ from independently extracted features (%s)."
              .formatted(
                  provider.getClass().getName(), describe(activeFeatures), describe(expected)));
    }
    var expectedAggregate = sum(expected);
    if (!expectedAggregate.equals(aggregate)) {
      throw new IllegalStateException(
          "GLS aggregate corruption: incremental (%s), independently calculated (%s), penalty version (%d)."
              .formatted(aggregate, expectedAggregate, penalties.version()));
    }
  }

  private static String describe(Map<?, ?> map) {
    return map.size() + " features; first entries " + map.entrySet().stream().limit(8).toList();
  }

  private GuidedLocalSearchNumber sum(Map<Key_, GuidedLocalSearchNumber> features) {
    if (penalties.counts().isEmpty()) {
      return GuidedLocalSearchNumber.ZERO;
    }
    var total = GuidedLocalSearchNumber.ZERO;
    for (var entry : features.entrySet()) {
      total = total.add(entry.getValue().multiply(penalties.count(entry.getKey())));
    }
    return total;
  }

  private void validateFeature(Key_ key, GuidedLocalSearchNumber cost) {
    if (key == null || cost.signum() < 0) {
      throw new IllegalArgumentException(
          "GLS feature provider (%s) emitted key (%s) with cost (%s); keys must be non-null and costs nonnegative."
              .formatted(provider.getClass().getName(), key, cost));
    }
  }

  private GuidedLocalSearchNumber decimalCost(Key_ key, BigDecimal cost) {
    if (cost == null) {
      throw new IllegalArgumentException(
          "GLS feature provider (%s) emitted null cost for key (%s). Costs must be non-null and nonnegative."
              .formatted(provider.getClass().getName(), key));
    }
    return GuidedLocalSearchNumber.of(cost);
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("The GLS feature session is already closed.");
    }
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      if (scoreDirector.getWorkingSolutionMutationObserver() == this) {
        scoreDirector.setWorkingSolutionMutationObserver(null);
      }
      activeFeatures.clear();
      aggregate = GuidedLocalSearchNumber.ZERO;
      try {
        session.close();
      } finally {
        synchronized (ACTIVE_SESSIONS) {
          ACTIVE_SESSIONS.remove(session);
        }
      }
    }
  }

  @Override
  public void beforeVariableChanged(Object entity, String variableName) {
    if (initialized) {
      try {
        session.beforeVariableChanged(entity, variableName);
      } catch (RuntimeException | Error failure) {
        workingSolutionChanged();
        throw failure;
      }
    }
  }

  @Override
  public void afterVariableChanged(Object entity, String variableName) {
    if (initialized) {
      try {
        session.afterVariableChanged(entity, variableName);
      } catch (RuntimeException | Error failure) {
        workingSolutionChanged();
        throw failure;
      }
    }
  }

  @Override
  public void beforeListVariableChanged(
      Object entity, String variableName, int fromIndex, int toIndex) {
    if (initialized) {
      try {
        session.beforeListVariableChanged(entity, variableName, fromIndex, toIndex);
      } catch (RuntimeException | Error failure) {
        workingSolutionChanged();
        throw failure;
      }
    }
  }

  @Override
  public void afterListVariableChanged(
      Object entity, String variableName, int fromIndex, int toIndex) {
    if (initialized) {
      try {
        session.afterListVariableChanged(entity, variableName, fromIndex, toIndex);
      } catch (RuntimeException | Error failure) {
        workingSolutionChanged();
        throw failure;
      }
    }
  }

  @Override
  public void beforeListVariableElementAssigned(String variableName, Object element) {
    if (initialized) {
      try {
        session.beforeListVariableElementAssigned(variableName, element);
      } catch (RuntimeException | Error failure) {
        workingSolutionChanged();
        throw failure;
      }
    }
  }

  @Override
  public void afterListVariableElementAssigned(String variableName, Object element) {
    if (initialized) {
      try {
        session.afterListVariableElementAssigned(variableName, element);
      } catch (RuntimeException | Error failure) {
        workingSolutionChanged();
        throw failure;
      }
    }
  }

  @Override
  public void beforeListVariableElementUnassigned(String variableName, Object element) {
    if (initialized) {
      try {
        session.beforeListVariableElementUnassigned(variableName, element);
      } catch (RuntimeException | Error failure) {
        workingSolutionChanged();
        throw failure;
      }
    }
  }

  @Override
  public void afterListVariableElementUnassigned(String variableName, Object element) {
    if (initialized) {
      try {
        session.afterListVariableElementUnassigned(variableName, element);
      } catch (RuntimeException | Error failure) {
        workingSolutionChanged();
        throw failure;
      }
    }
  }

  private final class Updater implements GuidedLocalSearchFeatureUpdater<Key_> {
    @Override
    public void accept(Key_ key, long cost) {
      put(key, GuidedLocalSearchNumber.of(cost));
    }

    @Override
    public void accept(Key_ key, BigDecimal cost) {
      put(key, decimalCost(key, cost));
    }

    private void put(Key_ key, GuidedLocalSearchNumber cost) {
      validateFeature(key, cost);
      var oldCost = activeFeatures.put(key, cost);
      var count = penalties.count(key);
      if (oldCost != null) {
        aggregate = aggregate.subtract(oldCost.multiply(count));
      }
      aggregate = aggregate.add(cost.multiply(count));
    }

    @Override
    public void remove(Key_ key) {
      var oldCost = activeFeatures.remove(key);
      if (oldCost == null) {
        throw new IllegalStateException(
            "GLS feature provider (%s) removed absent feature (%s)."
                .formatted(provider.getClass().getName(), key));
      }
      aggregate = aggregate.subtract(oldCost.multiply(penalties.count(key)));
    }
  }
}
