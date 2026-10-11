package greycos.solver.math.api;

import org.jspecify.annotations.NullMarked;

/** An immutable comparison, given a name when added to a model. */
@NullMarked
public record LinearConstraint(LinearExpression expression, Relation relation, long bound) {
  public LinearConstraint {
    if (expression == null || relation == null) {
      throw new IllegalArgumentException(
          "The constraint expression and relation must not be null.");
    }
  }
}
