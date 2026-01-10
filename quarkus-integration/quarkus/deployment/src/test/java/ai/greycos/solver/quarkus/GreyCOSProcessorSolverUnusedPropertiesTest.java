package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.quarkus.testdomain.dummy.DummyDistanceMeter;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorSolverUnusedPropertiesTest {

  @RegisterExtension
  static final QuarkusUnitTest config1 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.environment-mode", "FULL_ASSERT")
          .overrideConfigKey("quarkus.greycos.solver.\"solver1\".daemon", "true")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver1\".nearby-distance-meter-class",
              "ai.greycos.solver.quarkus.testdomain.dummy.DummyDistanceMeter")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".move-thread-count", "2")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".domain-access-type", "REFLECTION")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".termination.spent-limit", "4h")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver3\".termination.unimproved-spent-limit", "5h")
          .overrideConfigKey("quarkus.greycos.solver.\"solver3\".termination.best-score-limit", "0")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class,
                          DummyDistanceMeter.class))
          .assertException(
              throwable -> {
                // This failure happens at build time and does not have access to solver3, which
                // is only defined at runtime
                assertThat(throwable)
                    .hasMessageContaining("Some names defined in properties")
                    .hasMessageContaining("solver2")
                    .hasMessageContaining(
                        "do not have a corresponding @"
                            + Named.class.getSimpleName()
                            + " injection point")
                    .hasMessageContaining("solver1");
              });

  @RegisterExtension
  static final QuarkusUnitTest config2 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.environment-mode", "FULL_ASSERT")
          .overrideConfigKey("quarkus.greycos.solver.\"solver1\".daemon", "true")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver1\".nearby-distance-meter-class",
              "ai.greycos.solver.quarkus.testdomain.dummy.DummyDistanceMeter")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver2\".termination.unimproved-spent-limit", "5h")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".termination.best-score-limit", "0")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver3\".termination.unimproved-spent-limit", "5h")
          .overrideConfigKey("quarkus.greycos.solver.\"solver3\".termination.best-score-limit", "0")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class,
                          DummyDistanceMeter.class))
          .assertException(
              throwable -> {
                // The build succeeds, but runtime fails at startup due to runtime properties
                // referencing
                // missing Named annotations
                assertThat(throwable)
                    .hasMessageContaining("Some names defined in properties")
                    .hasMessageContaining("solver2")
                    .hasMessageContaining("solver3")
                    .hasMessageContaining(
                        "do not have a corresponding @"
                            + Named.class.getSimpleName()
                            + " injection point")
                    .hasMessageContaining("solver1");
              });

  @Inject SolverConfig solverConfig;

  @Inject
  @Named("solver1")
  SolverManager<TestdataQuarkusSolution, ?> solverManager;

  @Test
  void solve() {
    fail("Build should fail");
  }
}
