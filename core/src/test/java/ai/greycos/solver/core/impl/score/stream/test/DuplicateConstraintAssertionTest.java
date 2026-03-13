package ai.greycos.solver.core.impl.score.stream.test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.greycos.solver.core.api.score.stream.test.ConstraintVerifier;
import ai.greycos.solver.core.testcotwin.constraintverifier.TestdataConstraintVerifierDuplicateConstraintProvider;
import ai.greycos.solver.core.testcotwin.constraintverifier.TestdataConstraintVerifierExtendedSolution;
import ai.greycos.solver.core.testcotwin.constraintverifier.TestdataConstraintVerifierFirstEntity;
import ai.greycos.solver.core.testcotwin.constraintverifier.TestdataConstraintVerifierSecondEntity;

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
