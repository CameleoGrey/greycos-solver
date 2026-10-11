package greycos.solver.math.impl.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RowTest {

  @Test
  void runtimeArithmeticRemainsChecked() {
    var le = new Row(0, "le", RowKind.LE, 1, -1);
    assertThatThrownBy(() -> le.activity(Long.MAX_VALUE)).isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> le.residual(Long.MAX_VALUE)).isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> new Row(0, "eq", RowKind.EQ, 0, 0).violation(Long.MIN_VALUE))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> new Row(0, "ge", RowKind.GE, 0, 0).violation(Long.MIN_VALUE))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> new Row(0, "min", RowKind.MINIMIZE, 0, 0).softImpact(Long.MIN_VALUE))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void inequalitiesDoNotNegateSatisfiedMinimumResidual() {
    var le = new Row(0, "le", RowKind.LE, 0, 0);
    assertThat(le.residual(Long.MIN_VALUE)).isEqualTo(Long.MIN_VALUE);
    assertThat(le.violation(Long.MIN_VALUE)).isZero();
    assertThat(le.violation(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    assertThat(le.softImpact(Long.MAX_VALUE)).isZero();
    var ge = new Row(0, "ge", RowKind.GE, 0, 0);
    assertThat(ge.violation(Long.MAX_VALUE)).isZero();
  }
}
