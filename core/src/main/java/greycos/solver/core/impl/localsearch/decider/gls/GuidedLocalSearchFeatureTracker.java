package greycos.solver.core.impl.localsearch.decider.gls;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureConsumer;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureSession;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureUpdater;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
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
  private GuidedLocalSearchIdentityRegistry<Solution_> identityRegistry;
  private GuidedLocalSearchAutomaticFeatures<Solution_> automatic;
  private final Map<Object, GuidedLocalSearchNumber> automaticFeatures = new HashMap<>();
  private final Set<Object> baseline = new HashSet<>();
  private final Set<Object> difference = new HashSet<>();
  private final Set<Key_> customKeys = new HashSet<>();
  private List<GuidedLocalSearchPenaltyTable.Snapshot<Object>> levelPenalties;
  private List<Map<Object, GuidedLocalSearchNumber>> levelFeatures;
  private GuidedLocalSearchNumber[] automaticTotals;
  private GuidedLocalSearchNumber[] customTotals;
  private int scalarTargetIndex;

  private GuidedLocalSearchFeatureTracker(
      InnerScoreDirector<Solution_, ?> scoreDirector,
      GuidedLocalSearchFeatureProvider<Solution_, Key_> provider,
      GuidedLocalSearchPenaltyTable.Snapshot<Key_> penalties) {
    this.scoreDirector = Objects.requireNonNull(scoreDirector);
    this.provider = provider;
    this.penalties = Objects.requireNonNull(penalties);
    scalarTargetIndex = scoreDirector.getScoreDefinition().getLevelsSize() - 1;
    session =
        provider == null
            ? null
            : Objects.requireNonNull(
                provider.newSession(),
                () ->
                    "The GLS feature provider (%s) returned a null session."
                        .formatted(provider.getClass().getName()));
    if (session != null)
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

  public static <Solution_, Key_> GuidedLocalSearchFeatureTracker<Solution_, Key_> attach(
      InnerScoreDirector<Solution_, ?> director,
      GuidedLocalSearchFeatureProvider<Solution_, Key_> provider,
      GuidedLocalSearchPenaltyTable.Snapshot<Key_> penalties,
      int targetScoreLevelIndex) {
    var tracker = attach(director, provider, penalties);
    tracker.scalarTargetIndex = targetScoreLevelIndex;
    return tracker;
  }

  public static <Solution_> GuidedLocalSearchFeatureTracker<Solution_, Object> attachAutomatic(
      InnerScoreDirector<Solution_, ?> director,
      GuidedLocalSearchFeatureProvider<Solution_, Object> provider,
      List<GuidedLocalSearchPenaltyTable.Snapshot<Object>> penalties,
      GuidedLocalSearchIdentityRegistry<Solution_> catalog,
      int scalarTargetIndex) {
    var tracker =
        GuidedLocalSearchFeatureTracker.<Solution_, Object>attach(
            director, provider, new GuidedLocalSearchPenaltyTable.Snapshot<>(0L, Map.of()));
    try {
      tracker.scalarTargetIndex = scalarTargetIndex;
      int size = director.getScoreDefinition().getLevelsSize();
      if (penalties.size() != size || scalarTargetIndex < 0 || scalarTargetIndex >= size) {
        throw new IllegalArgumentException("GLS feature levels must match the score definition.");
      }
      tracker.levelPenalties = List.copyOf(penalties);
      tracker.levelFeatures = new ArrayList<>(size);
      for (int i = 0; i < size; i++) tracker.levelFeatures.add(new HashMap<>());
      tracker.automaticTotals = new GuidedLocalSearchNumber[size];
      tracker.customTotals = new GuidedLocalSearchNumber[size];
      Arrays.fill(tracker.automaticTotals, GuidedLocalSearchNumber.ZERO);
      Arrays.fill(tracker.customTotals, GuidedLocalSearchNumber.ZERO);
      tracker.identityRegistry =
          catalog == null
              ? GuidedLocalSearchIdentityRegistry.create(director)
              : catalog.bind(director);
      tracker.automatic =
          new GuidedLocalSearchAutomaticFeatures<>(
              director, tracker.identityRegistry, tracker::addAutomatic, tracker::removeAutomatic);
      return tracker;
    } catch (RuntimeException | Error failure) {
      try {
        tracker.close();
      } catch (RuntimeException | Error closeFailure) {
        if (failure != closeFailure) failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  public GuidedLocalSearchIdentityRegistry<Solution_> identityRegistry() {
    return identityRegistry;
  }

  public record Guidance(
      List<GuidedLocalSearchNumber> automatic, List<GuidedLocalSearchNumber> custom) {}

  public Guidance aggregates() {
    flush();
    return new Guidance(
        List.copyOf(Arrays.asList(automaticTotals)), List.copyOf(Arrays.asList(customTotals)));
  }

  public Map<Object, GuidedLocalSearchNumber> automaticFeatures() {
    flush();
    return Collections.unmodifiableMap(automaticFeatures);
  }

  public Map<Object, GuidedLocalSearchNumber> customFeatures(int level) {
    flush();
    return Collections.unmodifiableMap(levelFeatures.get(level));
  }

  /** Commits only changed feature presence to the incumbent baseline. */
  public void markBaseline() {
    flush();
    for (var key : difference) {
      if (automaticFeatures.containsKey(key)) baseline.add(key);
      else baseline.remove(key);
    }
    difference.clear();
  }

  public int automaticDifferenceCount() {
    flush();
    return difference.size();
  }

  public void updatePenalties(List<GuidedLocalSearchPenaltyTable.Snapshot<Object>> penalties) {
    ensureOpen();
    if (penalties.size() != levelFeatures.size()) {
      throw new IllegalArgumentException("GLS penalty levels must match the score definition.");
    }
    levelPenalties = List.copyOf(penalties);
    for (int i = 0; i < penalties.size(); i++) {
      automaticTotals[i] = sum(automaticFeatures, penalties.get(i));
      customTotals[i] = sum(levelFeatures.get(i), penalties.get(i));
    }
  }

  private void addAutomatic(Object key) {
    if (automaticFeatures.putIfAbsent(key, GuidedLocalSearchNumber.ONE) != null) {
      throw new IllegalStateException("Duplicate automatic GLS feature (" + key + ").");
    }
    if (baseline.contains(key)) difference.remove(key);
    else difference.add(key);
    for (int i = 0; i < automaticTotals.length; i++) {
      automaticTotals[i] =
          automaticTotals[i].add(GuidedLocalSearchNumber.of(levelPenalties.get(i).count(key)));
    }
  }

  private void removeAutomatic(Object key) {
    if (automaticFeatures.remove(key) == null) {
      throw new IllegalStateException("Absent automatic GLS feature (" + key + ").");
    }
    if (baseline.contains(key)) difference.add(key);
    else difference.remove(key);
    for (int i = 0; i < automaticTotals.length; i++) {
      automaticTotals[i] =
          automaticTotals[i].subtract(GuidedLocalSearchNumber.of(levelPenalties.get(i).count(key)));
    }
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
    automaticFeatures.clear();
    baseline.clear();
    difference.clear();
    customKeys.clear();
    if (levelFeatures != null) {
      levelFeatures.forEach(Map::clear);
      Arrays.fill(automaticTotals, GuidedLocalSearchNumber.ZERO);
      Arrays.fill(customTotals, GuidedLocalSearchNumber.ZERO);
    }
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
        if (session != null)
          session.resetWorkingSolution(Objects.requireNonNull(scoreDirector.getWorkingSolution()));
        if (automatic != null) automatic.reset();
        initialized = true;
      }
      if (automatic != null) automatic.flush();
      if (session != null) session.flushChanges(updater);
    } catch (RuntimeException | Error failure) {
      // A session can emit several changes and then fail. Never expose its partial state later.
      workingSolutionChanged();
      throw failure;
    }
  }

  public void assertFromScratch() {
    flush();
    if (automatic != null) {
      try {
        assertAutomaticFromScratch();
      } catch (RuntimeException | Error failure) {
        workingSolutionChanged();
        throw failure;
      }
      return;
    }
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

            @Override
            public void acceptScore(Key_ key, Score<?> cost) {
              put(key, vectorCost(key, cost)[scalarTargetIndex]);
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

  private void assertAutomaticFromScratch() {
    var expectedAuto = new HashMap<Object, GuidedLocalSearchNumber>();
    automatic.extract(
        key -> {
          if (expectedAuto.putIfAbsent(key, GuidedLocalSearchNumber.ONE) != null) {
            throw new IllegalStateException(
                "Duplicate independently extracted automatic GLS feature (" + key + ").");
          }
        });
    var expectedCustom = new ArrayList<Map<Object, GuidedLocalSearchNumber>>(levelFeatures.size());
    for (int i = 0; i < levelFeatures.size(); i++) expectedCustom.add(new HashMap<>());
    var expectedKeys = new HashSet<Key_>();
    if (provider != null) {
      provider.extractFeatures(
          scoreDirector.getWorkingSolution(),
          new GuidedLocalSearchFeatureConsumer<>() {
            @Override
            public void accept(Key_ key, long cost) {
              put(key, scalarCost(GuidedLocalSearchNumber.of(cost)));
            }

            @Override
            public void accept(Key_ key, BigDecimal cost) {
              put(key, scalarCost(decimalCost(key, cost)));
            }

            @Override
            public void acceptScore(Key_ key, Score<?> cost) {
              put(key, vectorCost(key, cost));
            }

            private void put(Key_ key, GuidedLocalSearchNumber[] costs) {
              for (var cost : costs) validateFeature(key, cost);
              if (!expectedKeys.add(key)) {
                throw new IllegalStateException(
                    "GLS feature provider ("
                        + provider.getClass().getName()
                        + ") extracted duplicate key ("
                        + key
                        + "). Give separate occurrences distinct keys.");
              }
              var namespaced = new CustomFeature(key);
              for (int i = 0; i < costs.length; i++) {
                if (costs[i].signum() != 0) expectedCustom.get(i).put(namespaced, costs[i]);
              }
            }
          });
    }
    if (!expectedAuto.equals(automaticFeatures)
        || !expectedCustom.equals(levelFeatures)
        || !expectedKeys.equals(customKeys)) {
      throw new IllegalStateException(
          "GLS feature corruption: incremental automatic/custom features differ from independent extraction.");
    }
    for (int i = 0; i < levelFeatures.size(); i++) {
      if (!sum(expectedAuto, levelPenalties.get(i)).equals(automaticTotals[i])
          || !sum(expectedCustom.get(i), levelPenalties.get(i)).equals(customTotals[i])) {
        throw new IllegalStateException("GLS aggregate corruption at score level (" + i + ").");
      }
    }
    var expectedDifference = new HashSet<>(baseline);
    for (var key : automaticFeatures.keySet()) {
      if (!expectedDifference.add(key)) expectedDifference.remove(key);
    }
    if (!expectedDifference.equals(difference)) {
      throw new IllegalStateException(
          "GLS automatic feature difference differs from its incumbent baseline.");
    }
  }

  private static <Key_> GuidedLocalSearchNumber sum(
      Map<Key_, GuidedLocalSearchNumber> features,
      GuidedLocalSearchPenaltyTable.Snapshot<Key_> penalties) {
    var result = GuidedLocalSearchNumber.ZERO;
    for (var entry : features.entrySet()) {
      result = result.add(entry.getValue().multiply(penalties.count(entry.getKey())));
    }
    return result;
  }

  private GuidedLocalSearchNumber[] scalarCost(GuidedLocalSearchNumber cost) {
    var costs = new GuidedLocalSearchNumber[scoreDirector.getScoreDefinition().getLevelsSize()];
    Arrays.fill(costs, GuidedLocalSearchNumber.ZERO);
    costs[scalarTargetIndex] = cost;
    return costs;
  }

  private GuidedLocalSearchNumber[] vectorCost(Key_ key, Score<?> cost) {
    var zero = scoreDirector.getScoreDefinition().getZeroScore();
    if (cost == null
        || cost.getClass() != zero.getClass()
        || cost.structuralScore() != 0
        || !cost.zero().equals(zero)) {
      throw new IllegalArgumentException(
          "GLS feature ("
              + key
              + ") has incompatible score cost ("
              + cost
              + "). Use the solution's score type and dimensions with a zero structural component.");
    }
    var numbers = cost.toLevelNumbers();
    var result = new GuidedLocalSearchNumber[numbers.length];
    for (int i = 0; i < numbers.length; i++) {
      result[i] = GuidedLocalSearchNumber.of(numbers[i]);
      validateFeature(key, result[i]);
    }
    return result;
  }

  private void putVector(Key_ key, GuidedLocalSearchNumber[] costs) {
    for (var cost : costs) validateFeature(key, cost);
    var namespaced = new CustomFeature(key);
    customKeys.add(key);
    for (int i = 0; i < costs.length; i++) {
      var old =
          costs[i].signum() == 0
              ? levelFeatures.get(i).remove(namespaced)
              : levelFeatures.get(i).put(namespaced, costs[i]);
      var count = levelPenalties.get(i).count(namespaced);
      if (old != null) customTotals[i] = customTotals[i].subtract(old.multiply(count));
      customTotals[i] = customTotals[i].add(costs[i].multiply(count));
    }
  }

  private record CustomFeature(Object key) {}

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
        if (session != null) session.close();
      } finally {
        workingSolutionChanged();
        automatic = null;
        identityRegistry = null;
        synchronized (ACTIVE_SESSIONS) {
          ACTIVE_SESSIONS.remove(session);
        }
      }
    }
  }

  @Override
  public void beforeVariableChanged(Object entity, String variableName) {
    if (initialized && session != null) {
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
    if (initialized && automatic != null) automatic.afterVariableChanged(entity, variableName);
    if (initialized && session != null) {
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
    if (initialized && session != null) {
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
    if (initialized && automatic != null) automatic.markOwner(entity);
    if (initialized && session != null) {
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
    if (initialized && session != null) {
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
    if (initialized && automatic != null) automatic.markElement(element);
    if (initialized && session != null) {
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
    if (initialized && session != null) {
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
    if (initialized && automatic != null) automatic.markElement(element);
    if (initialized && session != null) {
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
      if (automatic != null) {
        putVector(key, scalarCost(cost));
        return;
      }
      var oldCost = activeFeatures.put(key, cost);
      var count = penalties.count(key);
      if (oldCost != null) {
        aggregate = aggregate.subtract(oldCost.multiply(count));
      }
      aggregate = aggregate.add(cost.multiply(count));
    }

    @Override
    public void acceptScore(Key_ key, Score<?> cost) {
      var costs = vectorCost(key, cost);
      if (automatic == null) put(key, costs[scalarTargetIndex]);
      else putVector(key, costs);
    }

    @Override
    public void remove(Key_ key) {
      if (automatic != null) {
        if (!customKeys.remove(key)) {
          throw new IllegalStateException(
              "GLS feature provider ("
                  + provider.getClass().getName()
                  + ") removed absent feature ("
                  + key
                  + ").");
        }
        var namespaced = new CustomFeature(key);
        for (int i = 0; i < levelFeatures.size(); i++) {
          var old = levelFeatures.get(i).remove(namespaced);
          if (old != null)
            customTotals[i] =
                customTotals[i].subtract(old.multiply(levelPenalties.get(i).count(namespaced)));
        }
        return;
      }
      var oldCost = activeFeatures.remove(key);
      if (oldCost == null) {
        throw new IllegalStateException(
            "GLS feature provider (%s) removed absent feature (%s)."
                .formatted(provider.getClass().getName(), key));
      }
      aggregate = aggregate.subtract(oldCost.multiply(penalties.count(key)));
    }
  }

  @Override
  public boolean requiresListVariableRelationshipChanges() {
    return automatic != null;
  }

  @Override
  public void afterListVariableRelationshipChanged(
      ListVariableDescriptor<Solution_> descriptor, Object element) {
    if (initialized && automatic != null) automatic.markElement(element);
  }
}
