package ai.greycos.solver.core.impl.score.stream.test;

import ai.greycos.solver.core.api.score.stream.test.ConstraintVerifier;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.constraintweightoverrides.TestdataConstraintWeightOverridesConstraintProvider;
import ai.greycos.solver.core.testcotwin.constraintweightoverrides.TestdataConstraintWeightOverridesSolution;

import org.junit.jupiter.api.Test;

class NullOverridesTest {

  private final ConstraintVerifier<
          TestdataConstraintWeightOverridesConstraintProvider,
          TestdataConstraintWeightOverridesSolution>
      constraintVerifier =
          ConstraintVerifier.build(
              new TestdataConstraintWeightOverridesConstraintProvider(),
              TestdataConstraintWeightOverridesSolution.class,
              TestdataEntity.class);

  @Test
  void doesNotThrowNPEOnNoOverrides() {
    constraintVerifier
        .verifyThat(TestdataConstraintWeightOverridesConstraintProvider::firstConstraint)
        .given()
        .penalizesBy(0);
  }
}
