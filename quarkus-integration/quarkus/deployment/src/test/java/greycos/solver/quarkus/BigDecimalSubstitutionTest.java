package greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

class BigDecimalSubstitutionTest {

  @Test
  void roundTripPreservesUnscaledValueAndScale() {
    var substitution = new BigDecimalSubstitution();
    for (var original :
        List.of(
            BigDecimal.ZERO,
            new BigDecimal("0.0000"),
            new BigDecimal("0E+10"),
            new BigDecimal("0.1250"),
            new BigDecimal("1E-30"),
            new BigDecimal("1E+30"),
            new BigDecimal("-123456789012345678901234567890.012345678900"),
            new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE),
            new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE))) {
      var restored = substitution.deserialize(substitution.serialize(original));
      assertThat(restored.unscaledValue()).isEqualTo(original.unscaledValue());
      assertThat(restored.scale()).isEqualTo(original.scale());
    }
  }
}
