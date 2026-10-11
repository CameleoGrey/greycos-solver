package greycos.solver.core.api.score.profile;

import java.util.Objects;

import greycos.solver.core.api.score.stream.ConstraintRef;

/**
 * Final activity of a declared constraint in one generation.
 *
 * @param constraintRef the stable constraint identifier
 * @param activity the final activity, including disabled and unresolved constraints
 */
public record ConstraintProfileConstraintActivity(
    ConstraintRef constraintRef, ConstraintProfile.Activity activity) {

  public ConstraintProfileConstraintActivity {
    Objects.requireNonNull(constraintRef, "constraintRef");
    Objects.requireNonNull(activity, "activity");
  }
}
