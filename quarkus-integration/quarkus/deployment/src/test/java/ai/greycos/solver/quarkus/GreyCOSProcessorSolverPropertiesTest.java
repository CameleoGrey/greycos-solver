package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.cotwin.common.CotwinAccessType;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
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

class GreyCOSProcessorSolverPropertiesTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.environment-mode", "FULL_ASSERT")
          .overrideConfigKey("quarkus.greycos.solver.daemon", "true")
          .overrideConfigKey(
              "quarkus.greycos.solver.nearby-distance-meter-class",
              "ai.greycos.solver.quarkus.testcotwin.dummy.DummyDistanceMeter")
          .overrideConfigKey("quarkus.greycos.solver.move-thread-count", "2")
          .overrideConfigKey("quarkus.greycos.solver.cotwin-access-type", "REFLECTION")
          .overrideConfigKey("quarkus.greycos.solver.termination.spent-limit", "4h")
          .overrideConfigKey("quarkus.greycos.solver.termination.unimproved-spent-limit", "5h")
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
          .overrideConfigKey(
              "quarkus.greycos.solver.termination.diminished-returns.enabled", "true")
          .overrideConfigKey(
              "quarkus.greycos.solver.termination.diminished-returns.sliding-window-duration", "6h")
          .overrideConfigKey(
              "quarkus.greycos.solver.termination.diminished-returns.minimum-improvement-ratio",
              "0.5")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class,
                          DummyDistanceMeter.class));

  @Inject SolverConfig solverConfig;
  @Inject SolverFactory<TestdataQuarkusSolution> solverFactory;

  @Test
  void solverProperties() {
    assertEquals(EnvironmentMode.FULL_ASSERT, solverConfig.getEnvironmentMode());
    assertTrue(solverConfig.getDaemon());
    assertEquals("2", solverConfig.getMoveThreadCount());
    assertEquals(CotwinAccessType.REFLECTION, solverConfig.getCotwinAccessType());
    assertEquals(null, solverConfig.getScoreDirectorFactoryConfig().getConstraintStreamImplType());
    assertNotNull(solverConfig.getNearbyDistanceMeterClass());
    assertNotNull(solverFactory);
  }

  @Test
  void terminationProperties() {
    assertEquals(Duration.ofHours(4), solverConfig.getTerminationConfig().getSpentLimit());
    assertEquals(
        Duration.ofHours(5), solverConfig.getTerminationConfig().getUnimprovedSpentLimit());
    assertEquals(
        SimpleScore.of(0).toString(), solverConfig.getTerminationConfig().getBestScoreLimit());

    var terminationConfig = solverConfig.getTerminationConfig();
    assertNotNull(terminationConfig);
    assertNotNull(terminationConfig.getDiminishedReturnsConfig());
    assertEquals(
        Duration.ofHours(6),
        terminationConfig.getDiminishedReturnsConfig().getSlidingWindowDuration());
    assertEquals(0.5, terminationConfig.getDiminishedReturnsConfig().getMinimumImprovementRatio());
  }
}
