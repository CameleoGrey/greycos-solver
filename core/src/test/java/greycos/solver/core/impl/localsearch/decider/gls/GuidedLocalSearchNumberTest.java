package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class GuidedLocalSearchNumberTest {

  @Test
  void checkedIntegralArithmeticWidensAndNarrowsExactly() {
    var maximum = GuidedLocalSearchNumber.of(Long.MAX_VALUE);
    var beyond = maximum.add(GuidedLocalSearchNumber.ONE);
    assertThat(beyond.toBigDecimal()).isEqualByComparingTo("9223372036854775808");
    assertThat(beyond.isPrimitive()).isFalse();
    assertThat(beyond.subtract(GuidedLocalSearchNumber.ONE)).isEqualTo(maximum);
    assertThat(beyond.subtract(GuidedLocalSearchNumber.ONE).isPrimitive()).isTrue();
    assertThat(maximum.multiply(Long.MAX_VALUE).toBigDecimal())
        .isEqualByComparingTo(new BigDecimal(BigInteger.valueOf(Long.MAX_VALUE).pow(2)));
    assertThat(GuidedLocalSearchNumber.of(Long.MIN_VALUE).subtract(maximum).toBigDecimal())
        .isEqualByComparingTo("-18446744073709551615");
  }

  @Test
  void fractionalCostsAccumulateWithoutPerFeatureRounding() {
    var tenth = GuidedLocalSearchNumber.of(new BigDecimal("0.10"));
    assertThat(tenth.multiply(3).toBigDecimal()).isEqualByComparingTo("0.3");
    assertThat(tenth.add(tenth).add(tenth)).isEqualTo(tenth.multiply(3));
    assertThat(GuidedLocalSearchNumber.of(new BigDecimal("1.00")))
        .isEqualTo(GuidedLocalSearchNumber.ONE)
        .hasSameHashCodeAs(GuidedLocalSearchNumber.ONE);
    assertThat(GuidedLocalSearchNumber.of(new BigDecimal("1E+3")).isPrimitive()).isTrue();
  }

  @Test
  void floatingPointValuesPreserveExactIeeeMagnitudeAndRejectNonFiniteNumbers() {
    assertThat(GuidedLocalSearchNumber.of((Number) 0.1d).toBigDecimal())
        .isEqualByComparingTo(new BigDecimal(0.1d));
    assertThat(GuidedLocalSearchNumber.of((Number) 0.1f).toBigDecimal())
        .isEqualByComparingTo(new BigDecimal((double) 0.1f));
    assertThat(GuidedLocalSearchNumber.of((Number) (-0.0d)))
        .isEqualTo(GuidedLocalSearchNumber.ZERO);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> GuidedLocalSearchNumber.of((Number) Double.NaN))
        .withMessageContaining("finite");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> GuidedLocalSearchNumber.of((Number) Float.POSITIVE_INFINITY));
  }
}
