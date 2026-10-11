package greycos.solver.greybay.config;

import java.math.BigInteger;
import java.util.Objects;

import greycos.solver.greybay.impl.sampling.NumericSupport;

public record IntegerDimension(
    String name, long lower, long upper, long step, NumericPrior prior, Activation activation)
    implements Dimension {
  public IntegerDimension {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Dimension name must not be blank.");
    Objects.requireNonNull(prior, "prior");
    Objects.requireNonNull(activation, "activation");
    if (lower < -(1L << 53)
        || upper > (1L << 53)
        || lower > upper
        || step <= 0
        || !BigInteger.valueOf(upper)
            .subtract(BigInteger.valueOf(lower))
            .mod(BigInteger.valueOf(step))
            .equals(BigInteger.ZERO))
      throw new IllegalArgumentException(
          "Integer dimension ("
              + name
              + ") requires a reachable inclusive grid in [-2^53, 2^53] and a positive step.");
    NumericSupport.validateInteger(name, lower, upper, step, prior);
  }

  public IntegerDimension(String name, long lower, long upper, long step) {
    this(name, lower, upper, step, NumericPrior.LINEAR, Activation.always());
  }

  public IntegerDimension(String name, long lower, long upper, long step, NumericPrior prior) {
    this(name, lower, upper, step, prior, Activation.always());
  }

  public long gridCount() {
    return (upper - lower) / step + 1;
  }

  @Override
  public IntegerDimension withActivation(Activation condition) {
    return new IntegerDimension(name, lower, upper, step, prior, condition);
  }

  @Override
  public void validateValue(Object value) {
    if (!(value instanceof Long integer)
        || integer < lower
        || integer > upper
        || (integer - lower) % step != 0)
      throw new IllegalArgumentException(
          "Invalid integer value (" + value + ") for (" + name + ").");
  }
}
