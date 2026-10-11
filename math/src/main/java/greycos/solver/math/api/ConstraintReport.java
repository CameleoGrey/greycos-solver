package greycos.solver.math.api;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * An independently replayed mathematical row. The signed residual is {@code activity - bound} for
 * every relation. The nonnegative violation is zero exactly when this row is satisfied.
 *
 * @param name the model constraint name
 * @param relation the original comparison
 * @param activity the original left-hand expression value
 * @param bound the right-hand bound
 * @param residual the signed left-minus-right residual
 * @param violation the nonnegative violation magnitude
 */
@NullMarked
public record ConstraintReport(
    String name, Relation relation, long activity, long bound, long residual, long violation) {

  public ConstraintReport {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(relation, "relation");
    if (violation < 0L) {
      throw new IllegalArgumentException("The violation (" + violation + ") must be nonnegative.");
    }
  }
}
