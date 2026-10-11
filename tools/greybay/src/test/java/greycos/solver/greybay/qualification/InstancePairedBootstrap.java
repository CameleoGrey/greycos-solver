package greycos.solver.greybay.qualification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import greycos.solver.greybay.api.objective.ExactRational;

/** Independent report-only statistics. These intervals never select or rank configurations. */
public final class InstancePairedBootstrap {
  public static final int RESAMPLES = 10_000;

  private InstancePairedBootstrap() {}

  /** All levels are native score levels in their original order and units. Higher is better. */
  public record Pair(
      String instance,
      long optimizerSeed,
      long solverSeed,
      boolean leftFeasible,
      boolean rightFeasible,
      List<ExactRational> leftLevels,
      List<ExactRational> rightLevels) {
    public Pair {
      Objects.requireNonNull(instance, "instance");
      leftLevels = List.copyOf(leftLevels);
      rightLevels = List.copyOf(rightLevels);
      if (instance.isBlank() || leftLevels.isEmpty() || leftLevels.size() != rightLevels.size()) {
        throw new IllegalArgumentException("Pair instance and matching score levels are required.");
      }
    }
  }

  public record Interval(ExactRational estimate, ExactRational lower, ExactRational upper) {}

  /** Paired seed range exposes variability; it is not a confidence interval. */
  public record InstanceSummary(
      String instance,
      int pairedSeedCount,
      List<ExactRational> meanDifferences,
      List<ExactRational> minimumDifferences,
      List<ExactRational> maximumDifferences,
      ExactRational feasibilityDifference) {
    public InstanceSummary {
      meanDifferences = List.copyOf(meanDifferences);
      minimumDifferences = List.copyOf(minimumDifferences);
      maximumDifferences = List.copyOf(maximumDifferences);
    }
  }

  public record Result(
      long resamplingSeed,
      int resamples,
      String estimator,
      Interval feasibility,
      List<Interval> levels,
      List<InstanceSummary> instances) {
    public Result {
      levels = List.copyOf(levels);
      instances = List.copyOf(instances);
    }
  }

  public static Result estimate(List<Pair> pairs, long resamplingSeed) {
    if (pairs.isEmpty()) {
      throw new IllegalArgumentException("A paired bootstrap requires a nonempty complete panel.");
    }
    record Seed(long optimizer, long solver) {}
    var clusters = new LinkedHashMap<String, List<Pair>>();
    var keys = new LinkedHashMap<String, HashSet<Seed>>();
    int levelCount = pairs.getFirst().leftLevels().size();
    for (var pair : pairs) {
      if (pair.leftLevels().size() != levelCount) {
        throw new IllegalArgumentException("Paired native score layouts must agree.");
      }
      if (!keys.computeIfAbsent(pair.instance(), ignored -> new HashSet<>())
          .add(new Seed(pair.optimizerSeed(), pair.solverSeed()))) {
        throw new IllegalArgumentException("Duplicate paired cell for instance " + pair.instance());
      }
      clusters.computeIfAbsent(pair.instance(), ignored -> new ArrayList<>()).add(pair);
    }
    var expectedKeys = keys.values().iterator().next();
    if (keys.values().stream().anyMatch(value -> !expectedKeys.equals(value))) {
      throw new IllegalArgumentException(
          "Every instance must contain the same complete paired seed panel.");
    }
    // Canonical cluster order also makes results independent of observation arrival order.
    var summaries =
        clusters.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> summarize(entry.getKey(), entry.getValue(), levelCount))
            .toList();
    var random = new Random(resamplingSeed);
    var feasibility = new ArrayList<ExactRational>(RESAMPLES);
    var levels = new ArrayList<List<ExactRational>>(levelCount);
    for (int level = 0; level < levelCount; level++) {
      levels.add(new ArrayList<>(RESAMPLES));
    }
    for (int replicate = 0; replicate < RESAMPLES; replicate++) {
      var feasibilitySum = ExactRational.ZERO;
      var sums = zeros(levelCount);
      for (int draw = 0; draw < summaries.size(); draw++) {
        // One draw selects an entire instance, including every paired optimizer/solver seed.
        var cluster = summaries.get(random.nextInt(summaries.size()));
        feasibilitySum = feasibilitySum.add(cluster.feasibilityDifference());
        for (int level = 0; level < levelCount; level++) {
          sums.set(level, sums.get(level).add(cluster.meanDifferences().get(level)));
        }
      }
      feasibility.add(feasibilitySum.divide(summaries.size()));
      for (int level = 0; level < levelCount; level++) {
        levels.get(level).add(sums.get(level).divide(summaries.size()));
      }
    }
    var levelIntervals = new ArrayList<Interval>(levelCount);
    for (int level = 0; level < levelCount; level++) {
      final int index = level;
      levelIntervals.add(
          interval(
              mean(summaries.stream().map(s -> s.meanDifferences().get(index)).toList()),
              levels.get(level)));
    }
    return new Result(
        resamplingSeed,
        RESAMPLES,
        "Equal-instance mean of within-instance paired seed means; 95% percentile interval, inverse empirical CDF",
        interval(
            mean(summaries.stream().map(InstanceSummary::feasibilityDifference).toList()),
            feasibility),
        levelIntervals,
        summaries);
  }

  private static InstanceSummary summarize(String instance, List<Pair> pairs, int levelCount) {
    var sums = zeros(levelCount);
    var minimum = new ArrayList<ExactRational>(levelCount);
    var maximum = new ArrayList<ExactRational>(levelCount);
    long feasibility = 0;
    for (var pair : pairs) {
      feasibility += (pair.leftFeasible() ? 1 : 0) - (pair.rightFeasible() ? 1 : 0);
      for (int level = 0; level < levelCount; level++) {
        var difference = pair.leftLevels().get(level).subtract(pair.rightLevels().get(level));
        sums.set(level, sums.get(level).add(difference));
        if (minimum.size() <= level) {
          minimum.add(difference);
          maximum.add(difference);
        } else {
          if (minimum.get(level).compareTo(difference) > 0) {
            minimum.set(level, difference);
          }
          if (maximum.get(level).compareTo(difference) < 0) {
            maximum.set(level, difference);
          }
        }
      }
    }
    return new InstanceSummary(
        instance,
        pairs.size(),
        sums.stream().map(sum -> sum.divide(pairs.size())).toList(),
        minimum,
        maximum,
        ExactRational.of(feasibility, pairs.size()));
  }

  private static ArrayList<ExactRational> zeros(int size) {
    var values = new ArrayList<ExactRational>(size);
    for (int index = 0; index < size; index++) {
      values.add(ExactRational.ZERO);
    }
    return values;
  }

  private static ExactRational mean(List<ExactRational> values) {
    return values.stream().reduce(ExactRational.ZERO, ExactRational::add).divide(values.size());
  }

  private static Interval interval(ExactRational estimate, List<ExactRational> resampled) {
    resampled.sort(Comparator.naturalOrder());
    // ceil(0.025 * 10000) - 1 and ceil(0.975 * 10000) - 1: no rounded floating-point quantiles.
    return new Interval(estimate, resampled.get(249), resampled.get(9749));
  }
}
