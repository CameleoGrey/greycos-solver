package greycos.solver.quarkus;

import java.math.BigDecimal;

import io.quarkus.runtime.ObjectSubstitution;

/** Records a decimal's canonical string, preserving both its value and its scale. */
public final class BigDecimalSubstitution implements ObjectSubstitution<BigDecimal, String> {

  @Override
  public String serialize(BigDecimal value) {
    return value.toString();
  }

  @Override
  public BigDecimal deserialize(String value) {
    return new BigDecimal(value);
  }
}
