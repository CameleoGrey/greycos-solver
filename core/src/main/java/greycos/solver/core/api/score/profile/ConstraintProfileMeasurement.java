package greycos.solver.core.api.score.profile;

import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.score.stream.ConstraintRef;

/**
 * A unit's exclusive work in one section, entry ownership epoch, and callback operation.
 *
 * @param stage the section receiving this duration
 * @param ownershipEpoch the generation-local epoch captured on invocation entry
 * @param ownershipState the state matching that epoch
 * @param operation the callback operation
 * @param owners exact, sorted, distinct owners captured on entry
 * @param exclusiveNanos exclusive duration charged to this section
 * @param eventCount invocations entered in this section
 * @param failureCount failed invocations entered in this section
 * @param partialInvocations whether a duration was split across sections
 */
public record ConstraintProfileMeasurement(
    ConstraintProfile.Stage stage,
    int ownershipEpoch,
    ConstraintProfile.OwnershipState ownershipState,
    ConstraintProfile.Operation operation,
    List<ConstraintRef> owners,
    long exclusiveNanos,
    long eventCount,
    long failureCount,
    boolean partialInvocations) {

  public ConstraintProfileMeasurement {
    Objects.requireNonNull(stage, "stage");
    ConstraintProfileValidation.nonnegative("ownershipEpoch", ownershipEpoch);
    Objects.requireNonNull(ownershipState, "ownershipState");
    Objects.requireNonNull(operation, "operation");
    owners = ConstraintProfileValidation.owners(owners);
    if (ownershipState == ConstraintProfile.OwnershipState.UNRESOLVED && !owners.isEmpty()) {
      throw new IllegalArgumentException(
          "The owners (" + owners + ") must be empty for ownershipState (" + ownershipState + ").");
    }
    ConstraintProfileValidation.measurement(
        exclusiveNanos, eventCount, failureCount, partialInvocations);
  }
}
