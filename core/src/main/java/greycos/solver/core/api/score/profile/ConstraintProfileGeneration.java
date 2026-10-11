package greycos.solver.core.api.score.profile;

import java.util.Comparator;
import java.util.List;

/**
 * Metadata for a Bavet session, retained after that session is replaced or closed.
 *
 * @param generationId unique run-local session identity
 * @param initializationCompleted whether the first settlement completed successfully
 * @param ownershipEpochs ownership states in ascending local epoch order
 * @param constraints final activity of every declared constraint, including disabled constraints
 */
public record ConstraintProfileGeneration(
    long generationId,
    boolean initializationCompleted,
    List<ConstraintProfileOwnershipEpoch> ownershipEpochs,
    List<ConstraintProfileConstraintActivity> constraints) {

  public ConstraintProfileGeneration {
    ConstraintProfileValidation.nonnegative("generationId", generationId);
    ownershipEpochs =
        ConstraintProfileValidation.sortedCopy(
            "ownershipEpochs",
            ownershipEpochs,
            Comparator.comparingInt(ConstraintProfileOwnershipEpoch::ownershipEpoch));
    constraints =
        ConstraintProfileValidation.sortedCopy(
            "constraints",
            constraints,
            Comparator.comparing(ConstraintProfileConstraintActivity::constraintRef));
    ConstraintProfileValidation.unique(
        "ownershipEpoch", ownershipEpochs, ConstraintProfileOwnershipEpoch::ownershipEpoch);
    ConstraintProfileValidation.unique(
        "constraintRef", constraints, ConstraintProfileConstraintActivity::constraintRef);
  }
}
