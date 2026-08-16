package greycos.solver.core.impl.score.stream.test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import greycos.solver.core.api.score.stream.test.ConstraintVerifier;
import greycos.solver.core.testcotwin.constraintverifier.TestdataConstraintVerifierDuplicateConstraintProvider;
import greycos.solver.core.testcotwin.constraintverifier.TestdataConstraintVerifierExtendedSolution;
import greycos.solver.core.testcotwin.constraintverifier.TestdataConstraintVerifierFirstEntity;
import greycos.solver.core.testcotwin.constraintverifier.TestdataConstraintVerifierSecondEntity;

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
