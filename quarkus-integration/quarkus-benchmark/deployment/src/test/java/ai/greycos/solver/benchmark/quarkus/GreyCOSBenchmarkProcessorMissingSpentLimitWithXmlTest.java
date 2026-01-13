package ai.greycos.solver.benchmark.quarkus;

import static org.assertj.core.api.Assertions.assertThatCode;

import ai.greycos.solver.benchmark.config.PlannerBenchmarkConfig;
import ai.greycos.solver.benchmark.quarkus.testcotwin.normal.constraints.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.benchmark.quarkus.testcotwin.normal.cotwin.TestdataQuarkusEntity;
import ai.greycos.solver.benchmark.quarkus.testcotwin.normal.cotwin.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSBenchmarkProcessorMissingSpentLimitWithXmlTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey(
              "quarkus.greycos.benchmark.solver-benchmark-config-xml",
              "emptySolverBenchmarkConfig.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addAsResource("emptySolverBenchmarkConfig.xml")
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class));

  @Test
  void benchmark() throws InterruptedException {
    assertThatCode(
            () ->
                new GreyCOSBenchmarkRecorder()
                    .benchmarkConfigSupplier(new PlannerBenchmarkConfig(), null)
                    .get())
        .hasMessageContainingAll(
            "At least one of the properties",
            "quarkus.greycos.benchmark.solver.termination.spent-limit",
            "quarkus.greycos.benchmark.solver.termination.best-score-limit",
            "quarkus.greycos.benchmark.solver.termination.unimproved-spent-limit",
            "is required if termination is not configured");
  }
}
