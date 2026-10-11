package greycos.solver.math.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.NullMarked;

/**
 * An immutable integral linear expression. Operations retain the original expression graph; model
 * construction combines coefficients using exact arithmetic and validates the supported long range.
 * The public node types permit independent evaluation without depending on compiler internals.
 */
@NullMarked
public sealed interface LinearExpression
    permits IntVar, LinearExpression.Constant, LinearExpression.Sum, LinearExpression.Scale {

  /** Creates a constant expression. */
  static LinearExpression constant(long value) {
    return new Constant(value);
  }

  /** Creates a sum in iteration order; an empty sum denotes zero. */
  static LinearExpression sum(Collection<? extends LinearExpression> expressions) {
    if (expressions == null) {
      throw new IllegalArgumentException("The expressions to sum must not be null.");
    }
    return new Sum(new ArrayList<>(expressions));
  }

  /** Adds an expression without changing either operand. */
  default LinearExpression plus(LinearExpression other) {
    if (other == null) {
      throw new IllegalArgumentException("The expression to add must not be null.");
    }
    return new Sum(List.of(this, other));
  }

  /** Adds an integral constant. */
  default LinearExpression plus(long value) {
    return plus(constant(value));
  }

  /** Subtracts an expression without changing either operand. */
  default LinearExpression minus(LinearExpression other) {
    if (other == null) {
      throw new IllegalArgumentException("The expression to subtract must not be null.");
    }
    return plus(other.times(-1));
  }

  /**
   * Subtracts an integral constant, including {@link Long#MIN_VALUE}, using exact normalization.
   */
  default LinearExpression minus(long value) {
    return minus(constant(value));
  }

  /** Multiplies by an integral scalar. */
  default LinearExpression times(long coefficient) {
    return new Scale(this, coefficient);
  }

  /** Creates a non-strict upper-bound constraint. */
  default LinearConstraint le(long bound) {
    return new LinearConstraint(this, Relation.LE, bound);
  }

  /** Creates a non-strict lower-bound constraint. */
  default LinearConstraint ge(long bound) {
    return new LinearConstraint(this, Relation.GE, bound);
  }

  /** Creates an equality constraint. */
  default LinearConstraint eq(long bound) {
    return new LinearConstraint(this, Relation.EQ, bound);
  }

  /** An original integral constant node. */
  record Constant(long value) implements LinearExpression {}

  /** An original sum node with an immutable, ordered operand list. */
  record Sum(List<LinearExpression> operands) implements LinearExpression {
    public Sum {
      if (operands == null || operands.stream().anyMatch(operand -> operand == null)) {
        throw new IllegalArgumentException("The sum operands and their elements must not be null.");
      }
      operands = List.copyOf(operands);
    }
  }

  /** An original scalar multiplication node. */
  record Scale(LinearExpression expression, long coefficient) implements LinearExpression {
    public Scale {
      if (expression == null) {
        throw new IllegalArgumentException("The expression to multiply must not be null.");
      }
    }
  }
}
