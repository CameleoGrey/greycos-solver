package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorMultipleSolversInvalidEntityClassTest {

  // Empty classes
  @RegisterExtension
  static final QuarkusUnitTest config1 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.\"solver1\".environment-mode", "FULL_ASSERT")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".environment-mode", "PHASE_ASSERT")
          .setArchiveProducer(
              () -> ShrinkWrap.create(JavaArchive.class).addClasses(TestdataQuarkusSolution.class))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining(
                          "No classes were found with a @PlanningEntity annotation."));

  @Inject
  @Named("solver1")
  SolverManager<?, ?> solverManager1;

  @Inject
  @Named("solver2")
  SolverManager<?, ?> solverManager2;

  @Test
  void test() {
    fail("Should not call this method.");
  }
}
