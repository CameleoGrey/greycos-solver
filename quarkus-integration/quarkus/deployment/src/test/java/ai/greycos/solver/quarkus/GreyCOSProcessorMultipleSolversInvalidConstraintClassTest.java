package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.quarkus.testcotwin.dummy.DummyTestdataQuarkusEasyScoreCalculator;
import ai.greycos.solver.quarkus.testcotwin.dummy.DummyTestdataQuarkusIncrementalScoreCalculator;
import ai.greycos.solver.quarkus.testcotwin.dummy.DummyTestdataQuarkusShadowVariableEasyScoreCalculator;
import ai.greycos.solver.quarkus.testcotwin.dummy.DummyTestdataQuarkusShadowVariableIncrementalScoreCalculator;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;
import ai.greycos.solver.quarkus.testcotwin.shadowvariable.TestdataQuarkusShadowVariableConstraintProvider;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorMultipleSolversInvalidConstraintClassTest {

  // Empty classes
  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.\"solver1\".environment-mode", "FULL_ASSERT")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".environment-mode", "PHASE_ASSERT")
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
          .overrideConfigKey("quarkus.greycos.solver.\"solver1\".environment-mode", "FULL_ASSERT")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".environment-mode", "PHASE_ASSERT")
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
                      .hasMessageContaining("Some solver configs")
                      .hasMessageContaining("solver1")
                      .hasMessageContaining("solver2")
                      .hasMessageContaining(
                          "don't specify a EasyScoreCalculator score class, yet there are multiple available")
                      .hasMessageContaining(
                          "ai.greycos.solver.quarkus.testcotwin.dummy.DummyTestdataQuarkusEasyScoreCalculator")
                      .hasMessageContaining(
                          "ai.greycos.solver.quarkus.testcotwin.dummy.DummyTestdataQuarkusShadowVariableEasyScoreCalculator")
                      .hasMessageContaining("on the classpath."));

  // Multiple classes - EasyScoreCalculator with XML
  @RegisterExtension
  static final QuarkusUnitTest config3 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.\"solver1\".environment-mode", "FULL_ASSERT")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver1\".solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverConfig.xml")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".environment-mode", "PHASE_ASSERT")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver2\".solver-config-xml",
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
                      .hasMessageContaining("Some solver configs")
                      .hasMessageContaining("solver1")
                      .hasMessageContaining("solver2")
                      .hasMessageContaining(
                          "don't specify a EasyScoreCalculator score class, yet there are multiple available")
                      .hasMessageContaining(
                          "ai.greycos.solver.quarkus.testcotwin.dummy.DummyTestdataQuarkusEasyScoreCalculator")
                      .hasMessageContaining(
                          "ai.greycos.solver.quarkus.testcotwin.dummy.DummyTestdataQuarkusShadowVariableEasyScoreCalculator")
                      .hasMessageContaining("on the classpath."));

  // Multiple classes - ConstraintProvider
  @RegisterExtension
  static final QuarkusUnitTest config4 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.\"solver1\".environment-mode", "FULL_ASSERT")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".environment-mode", "PHASE_ASSERT")
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
                      .hasMessageContaining("Some solver configs")
                      .hasMessageContaining("solver1")
                      .hasMessageContaining("solver2")
                      .hasMessageContaining(
                          "don't specify a ConstraintProvider score class, yet there are multiple available")
                      .hasMessageContaining(TestdataQuarkusConstraintProvider.class.getName())
                      .hasMessageContaining(
                          TestdataQuarkusShadowVariableConstraintProvider.class.getName())
                      .hasMessageContaining("on the classpath."));

  // Multiple classes - ConstraintProvider with XML
  @RegisterExtension
  static final QuarkusUnitTest config5 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.\"solver1\".environment-mode", "FULL_ASSERT")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver1\".solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverConfigWithoutScore.xml")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".environment-mode", "PHASE_ASSERT")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver2\".solver-config-xml",
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
                      .hasMessageContaining("Some solver configs")
                      .hasMessageContaining("solver1")
                      .hasMessageContaining("solver2")
                      .hasMessageContaining(
                          "don't specify a ConstraintProvider score class, yet there are multiple available")
                      .hasMessageContaining(TestdataQuarkusConstraintProvider.class.getName())
                      .hasMessageContaining(
                          TestdataQuarkusShadowVariableConstraintProvider.class.getName())
                      .hasMessageContaining("on the classpath."));

  // Multiple classes - IncrementalScoreCalculator
  @RegisterExtension
  static final QuarkusUnitTest config6 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.\"solver1\".environment-mode", "FULL_ASSERT")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".environment-mode", "PHASE_ASSERT")
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
                      .hasMessageContaining("Some solver configs")
                      .hasMessageContaining("solver1")
                      .hasMessageContaining("solver2")
                      .hasMessageContaining(
                          "don't specify a IncrementalScoreCalculator score class, yet there are multiple available")
                      .hasMessageContaining(
                          DummyTestdataQuarkusIncrementalScoreCalculator.class.getName())
                      .hasMessageContaining(
                          DummyTestdataQuarkusShadowVariableIncrementalScoreCalculator.class
                              .getName())
                      .hasMessageContaining("on the classpath."));

  // Multiple classes - IncrementalScoreCalculator with XML
  @RegisterExtension
  static final QuarkusUnitTest config7 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.\"solver1\".environment-mode", "FULL_ASSERT")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver1\".solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverConfigWithoutScore.xml")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".environment-mode", "PHASE_ASSERT")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver2\".solver-config-xml",
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
                      .hasMessageContaining("Some solver configs")
                      .hasMessageContaining("solver1")
                      .hasMessageContaining("solver2")
                      .hasMessageContaining(
                          "don't specify a IncrementalScoreCalculator score class, yet there are multiple available")
                      .hasMessageContaining(
                          DummyTestdataQuarkusIncrementalScoreCalculator.class.getName())
                      .hasMessageContaining(
                          DummyTestdataQuarkusShadowVariableIncrementalScoreCalculator.class
                              .getName())
                      .hasMessageContaining("on the classpath."));

  // Unused classes - EasyScoreCalculator
  @RegisterExtension
  static final QuarkusUnitTest config8 =
      new QuarkusUnitTest()
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver1\".solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverWithEasyScore.xml")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver2\".solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverWithEasyScore.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          DummyTestdataQuarkusEasyScoreCalculator.class)
                      .addClasses(DummyTestdataQuarkusShadowVariableEasyScoreCalculator.class)
                      .addAsResource("ai/greycos/solver/quarkus/customSolverWithEasyScore.xml"))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining(
                          "Unused classes ([%s]) that implements EasyScoreCalculator were found."
                              .formatted(
                                  DummyTestdataQuarkusShadowVariableEasyScoreCalculator.class
                                      .getName())));

  // Unused classes - ConstraintProvider
  @RegisterExtension
  static final QuarkusUnitTest config9 =
      new QuarkusUnitTest()
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver1\".solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverQuarkusConfig.xml")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver2\".solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverQuarkusConfig.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class)
                      .addClasses(TestdataQuarkusShadowVariableConstraintProvider.class)
                      .addAsResource("ai/greycos/solver/quarkus/customSolverQuarkusConfig.xml"))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining(
                          "Unused classes ([%s]) that implements ConstraintProvider were found."
                              .formatted(
                                  TestdataQuarkusShadowVariableConstraintProvider.class
                                      .getName())));

  // Unused classes - IncrementalScoreCalculator
  @RegisterExtension
  static final QuarkusUnitTest config10 =
      new QuarkusUnitTest()
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver1\".solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverWithIncrementalScore.xml")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver2\".solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverWithIncrementalScore.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          DummyTestdataQuarkusIncrementalScoreCalculator.class)
                      .addClasses(
                          DummyTestdataQuarkusShadowVariableIncrementalScoreCalculator.class)
                      .addAsResource(
                          "ai/greycos/solver/quarkus/customSolverWithIncrementalScore.xml"))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining(
                          "Unused classes ([%s]) that implements IncrementalScoreCalculator were found."
                              .formatted(
                                  DummyTestdataQuarkusShadowVariableIncrementalScoreCalculator.class
                                      .getName())));

  @Inject
  @Named("solver1")
  SolverManager<?> solverManager1;

  @Inject
  @Named("solver2")
  SolverManager<?> solverManager2;

  @Test
  void test() {
    fail("Should not call this method.");
  }
}
