package ai.greycos.solver.benchmark.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

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

class GreyCOSBenchmarkProcessorPhasesTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.benchmark.solver.termination.best-score-limit", "0")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class)
                      .addAsResource("solverConfigWithPhases.xml", "solverConfig.xml")
                      .addAsResource(
                          "solverBenchmarkConfigWithPhases.xml", "solverBenchmarkConfig.xml"));

  @Inject PlannerBenchmarkConfig plannerBenchmarkConfig;

  @Test
  void doesNotInheritPhasesFromSolverConfig() {
    assertThat(
            plannerBenchmarkConfig
                .getSolverBenchmarkConfigList()
                .get(0)
                .getSolverConfig()
                .getPhaseConfigList())
        .hasSize(2);
    assertThat(
            plannerBenchmarkConfig
                .getSolverBenchmarkConfigList()
                .get(1)
                .getSolverConfig()
                .getPhaseConfigList())
        .hasSize(3);
  }
}
