package ai.greycos.solver.core.api.score.stream;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.greycos.solver.core.api.score.constraint.ConstraintRef;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ConstraintBuilder {

  /**
   * Builds a {@link Constraint} from the constraint stream. The {@link ConstraintRef#packageName()
   * constraint package} defaults to the package of the {@link PlanningSolution} class. The
   * constraint will be placed in the {@link Constraint#DEFAULT_CONSTRAINT_GROUP default constraint
   * group}.
   *
   * @param constraintName shows up in {@link ConstraintMatchTotal} during score justification
   */
  default Constraint asConstraint(String constraintName) {
    return asConstraintDescribed(constraintName, "");
  }

  /**
   * Builds a {@link Constraint} from the constraint stream. The {@link ConstraintRef#packageName()
   * constraint package} defaults to the package of the {@link PlanningSolution} class. The
   * constraint will be placed in the {@link Constraint#DEFAULT_CONSTRAINT_GROUP default constraint
   * group}.
   *
   * @param constraintName shows up in {@link ConstraintMatchTotal} during score justification
   * @param constraintDescription can contain any character, but it is recommended to keep it short
   *     and concise
   */
  default Constraint asConstraintDescribed(String constraintName, String constraintDescription) {
    return asConstraintDescribed(
        constraintName, constraintDescription, Constraint.DEFAULT_CONSTRAINT_GROUP);
  }

  /**
   * Builds a {@link Constraint} from the constraint stream. The {@link ConstraintRef#packageName()
   * constraint package} defaults to the package of the {@link PlanningSolution} class. Both the
   * constraint name and the constraint group are only allowed to contain alphanumeric characters,
   * spaces, "-", "_", "'" or ".". The constraint description can contain any character, but it is
   * recommended to keep it short and concise.
   *
   * <p>Unlike the constraint name and group, the constraint description is unlikely to be used
   * externally as an identifier, and therefore doesn't need to be URL-friendly, or protected
   * against injection attacks.
   *
   * @param constraintName shows up in {@link ConstraintMatchTotal} during score justification
   * @param constraintDescription can contain any character, but it is recommended to keep it short
   *     and concise
   * @param constraintGroup not used by the solver directly, but may be used by external tools to
   *     group constraints together, such as by their source or by their purpose
   */
  Constraint asConstraintDescribed(
      String constraintName, String constraintDescription, String constraintGroup);

  /**
   * Builds a {@link Constraint} from the constraint stream.
   *
   * @param constraintName never null, shows up in {@link ConstraintMatchTotal} during score
   *     justification
   * @param constraintPackage never null
   * @return never null
   * @deprecated Constraint package should no longer be used, use {@link #asConstraint(String)}
   *     instead.
   */
  @Deprecated(forRemoval = true, since = "1.13.0")
  Constraint asConstraint(String constraintPackage, String constraintName);
}
