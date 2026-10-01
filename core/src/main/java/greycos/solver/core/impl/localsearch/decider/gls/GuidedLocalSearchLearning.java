package greycos.solver.core.impl.localsearch.decider.gls;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coordinator-owned marginal-cost estimates. Workers only consume published immutable generations;
 * candidate consumption touches changed features and the expiring event, never the entire history.
 */
public final class GuidedLocalSearchLearning {

  private static final int EVENT_LIMIT = 256;
  private static final int FEATURE_SAMPLE_LIMIT = 8;
  private static final int NEUTRAL_WEIGHT = 4;

  private final List<GuidedLocalSearchScale.Calibration> calibrations;
  private final Map<Object, FeatureHistory> histories = new HashMap<>();
  private final ArrayDeque<Event> events = new ArrayDeque<>();
  private long eventOrdinal;
  private Snapshot snapshot;

  public GuidedLocalSearchLearning(List<BigDecimal> scaleOverrides) {
    calibrations = new ArrayList<>(scaleOverrides.size());
    for (var override : scaleOverrides)
      calibrations.add(new GuidedLocalSearchScale.Calibration(override));
    snapshot = buildSnapshot(0L);
  }

  public void observe(
      GuidedLocalSearchFeatureTracker.AutomaticDelta delta,
      Number[] candidate,
      Number[] current,
      boolean scaleEligible) {
    if (candidate.length != calibrations.size() || current.length != calibrations.size()) {
      throw new IllegalArgumentException("GLS observations must match the score level count.");
    }
    long ordinal = Math.incrementExact(eventOrdinal);
    eventOrdinal = ordinal;
    if (events.size() == EVENT_LIMIT) expire(events.removeFirst());
    int divisor = Math.max(delta.removed().size(), delta.added().size());
    if (divisor == 0) {
      events.addLast(new Event(ordinal, List.of(), List.of()));
      return;
    }
    var removed = new ArrayList<GuidedLocalSearchRational>(calibrations.size());
    var added = new ArrayList<GuidedLocalSearchRational>(calibrations.size());
    for (int level = 0; level < calibrations.size(); level++) {
      var difference =
          GuidedLocalSearchNumber.of(candidate[level])
              .subtract(GuidedLocalSearchNumber.of(current[level]));
      var normalized = GuidedLocalSearchRational.of(difference, divisor);
      removed.add(normalized);
      added.add(normalized.negate());
      if (scaleEligible) calibrations.get(level).observe(difference, divisor);
    }
    observeFeatures(delta.removed(), new Observation(ordinal, List.copyOf(removed)));
    observeFeatures(delta.added(), new Observation(ordinal, List.copyOf(added)));
    events.addLast(new Event(ordinal, delta.removed(), delta.added()));
  }

  private void observeFeatures(List<Object> keys, Observation observation) {
    for (var key : keys) {
      var history =
          histories.computeIfAbsent(key, ignored -> new FeatureHistory(calibrations.size()));
      if (history.observations.size() == FEATURE_SAMPLE_LIMIT) history.observations.removeFirst();
      history.observations.addLast(observation);
      history.updated = true;
    }
  }

  private void expire(Event event) {
    expireFeatures(event.removed, event.ordinal);
    expireFeatures(event.added, event.ordinal);
  }

  private void expireFeatures(List<Object> keys, long ordinal) {
    for (var key : keys) {
      var history = histories.get(key);
      if (history != null
          && !history.observations.isEmpty()
          && history.observations.getFirst().ordinal == ordinal) {
        history.observations.removeFirst();
        if (history.observations.isEmpty() && !history.published) histories.remove(key);
      }
    }
  }

  int retainedFeatureCount() {
    return histories.size();
  }

  /** Publishes only at a quiescent penalty barrier, after consuming the completed round. */
  public Snapshot publish(
      Set<Object> activeAutomatic, List<GuidedLocalSearchPenaltyTable.Snapshot<Object>> penalties) {
    if (penalties.size() != calibrations.size()) {
      throw new IllegalArgumentException("GLS penalty levels must match the learning level count.");
    }
    calibrations.forEach(GuidedLocalSearchScale.Calibration::publishAtPenaltyUpdate);
    for (Iterator<Map.Entry<Object, FeatureHistory>> iterator = histories.entrySet().iterator();
        iterator.hasNext(); ) {
      var entry = iterator.next();
      var key = entry.getKey();
      var history = entry.getValue();
      boolean retained = activeAutomatic.contains(key);
      if (!retained) {
        for (var penalty : penalties) {
          if (penalty.count(key) != 0L) {
            retained = true;
            break;
          }
        }
      }
      if (!retained) {
        iterator.remove();
        continue;
      }
      if (history.updated && !history.observations.isEmpty()) {
        for (int level = 0; level < calibrations.size(); level++) {
          var observations = new ArrayList<GuidedLocalSearchRational>(history.observations.size());
          for (var observation : history.observations)
            observations.add(observation.values.get(level));
          history.weights[level] =
              GuidedLocalSearchRational.median(observations)
                  .weight(calibrations.get(level).scale());
        }
      }
      // Expiration alone does not erase a previously published estimate.
      history.updated = false;
      history.published = true;
    }
    snapshot = buildSnapshot(Math.incrementExact(snapshot.version()));
    return snapshot;
  }

  public Snapshot snapshot() {
    return snapshot;
  }

  private Snapshot buildSnapshot(long version) {
    var weights = new ArrayList<Map<Object, Integer>>(calibrations.size());
    var scales = new ArrayList<GuidedLocalSearchScale>(calibrations.size());
    var calibrated = new ArrayList<Boolean>(calibrations.size());
    var scaleObservationCounts = new ArrayList<Integer>(calibrations.size());
    for (int level = 0; level < calibrations.size(); level++) {
      var values = new HashMap<Object, Integer>();
      for (var entry : histories.entrySet()) {
        int weight = entry.getValue().weights[level];
        if (weight != NEUTRAL_WEIGHT) values.put(entry.getKey(), weight);
      }
      weights.add(Map.copyOf(values));
      var calibration = calibrations.get(level);
      scales.add(calibration.scale());
      calibrated.add(calibration.calibrated());
      scaleObservationCounts.add(calibration.observationCount());
    }
    return new Snapshot(
        version,
        List.copyOf(scales),
        List.copyOf(calibrated),
        List.copyOf(weights),
        histories.size(),
        events.size(),
        List.copyOf(scaleObservationCounts));
  }

  int featureObservationCount(Object key) {
    var history = histories.get(key);
    return history == null ? 0 : history.observations.size();
  }

  private static final class FeatureHistory {
    private final ArrayDeque<Observation> observations = new ArrayDeque<>();
    private final int[] weights;
    private boolean updated;
    private boolean published;

    FeatureHistory(int levels) {
      weights = new int[levels];
      java.util.Arrays.fill(weights, NEUTRAL_WEIGHT);
    }
  }

  private record Observation(long ordinal, List<GuidedLocalSearchRational> values) {}

  private record Event(long ordinal, List<Object> removed, List<Object> added) {}

  public record Snapshot(
      long version,
      List<GuidedLocalSearchScale> scales,
      List<Boolean> calibrated,
      List<Map<Object, Integer>> weights,
      int retainedFeatureCount,
      int retainedEventCount,
      List<Integer> scaleObservationCounts) {
    public Snapshot {
      scales = List.copyOf(scales);
      calibrated = List.copyOf(calibrated);
      weights = weights.stream().map(Map::copyOf).toList();
      scaleObservationCounts = List.copyOf(scaleObservationCounts);
    }

    public int weight(int level, Object key) {
      return weights.get(level).getOrDefault(key, NEUTRAL_WEIGHT);
    }

    public Map<Object, Integer> weights(int level) {
      return weights.get(level);
    }

    static Snapshot neutral(int levels) {
      return new GuidedLocalSearchLearning(Collections.nCopies(levels, null)).snapshot();
    }
  }
}
