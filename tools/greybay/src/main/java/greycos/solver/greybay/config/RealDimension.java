package greycos.solver.greybay.config;

import java.util.Objects;

import greycos.solver.greybay.impl.sampling.NumericSupport;

public record RealDimension(
    String name, double lower, double upper, NumericPrior prior, Activation activation)
    implements Dimension {
  public RealDimension {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Dimension name must not be blank.");
    Objects.requireNonNull(prior, "prior");
    Objects.requireNonNull(activation, "activation");
    if (!Double.isFinite(lower)
        || !Double.isFinite(upper)
        || lower >= upper
        || prior == NumericPrior.LOGARITHMIC && lower <= 0)
      throw new IllegalArgumentException(
          "Real dimension ("
              + name
              + ") requires finite ordered bounds and positive logarithmic support.");
    lower = lower == 0 ? 0 : lower;
    upper = upper == 0 ? 0 : upper;
    NumericSupport.validateWidth(
        name,
        prior == NumericPrior.LINEAR ? lower : StrictMath.log(lower),
        prior == NumericPrior.LINEAR ? upper : StrictMath.log(upper));
  }

  public RealDimension(String name, double lower, double upper) {
    this(name, lower, upper, NumericPrior.LINEAR, Activation.always());
  }

  public RealDimension(String name, double lower, double upper, NumericPrior prior) {
    this(name, lower, upper, prior, Activation.always());
  }

  @Override
  public RealDimension withActivation(Activation condition) {
    return new RealDimension(name, lower, upper, prior, condition);
  }

  @Override
  public void validateValue(Object value) {
    if (!(value instanceof Double real) || !Double.isFinite(real) || real < lower || real > upper)
      throw new IllegalArgumentException("Invalid real value (" + value + ") for (" + name + ").");
  }
}
