package greycos.solver.math.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Map;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.math.api.LinearExpression;
import greycos.solver.math.api.MathModel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class IndependentEvaluatorTest {

  @Test
  void replaysOriginalRowsObjectivesAndSignedResiduals() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", -3, 3);
    builder.addConstraint("le", x.times(-2).plus(4).le(1));
    builder.addConstraint("ge", x.plus(-5).ge(-1));
    builder.addConstraint("eq", x.times(3).eq(4));
    builder.minimize(x.times(-7).plus(2));
    var replay =
        IndependentEvaluator.evaluate(builder.build(), Map.of(x, 2L), HardSoftScore.of(-4, 12));
    assertThat(replay.objectiveValue()).isEqualTo(-12);
    assertThat(replay.reports())
        .extracting(report -> report.name())
        .containsExactly("le", "ge", "eq");
    assertThat(replay.reports())
        .extracting(report -> report.residual())
        .containsExactly(-1L, -2L, 2L);
    assertThat(replay.reports())
        .extracting(report -> report.violation())
        .containsExactly(0L, 2L, 2L);
  }

  @Test
  void acceptsSignedHardMinimumWithoutPositiveLongOverflow() {
    var builder = MathModel.builder();
    builder.addConstraint("maximum", LinearExpression.constant(Long.MAX_VALUE).le(0));
    builder.addConstraint("one", LinearExpression.constant(1).le(0));
    builder.maximize(LinearExpression.constant(Long.MIN_VALUE));
    var replay =
        IndependentEvaluator.evaluate(
            builder.build(), Map.of(), HardSoftScore.of(Long.MIN_VALUE, Long.MIN_VALUE));
    assertThat(replay.reports())
        .extracting(report -> report.violation())
        .containsExactly(Long.MAX_VALUE, 1L);
    assertThat(replay.objectiveValue()).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  void rejectsIncompleteForeignOutOfRangeAndChangedFixedAssignments() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", 0, 3);
    builder.fix(x, 2);
    var model = builder.build();
    assertThatIllegalStateException()
        .isThrownBy(() -> IndependentEvaluator.evaluate(model, Map.of(), HardSoftScore.ZERO))
        .withMessageContaining("Assignment");
    var foreign = MathModel.builder().intVar("x", 0, 3);
    assertThatIllegalStateException()
        .isThrownBy(
            () -> IndependentEvaluator.evaluate(model, Map.of(foreign, 2L), HardSoftScore.ZERO))
        .withMessageContaining("x");
    assertThatIllegalStateException()
        .isThrownBy(() -> IndependentEvaluator.evaluate(model, Map.of(x, 4L), HardSoftScore.ZERO))
        .withMessageContaining("out-of-domain");
    assertThatIllegalStateException()
        .isThrownBy(() -> IndependentEvaluator.evaluate(model, Map.of(x, 1L), HardSoftScore.ZERO))
        .withMessageContaining("Fixed variable");
  }

  @Test
  void rejectsBackendScoreDisagreementIncludingStructuralCorruption() {
    var builder = MathModel.builder();
    var x = builder.binaryVar("x");
    builder.maximize(x);
    var model = builder.build();
    assertThatIllegalStateException()
        .isThrownBy(() -> IndependentEvaluator.evaluate(model, Map.of(x, 1L), HardSoftScore.ZERO))
        .withMessageContaining("differs from backend");
    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                IndependentEvaluator.evaluate(model, Map.of(x, 1L), new HardSoftScore(-1L, 0L, 1L)))
        .withMessageContaining("differs from backend");
  }

  @Test
  @Timeout(10)
  void handlesDeepAndSharedOriginalExpressionsWithoutRecursionOrExponentialTraversal() {
    var builder = MathModel.builder();
    var x = builder.binaryVar("x");
    LinearExpression shared = x;
    for (int i = 0; i < 150; i++) {
      shared = shared.plus(shared);
    }
    LinearExpression expression = shared.minus(shared);
    for (int i = 0; i < 10_000; i++) {
      expression = expression.plus(1);
    }
    builder.maximize(expression);
    var replay =
        IndependentEvaluator.evaluate(
            builder.build(), Map.of(x, 1L), HardSoftScore.of(0L, 10_000L));
    assertThat(replay.objectiveValue()).isEqualTo(10_000L);
  }
}
