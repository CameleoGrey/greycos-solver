package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import ai.greycos.solver.quarkus.testdomain.dummy.DummyDistanceMeter;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.config.ConfigValidationException;

class GreycosProcessorSolverInvalidNearbyClassTest {

  // Class not found
  @RegisterExtension
  static final QuarkusUnitTest config1 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.environment-mode", "FULL_ASSERT")
          .overrideConfigKey("quarkus.greycos.solver.daemon", "true")
          .overrideConfigKey(
              "quarkus.greycos.solver.nearby-distance-meter-class",
              DummyDistanceMeter.class.getName())
          .overrideConfigKey("quarkus.greycos.solver.move-thread-count", "2")
          .overrideConfigKey("quarkus.greycos.solver.domain-access-type", "REFLECTION")
          .overrideConfigKey("quarkus.greycos.solver.termination.spent-limit", "4h")
          .overrideConfigKey("quarkus.greycos.solver.termination.unimproved-spent-limit", "5h")
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
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
                      .isInstanceOf(ConfigValidationException.class)
                      .hasMessageContaining(
                          "The config property quarkus.greycos.solver.nearby-distance-meter-class with the config value")
                      .hasMessageContaining(DummyDistanceMeter.class.getName())
                      .hasMessageContaining("not found"));

  // Invalid Nearby Meter class
  @RegisterExtension
  static final QuarkusUnitTest config2 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.environment-mode", "FULL_ASSERT")
          .overrideConfigKey("quarkus.greycos.solver.daemon", "true")
          .overrideConfigKey(
              "quarkus.greycos.solver.nearby-distance-meter-class",
              TestdataQuarkusSolution.class.getName())
          .overrideConfigKey("quarkus.greycos.solver.move-thread-count", "2")
          .overrideConfigKey("quarkus.greycos.solver.domain-access-type", "REFLECTION")
          .overrideConfigKey("quarkus.greycos.solver.termination.spent-limit", "4h")
          .overrideConfigKey("quarkus.greycos.solver.termination.unimproved-spent-limit", "5h")
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
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
                      .isInstanceOf(IllegalArgumentException.class)
                      .hasMessageContaining("The Nearby Selection Meter class")
                      .hasMessageContaining(TestdataQuarkusSolution.class.getName())
                      .hasMessageContaining(
                          "of the solver config (default) does not implement NearbyDistanceMeter"));

  @Test
  void test() {
    fail("Should not call this method.");
  }
}
