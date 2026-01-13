package ai.greycos.solver.benchmark.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import jakarta.inject.Inject;

import ai.greycos.solver.benchmark.config.PlannerBenchmarkConfig;
import ai.greycos.solver.benchmark.quarkus.testcotwin.normal.constraints.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.benchmark.quarkus.testcotwin.normal.cotwin.TestdataQuarkusEntity;
import ai.greycos.solver.benchmark.quarkus.testcotwin.normal.cotwin.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSBenchmarkProcessorPropertiesTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey(
              "quarkus.greycos.benchmark.solver.termination.best-score-limit", "0hard/-1200soft")
          .overrideConfigKey("quarkus.greycos.benchmark.solver.termination.spent-limit", "5m")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class));

  @Inject PlannerBenchmarkConfig plannerBenchmarkConfig;

  @Test
  void terminationProperties() {
    assertEquals(
        Duration.ofMinutes(5),
        plannerBenchmarkConfig
            .getSolverBenchmarkConfigList()
            .get(0)
            .getSolverConfig()
            .getTerminationConfig()
            .getSpentLimit());
    assertEquals(
        "0hard/-1200soft",
        plannerBenchmarkConfig
            .getSolverBenchmarkConfigList()
            .get(0)
            .getSolverConfig()
            .getTerminationConfig()
            .getBestScoreLimit());
  }
}
