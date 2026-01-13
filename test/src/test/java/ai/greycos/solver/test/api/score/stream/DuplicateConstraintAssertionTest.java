package ai.greycos.solver.test.api.score.stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.greycos.solver.test.api.testcotwin.TestdataConstraintVerifierDuplicateConstraintProvider;
import ai.greycos.solver.test.api.testcotwin.TestdataConstraintVerifierExtendedSolution;
import ai.greycos.solver.test.api.testcotwin.TestdataConstraintVerifierFirstEntity;
import ai.greycos.solver.test.api.testcotwin.TestdataConstraintVerifierSecondEntity;

import org.junit.jupiter.api.Test;

class DuplicateConstraintAssertionTest {

  private final ConstraintVerifier<
          TestdataConstraintVerifierDuplicateConstraintProvider,
          TestdataConstraintVerifierExtendedSolution>
      constraintVerifier =
          ConstraintVerifier.build(
              new TestdataConstraintVerifierDuplicateConstraintProvider(),
              TestdataConstraintVerifierExtendedSolution.class,
              TestdataConstraintVerifierFirstEntity.class,
              TestdataConstraintVerifierSecondEntity.class);

  @Test
  void throwsExceptionOnDuplicateConstraintId() {
    assertThatThrownBy(
            () ->
                constraintVerifier.verifyThat(
                    TestdataConstraintVerifierDuplicateConstraintProvider::penalizeEveryEntity))
        .hasMessageContaining("Penalize every standard entity");
  }
}
