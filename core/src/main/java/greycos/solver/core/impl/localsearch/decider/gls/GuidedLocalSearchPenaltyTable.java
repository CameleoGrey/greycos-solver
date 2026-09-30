package greycos.solver.core.impl.localsearch.decider.gls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Coordinator-owned sparse penalty history. Mutation is only legal while all move workers are
 * quiescent. Generation snapshots share a read-only view, avoiding a copy of the entire history.
 */
public final class GuidedLocalSearchPenaltyTable<Key_> {

  private final Map<Key_, Long> counts = new HashMap<>();
  private final Map<Key_, Long> readOnlyCounts = Collections.unmodifiableMap(counts);
  private long version;

  public long version() {
    return version;
  }

  public long count(Key_ key) {
    return counts.getOrDefault(key, 0L);
  }

  public Snapshot<Key_> snapshot() {
    return new Snapshot<>(version, readOnlyCounts);
  }

  public void clear() {
    var nextVersion = Math.incrementExact(version);
    counts.clear();
    version = nextVersion;
  }

  /** Increments all positive-cost features tied for the exact maximum utility. */
  public int incrementMaximumUtility(Map<Key_, GuidedLocalSearchNumber> activeFeatures) {
    List<Key_> maximumKeys = new ArrayList<>();
    GuidedLocalSearchNumber maximumCost = GuidedLocalSearchNumber.ZERO;
    GuidedLocalSearchNumber maximumDenominator = GuidedLocalSearchNumber.ONE;
    for (var entry : activeFeatures.entrySet()) {
      var key = Objects.requireNonNull(entry.getKey());
      var cost = Objects.requireNonNull(entry.getValue());
      if (cost.signum() < 0) {
        throw new IllegalArgumentException(
            "GLS feature (%s) has negative cost (%s).".formatted(key, cost));
      }
      if (cost.signum() == 0) {
        continue;
      }
      var utilityDenominator =
          GuidedLocalSearchNumber.of(count(key)).add(GuidedLocalSearchNumber.ONE);
      int comparison =
          cost.multiply(maximumDenominator).compareTo(maximumCost.multiply(utilityDenominator));
      if (comparison > 0) {
        maximumKeys.clear();
        maximumCost = cost;
        maximumDenominator = utilityDenominator;
      }
      if (comparison >= 0) {
        maximumKeys.add(key);
      }
    }
    if (maximumKeys.isEmpty()) {
      return 0;
    }
    // Validate all counts before mutating, so an overflow cannot partially update the table.
    var nextVersion = Math.incrementExact(version);
    for (var key : maximumKeys) {
      if (count(key) == Long.MAX_VALUE) {
        throw new IllegalStateException(
            "The GLS penalty count for feature (%s) reached Long.MAX_VALUE.".formatted(key));
      }
    }
    for (var key : maximumKeys) {
      counts.put(key, count(key) + 1L);
    }
    version = nextVersion;
    return maximumKeys.size();
  }

  /**
   * A versioned view valid until the next coordinated penalty update. It is deliberately not a
   * persistent snapshot; no reader may outlive the worker barrier for this generation.
   */
  public record Snapshot<Key_>(long version, Map<Key_, Long> counts) {
    public Snapshot {
      Objects.requireNonNull(counts);
    }

    public long count(Key_ key) {
      return counts.getOrDefault(key, 0L);
    }
  }
}
