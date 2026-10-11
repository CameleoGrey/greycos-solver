package greycos.solver.core.api.score.profile;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Score-engine wall time and exclusive work for one section, or for both sections combined.
 *
 * @param scoringWallNanos union of score-engine wall intervals
 * @param measuredNanos sum of exclusive unit durations
 * @param unmeasuredNanos wall time minus measured time
 * @param eventCount number of entered invocations
 * @param failureCount number of failed invocations
 * @param coverage measured time divided by wall time, or null when wall time is zero
 * @param buckets work grouped by the exact recorded owner set
 */
public record ConstraintProfileSection(
    long scoringWallNanos,
    long measuredNanos,
    long unmeasuredNanos,
    long eventCount,
    long failureCount,
    @Nullable Double coverage,
    List<ConstraintProfileBucket> buckets) {

  public ConstraintProfileSection {
    ConstraintProfileValidation.nonnegative("scoringWallNanos", scoringWallNanos);
    ConstraintProfileValidation.nonnegative("measuredNanos", measuredNanos);
    ConstraintProfileValidation.nonnegative("unmeasuredNanos", unmeasuredNanos);
    ConstraintProfileValidation.counts(eventCount, failureCount);
    if (measuredNanos > scoringWallNanos || unmeasuredNanos != scoringWallNanos - measuredNanos) {
      throw new IllegalArgumentException(
          "The measuredNanos ("
              + measuredNanos
              + ") and unmeasuredNanos ("
              + unmeasuredNanos
              + ") must partition scoringWallNanos ("
              + scoringWallNanos
              + ").");
    }
    ConstraintProfileValidation.derived(
        "coverage", coverage, ConstraintProfileValidation.ratio(measuredNanos, scoringWallNanos));
    buckets =
        ConstraintProfileValidation.sortedCopy(
            "buckets", buckets, ConstraintProfileValidation.BUCKET_ORDER);
    ConstraintProfileValidation.section(measuredNanos, eventCount, failureCount, buckets);
  }
}
