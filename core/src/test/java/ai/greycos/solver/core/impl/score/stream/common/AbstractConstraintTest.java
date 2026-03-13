package ai.greycos.solver.core.impl.score.stream.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.testconstraint.TestConstraint;
import ai.greycos.solver.core.testconstraint.TestConstraintFactory;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AbstractConstraintTest {

  @ParameterizedTest
  @CsvSource({
    "Hello123,      true",
    "こんにちは123,   true",
    "你好123,        true",
    "Hello_123,     true",
    "Hello-123,     true",
    "Hello_123😊,   false",
    "_Hello123,     false",
    "-Hello123,     false",
    "😊Hello123,    false",
    "123Hello,      false",
    "Hello 123,     false",
    "Hello@123,     false"
  })
  void constraintGroupValidation(String constraintGroup, boolean isValid) {
    var solutionDescriptor = TestdataSolution.buildSolutionDescriptor();
    var constraintFactory =
        new TestConstraintFactory<TestdataSolution, SimpleScore>(solutionDescriptor);
    if (isValid) {
      assertThatCode(
              () ->
                  new TestConstraint<>(
                      constraintFactory, "Test Constraint", constraintGroup, SimpleScore.ZERO))
          .doesNotThrowAnyException();
    } else {
      assertThatThrownBy(
              () ->
                  new TestConstraint<>(
                      constraintFactory, "Test Constraint", constraintGroup, SimpleScore.ZERO))
          .hasMessageContaining("invalid characters")
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
