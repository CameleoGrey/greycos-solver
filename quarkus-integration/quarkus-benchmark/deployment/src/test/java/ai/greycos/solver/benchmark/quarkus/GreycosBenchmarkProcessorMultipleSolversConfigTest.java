package ai.greycos.solver.benchmark.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import ai.greycos.solver.benchmark.quarkus.testdomain.normal.constraints.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.benchmark.quarkus.testdomain.normal.domain.TestdataQuarkusEntity;
import ai.greycos.solver.benchmark.quarkus.testdomain.normal.domain.TestdataQuarkusSolution;
import ai.greycos.solver.core.api.solver.SolverManager;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

class GreyCOSBenchmarkProcessorMultipleSolversConfigTest {

  // It is not possible run a benchmark for multiple solvers
  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.\"solver1\".termination.spent-limit", "30s")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".termination.spent-limit", "30s")
          .overrideConfigKey("quarkus.greycos.benchmark.solver.termination.best-score-limit", "0")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(ConfigurationException.class)
                      .hasMessageContaining(
                          """
                                    When defining multiple solvers, the benchmark feature is not enabled.
                                    Consider using separate <solverBenchmark> instances for evaluating different solver configurations."""));

  @Inject
  @Named("solver1")
  SolverManager<?, ?> solverManager1;

  @Inject
  @Named("solver2")
  SolverManager<?, ?> solverManager2;

  @Test
  void benchmark() throws ExecutionException, InterruptedException {
    fail("It won't be executed");
  }
}
