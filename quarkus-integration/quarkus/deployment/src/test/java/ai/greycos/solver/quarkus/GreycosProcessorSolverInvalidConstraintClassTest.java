package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import ai.greycos.solver.quarkus.testdomain.dummy.DummyTestdataQuarkusEasyScoreCalculator;
import ai.greycos.solver.quarkus.testdomain.dummy.DummyTestdataQuarkusIncrementalScoreCalculator;
import ai.greycos.solver.quarkus.testdomain.dummy.DummyTestdataQuarkusShadowVariableEasyScoreCalculator;
import ai.greycos.solver.quarkus.testdomain.dummy.DummyTestdataQuarkusShadowVariableIncrementalScoreCalculator;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusSolution;
import ai.greycos.solver.quarkus.testdomain.shadowvariable.TestdataQuarkusShadowVariableConstraintProvider;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreycosProcessorSolverInvalidConstraintClassTest {

  // Empty classes
  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.environment-mode", "FULL_ASSERT")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(TestdataQuarkusEntity.class, TestdataQuarkusSolution.class))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining(
                          "No classes found that implement EasyScoreCalculator, ConstraintProvider, or IncrementalScoreCalculator."));

  // Multiple classes - EasyScoreCalculator
  @RegisterExtension
  static final QuarkusUnitTest config2 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.environment-mode", "FULL_ASSERT")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          DummyTestdataQuarkusEasyScoreCalculator.class)
                      .addClasses(DummyTestdataQuarkusShadowVariableEasyScoreCalculator.class))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining("Multiple score classes")
                      .hasMessageContaining(DummyTestdataQuarkusEasyScoreCalculator.class.getName())
                      .hasMessageContaining(
                          DummyTestdataQuarkusShadowVariableEasyScoreCalculator.class.getName())
                      .hasMessageContaining("that implements EasyScoreCalculator were found"));

  // Multiple classes - EasyScoreCalculator with XML
  @RegisterExtension
  static final QuarkusUnitTest config3 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.environment-mode", "FULL_ASSERT")
          .overrideConfigKey(
              "quarkus.greycos.solver.solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverConfig.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(TestdataQuarkusEntity.class, TestdataQuarkusSolution.class)
                      .addClasses(
                          DummyTestdataQuarkusEasyScoreCalculator.class,
                          DummyTestdataQuarkusShadowVariableEasyScoreCalculator.class)
                      .addAsResource("ai/greycos/solver/quarkus/customSolverConfig.xml"))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining("Multiple score classes")
                      .hasMessageContaining(DummyTestdataQuarkusEasyScoreCalculator.class.getName())
                      .hasMessageContaining(
                          DummyTestdataQuarkusShadowVariableEasyScoreCalculator.class.getName())
                      .hasMessageContaining("that implements EasyScoreCalculator were found"));

  // Multiple classes - ConstraintProvider
  @RegisterExtension
  static final QuarkusUnitTest config4 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.environment-mode", "FULL_ASSERT")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class)
                      .addClasses(TestdataQuarkusShadowVariableConstraintProvider.class))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining("Multiple score classes")
                      .hasMessageContaining(TestdataQuarkusConstraintProvider.class.getName())
                      .hasMessageContaining(
                          TestdataQuarkusShadowVariableConstraintProvider.class.getName())
                      .hasMessageContaining("that implements ConstraintProvider were found"));

  // Multiple classes - ConstraintProvider with XML
  @RegisterExtension
  static final QuarkusUnitTest config5 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.environment-mode", "FULL_ASSERT")
          .overrideConfigKey(
              "quarkus.greycos.solver.solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverConfigWithoutScore.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(TestdataQuarkusEntity.class, TestdataQuarkusSolution.class)
                      .addClasses(
                          TestdataQuarkusConstraintProvider.class,
                          TestdataQuarkusShadowVariableConstraintProvider.class)
                      .addAsResource(
                          "ai/greycos/solver/quarkus/customSolverConfigWithoutScore.xml"))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining("Multiple score classes")
                      .hasMessageContaining(TestdataQuarkusConstraintProvider.class.getName())
                      .hasMessageContaining(
                          TestdataQuarkusShadowVariableConstraintProvider.class.getName())
                      .hasMessageContaining("that implements ConstraintProvider were found"));

  // Multiple classes - IncrementalScoreCalculator
  @RegisterExtension
  static final QuarkusUnitTest config6 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.environment-mode", "FULL_ASSERT")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          DummyTestdataQuarkusIncrementalScoreCalculator.class)
                      .addClasses(
                          DummyTestdataQuarkusShadowVariableIncrementalScoreCalculator.class))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining("Multiple score classes")
                      .hasMessageContaining(
                          DummyTestdataQuarkusIncrementalScoreCalculator.class.getName())
                      .hasMessageContaining(
                          DummyTestdataQuarkusShadowVariableIncrementalScoreCalculator.class
                              .getName())
                      .hasMessageContaining(
                          "that implements IncrementalScoreCalculator were found"));

  // Multiple classes - IncrementalScoreCalculator with XML
  @RegisterExtension
  static final QuarkusUnitTest config7 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.environment-mode", "FULL_ASSERT")
          .overrideConfigKey(
              "quarkus.greycos.solver.solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverConfigWithoutScore.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class)
                      .addClasses(
                          DummyTestdataQuarkusIncrementalScoreCalculator.class,
                          DummyTestdataQuarkusShadowVariableIncrementalScoreCalculator.class)
                      .addAsResource(
                          "ai/greycos/solver/quarkus/customSolverConfigWithoutScore.xml"))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining("Multiple score classes")
                      .hasMessageContaining(
                          DummyTestdataQuarkusIncrementalScoreCalculator.class.getName())
                      .hasMessageContaining(
                          DummyTestdataQuarkusShadowVariableIncrementalScoreCalculator.class
                              .getName())
                      .hasMessageContaining(
                          "that implements IncrementalScoreCalculator were found"));

  @Test
  void test() {
    fail("Should not call this method.");
  }
}
