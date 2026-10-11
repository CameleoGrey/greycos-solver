package greycos.solver.math.api;

import org.jspecify.annotations.NullMarked;

/** A named hard constraint retaining its original expression and comparison. */
@NullMarked
public record NamedConstraint(
    String name, LinearExpression expression, Relation relation, long bound) {
  public NamedConstraint {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("The constraint name (" + name + ") must not be blank.");
    }
    if (expression == null || relation == null) {
      throw new IllegalArgumentException(
          "The expression and relation of constraint (" + name + ") must not be null.");
    }
  }
}
