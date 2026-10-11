package greycos.solver.greybay.config;

import java.util.Map;
import java.util.Objects;

public record FixedDimension(String name, Object value, Activation activation)
    implements Dimension {
  public FixedDimension {
    Objects.requireNonNull(activation, "activation");
    value = ParameterAssignment.of(Map.of(name, value)).get(name);
  }

  public FixedDimension(String name, Object value) {
    this(name, value, Activation.always());
  }

  @Override
  public FixedDimension withActivation(Activation condition) {
    return new FixedDimension(name, value, condition);
  }

  @Override
  public void validateValue(Object supplied) {
    if (!value.equals(supplied))
      throw new IllegalArgumentException(
          "Invalid fixed value (" + supplied + ") for (" + name + ").");
  }
}
