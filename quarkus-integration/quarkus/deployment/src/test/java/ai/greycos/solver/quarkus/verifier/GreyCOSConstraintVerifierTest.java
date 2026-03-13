package ai.greycos.solver.quarkus.verifier;

import java.util.Arrays;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.test.ConstraintVerifier;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSConstraintVerifierTest {
  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class));

  @Inject
  ConstraintVerifier<TestdataQuarkusConstraintProvider, TestdataQuarkusSolution> constraintVerifier;

  @Test
  void constraintVerifier() {
    TestdataQuarkusSolution solution = new TestdataQuarkusSolution();
    TestdataQuarkusEntity entityA = new TestdataQuarkusEntity();
    TestdataQuarkusEntity entityB = new TestdataQuarkusEntity();
    entityA.setValue("A");
    entityB.setValue("A");

    solution.setEntityList(Arrays.asList(entityA, entityB));
    solution.setValueList(Arrays.asList("A", "B"));
    constraintVerifier.verifyThat().givenSolution(solution).scores(SimpleScore.of(-2));

    entityB.setValue("B");
    constraintVerifier.verifyThat().givenSolution(solution).scores(SimpleScore.ZERO);
  }
}
