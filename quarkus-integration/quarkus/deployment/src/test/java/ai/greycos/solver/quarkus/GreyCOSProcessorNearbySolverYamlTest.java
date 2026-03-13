package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.quarkus.testcotwin.dummy.DummyDistanceMeter;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorNearbySolverYamlTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class,
                          DummyDistanceMeter.class)
                      .addAsResource(
                          "ai/greycos/solver/quarkus/single-solver/application-nearby.yaml",
                          "application.yaml"));

  @Inject SolverConfig solverConfig;
  @Inject SolverFactory<TestdataQuarkusSolution> solverFactory;

  @Test
  void solverProperties() {
    assertEquals(EnvironmentMode.FULL_ASSERT, solverConfig.getEnvironmentMode());
    assertNotNull(solverConfig.getNearbyDistanceMeterClass());
    assertTrue(solverConfig.getDaemon());
    assertEquals("2", solverConfig.getMoveThreadCount());
    assertNotNull(solverFactory);
  }

  @Test
  void terminationProperties() {
    assertEquals(Duration.ofHours(4), solverConfig.getTerminationConfig().getSpentLimit());
    assertEquals(
        Duration.ofHours(5), solverConfig.getTerminationConfig().getUnimprovedSpentLimit());
    assertEquals(
        SimpleScore.of(0).toString(), solverConfig.getTerminationConfig().getBestScoreLimit());
  }
}
