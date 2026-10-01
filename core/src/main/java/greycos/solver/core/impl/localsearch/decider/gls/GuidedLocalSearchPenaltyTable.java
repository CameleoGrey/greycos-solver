package greycos.solver.core.impl.localsearch.decider.gls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Coordinator-owned sparse penalty history. Mutation is only legal while all move workers are
 * quiescent. Generation snapshots share a read-only view, avoiding a copy of the entire history.
 */
public final class GuidedLocalSearchPenaltyTable<Key_> {

  private final Map<Key_, Long> counts = new HashMap<>();
  private final Map<Key_, Long> readOnlyCounts = Collections.unmodifiableMap(counts);
  // Assigned only by the coordinator at a penalty barrier; workers never register features.
  private final Map<Key_, Long> featureOrdinals = new HashMap<>();
  private final Random defaultRandom = new Random(0L);
  private long nextFeatureOrdinal;
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
    clearCounts();
    featureOrdinals.clear();
    nextFeatureOrdinal = 0L;
  }

  public void clearCounts() {
    var nextVersion = Math.incrementExact(version);
    counts.clear();
    version = nextVersion;
  }

  /** Release inactive, unpenalized tie metadata only at a coordinator barrier. */
  public void pruneInactive(
      Map<Key_, GuidedLocalSearchNumber> automaticFeatures,
      Map<Key_, GuidedLocalSearchNumber> customFeatures) {
    featureOrdinals
        .keySet()
        .removeIf(
            key ->
                count(key) == 0L
                    && !automaticFeatures.containsKey(key)
                    && !customFeatures.containsKey(key));
  }

  int retainedOrdinalCount() {
    return featureOrdinals.size();
  }

  /** Increments a seeded bounded batch from the exact maximum-utility tie. */
  public int incrementMaximumUtility(Map<Key_, GuidedLocalSearchNumber> activeFeatures) {
    return incrementMaximumUtility(
        activeFeatures, Map.of(), GuidedLocalSearchScale.ONE, defaultRandom);
  }

  /** Compares calibrated automatic costs and custom costs in the same score-level units. */
  public int incrementMaximumUtility(
      Map<Key_, GuidedLocalSearchNumber> automaticFeatures,
      Map<Key_, GuidedLocalSearchNumber> customFeatures,
      GuidedLocalSearchScale automaticScale) {
    return incrementMaximumUtility(
        automaticFeatures, customFeatures, automaticScale, defaultRandom);
  }

  public int incrementMaximumUtility(
      Map<Key_, GuidedLocalSearchNumber> activeFeatures, RandomGenerator random) {
    return incrementMaximumUtility(activeFeatures, Map.of(), GuidedLocalSearchScale.ONE, random);
  }

  public int incrementMaximumUtility(
      Map<Key_, GuidedLocalSearchNumber> automaticFeatures,
      Map<Key_, GuidedLocalSearchNumber> customFeatures,
      GuidedLocalSearchScale automaticScale,
      RandomGenerator random) {
    Objects.requireNonNull(random);
    pruneInactive(automaticFeatures, customFeatures);
    long positiveFeatureCount = 0L;
    List<Key_> maximumKeys = new ArrayList<>();
    GuidedLocalSearchNumber maximumCost = GuidedLocalSearchNumber.ZERO;
    GuidedLocalSearchNumber maximumDenominator = GuidedLocalSearchNumber.ONE;
    for (var features : List.of(automaticFeatures, customFeatures)) {
      for (var entry : features.entrySet()) {
        var key = Objects.requireNonNull(entry.getKey());
        var cost =
            Objects.requireNonNull(entry.getValue())
                .multiply(
                    features == automaticFeatures
                        ? automaticScale.numerator()
                        : automaticScale.denominator());
        if (cost.signum() < 0) {
          throw new IllegalArgumentException(
              "GLS feature (%s) has negative cost (%s).".formatted(key, cost));
        }
        if (cost.signum() == 0) {
          continue;
        }
        positiveFeatureCount++;
        featureOrdinals.computeIfAbsent(
            key,
            ignored -> {
              nextFeatureOrdinal = Math.incrementExact(nextFeatureOrdinal);
              return nextFeatureOrdinal;
            });
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
    }
    if (maximumKeys.isEmpty()) {
      return 0;
    }
    maximumKeys.sort(Comparator.comparingLong(featureOrdinals::get));
    int batchSize =
        (int) Math.min(maximumKeys.size(), Math.max(1L, (positiveFeatureCount + 15L) / 16L));
    // Partial Fisher-Yates sampling gives every tied feature equal probability without replacement.
    for (int i = 0; i < batchSize; i++) {
      Collections.swap(maximumKeys, i, i + random.nextInt(maximumKeys.size() - i));
    }
    var selectedKeys = maximumKeys.subList(0, batchSize);
    // Validate the whole selected batch before mutating any penalty count.
    var nextVersion = Math.incrementExact(version);
    for (var key : selectedKeys) {
      if (count(key) == Long.MAX_VALUE) {
        throw new IllegalStateException(
            "The GLS penalty count for feature (%s) reached Long.MAX_VALUE.".formatted(key));
      }
    }
    for (var key : selectedKeys) {
      counts.put(key, count(key) + 1L);
    }
    version = nextVersion;
    return batchSize;
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
