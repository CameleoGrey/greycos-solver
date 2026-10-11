package greycos.solver.core.api.score.profile;

import java.util.List;

import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.Nullable;

/**
 * Exclusive work charged once to an exact owner set. An empty owner set means unattributed work.
 *
 * @param owners sorted, distinct recorded owners
 * @param exclusiveNanos exclusive duration
 * @param eventCount invocations entered in this section
 * @param failureCount failed invocations entered in this section
 * @param partialInvocations whether any invocation duration crosses section boundaries
 * @param measuredShare fraction of its section's measured time, or null for a zero denominator
 * @param averageNanosPerCall duration per call, or null for zero calls or partial invocations
 */
public record ConstraintProfileBucket(
    List<ConstraintRef> owners,
    long exclusiveNanos,
    long eventCount,
    long failureCount,
    boolean partialInvocations,
    @Nullable Double measuredShare,
    @Nullable Double averageNanosPerCall) {

  public ConstraintProfileBucket {
    owners = ConstraintProfileValidation.owners(owners);
    ConstraintProfileValidation.measurement(
        exclusiveNanos, eventCount, failureCount, partialInvocations);
    ConstraintProfileValidation.fraction("measuredShare", measuredShare);
    ConstraintProfileValidation.derived(
        "averageNanosPerCall",
        averageNanosPerCall,
        partialInvocations ? null : ConstraintProfileValidation.ratio(exclusiveNanos, eventCount));
  }
}
