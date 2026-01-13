package ai.greycos.solver.benchmark.quarkus;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.ExecutionException;

import ai.greycos.solver.benchmark.config.PlannerBenchmarkConfig;
import ai.greycos.solver.benchmark.quarkus.testcotwin.normal.constraints.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.benchmark.quarkus.testcotwin.normal.cotwin.TestdataQuarkusEntity;
import ai.greycos.solver.benchmark.quarkus.testcotwin.normal.cotwin.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSBenchmarkProcessorMissingSpentLimitTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.test.flat-class-path", "true")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class));

  @Test
  void benchmark() throws ExecutionException, InterruptedException {
    assertThatCode(
            () ->
                new GreyCOSBenchmarkRecorder()
                    .benchmarkConfigSupplier(new PlannerBenchmarkConfig(), null)
                    .get())
        .hasMessageContaining(
            "At least one of the properties quarkus.greycos.benchmark.solver.termination.spent-limit, quarkus.greycos.benchmark.solver.termination.best-score-limit, quarkus.greycos.benchmark.solver.termination.unimproved-spent-limit is required if termination is not configured in the inherited solver benchmark config and solverBenchmarkBluePrint is used.");
  }
}
