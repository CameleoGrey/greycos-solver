package greycos.solver.core.api.score.profile;

import java.util.Objects;

/**
 * A generation-local ownership state; earlier measurements retain their entry epoch.
 *
 * @param ownershipEpoch nonnegative, generation-local epoch number
 * @param ownershipState the meaning of the owners recorded in this epoch
 */
public record ConstraintProfileOwnershipEpoch(
    int ownershipEpoch, ConstraintProfile.OwnershipState ownershipState) {

  public ConstraintProfileOwnershipEpoch {
    ConstraintProfileValidation.nonnegative("ownershipEpoch", ownershipEpoch);
    Objects.requireNonNull(ownershipState, "ownershipState");
  }
}
