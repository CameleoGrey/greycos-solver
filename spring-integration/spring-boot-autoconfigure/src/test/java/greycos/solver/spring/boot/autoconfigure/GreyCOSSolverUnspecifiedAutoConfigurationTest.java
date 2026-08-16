package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThatCode;

import greycos.solver.spring.boot.autoconfigure.dummy.MultipleConstraintProviderSpringTestConfiguration;
import greycos.solver.spring.boot.autoconfigure.dummy.MultipleEasyScoreConstraintSpringTestConfiguration;
import greycos.solver.spring.boot.autoconfigure.dummy.MultipleIncrementalScoreConstraintSpringTestConfiguration;
import greycos.solver.spring.boot.autoconfigure.dummy.MultipleSolutionsSpringTestConfiguration;
import greycos.solver.spring.boot.autoconfigure.dummy.NoEntitySpringTestConfiguration;
import greycos.solver.spring.boot.autoconfigure.dummy.NoSolutionSpringTestConfiguration;
import greycos.solver.spring.boot.autoconfigure.dummy.gizmo.constraints.easy.DummyGizmoEasyScore;
import greycos.solver.spring.boot.autoconfigure.dummy.gizmo.constraints.incremental.DummyGizmoIncrementalScore;
import greycos.solver.spring.boot.autoconfigure.dummy.normal.constraints.easy.DummySpringEasyScore;
import greycos.solver.spring.boot.autoconfigure.dummy.normal.constraints.incremental.DummySpringIncrementalScore;
import greycos.solver.spring.boot.autoconfigure.gizmo.constraints.TestdataGizmoConstraintProvider;
import greycos.solver.spring.boot.autoconfigure.gizmo.cotwin.TestdataGizmoSpringSolution;
import greycos.solver.spring.boot.autoconfigure.invalid.solution.InvalidSolutionSpringTestConfiguration;
import greycos.solver.spring.boot.autoconfigure.normal.NoConstraintsSpringTestConfiguration;
import greycos.solver.spring.boot.autoconfigure.normal.constraints.TestdataSpringConstraintProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.TestExecutionListeners;

@TestExecutionListeners
@Execution(ExecutionMode.CONCURRENT)
class GreyCOSSolverUnspecifiedAutoConfigurationTest {

  private final ApplicationContextRunner noUserConfigurationContextRunner;

  public GreyCOSSolverUnspecifiedAutoConfigurationTest() {
    noUserConfigurationContextRunner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    GreyCOSSolverAutoConfiguration.class, GreyCOSSolverBeanFactory.class));
  }

  @Test
  void noSolutionClass() {
    assertThatCode(
            () ->
                noUserConfigurationContextRunner
                    .withUserConfiguration(NoSolutionSpringTestConfiguration.class)
                    .withPropertyValues("greycos.solver.termination.best-score-limit=0")
                    .run(context -> context.getBean("solver1")))
        .cause()
        .message()
        .contains("No classes were found with a @PlanningSolution annotation.");
  }

  @Test
  void multipleSolutionClasses() {
    assertThatCode(
            () ->
                noUserConfigurationContextRunner
                    .withUserConfiguration(MultipleSolutionsSpringTestConfiguration.class)
                    .withPropertyValues("greycos.solver.termination.best-score-limit=0")
                    .run(context -> context.getBean("solver1")))
        .cause()
        .message()
        .contains(
            "Multiple classes",
            TestdataGizmoSpringSolution.class.getSimpleName(),
            "TestdataSpringSolution",
            "found in the classpath with a @PlanningSolution annotation.");
  }

  @Test
  void noEntityClass() {
    assertThatCode(
            () ->
                noUserConfigurationContextRunner
                    .withUserConfiguration(NoEntitySpringTestConfiguration.class)
                    .withPropertyValues("greycos.solver.termination.best-score-limit=0")
                    .run(context -> context.getBean("solver1")))
        .cause()
        .message()
        .contains("No classes were found with a @PlanningEntity annotation.");
  }

  @Test
  void noConstraintClass() {
    assertThatCode(
            () ->
                noUserConfigurationContextRunner
                    .withUserConfiguration(NoConstraintsSpringTestConfiguration.class)
                    .withPropertyValues("greycos.solver.termination.best-score-limit=0")
                    .run(context -> context.getBean("solver1")))
        .cause()
        .message()
        .contains(
            "No classes found that implement EasyScoreCalculator, ConstraintProvider, or IncrementalScoreCalculator.");
  }

  @Test
  void multipleEasyScoreConstraints() {
    assertThatCode(
            () ->
                noUserConfigurationContextRunner
                    .withUserConfiguration(MultipleEasyScoreConstraintSpringTestConfiguration.class)
                    .withPropertyValues("greycos.solver.termination.best-score-limit=0")
                    .run(context -> context.getBean("solver1")))
        .cause()
        .message()
        .contains(
            "Multiple score calculator classes",
            DummyGizmoEasyScore.class.getSimpleName(),
            DummySpringEasyScore.class.getSimpleName(),
            "that implements EasyScoreCalculator were found in the classpath.");
  }

  @Test
  void multipleConstraintProviderConstraints() {
    assertThatCode(
            () ->
                noUserConfigurationContextRunner
                    .withUserConfiguration(MultipleConstraintProviderSpringTestConfiguration.class)
                    .withPropertyValues("greycos.solver.termination.best-score-limit=0")
                    .run(context -> context.getBean("solver1")))
        .cause()
        .message()
        .contains(
            "Multiple score calculator classes",
            TestdataGizmoConstraintProvider.class.getSimpleName(),
            TestdataSpringConstraintProvider.class.getSimpleName(),
            "that implements ConstraintProvider were found in the classpath.");
  }

  @Test
  void multipleIncrementalScoreConstraints() {
    assertThatCode(
            () ->
                noUserConfigurationContextRunner
                    .withUserConfiguration(
                        MultipleIncrementalScoreConstraintSpringTestConfiguration.class)
                    .withPropertyValues("greycos.solver.termination.best-score-limit=0")
                    .run(context -> context.getBean("solver1")))
        .cause()
        .message()
        .contains(
            "Multiple score calculator classes",
            DummyGizmoIncrementalScore.class.getSimpleName(),
            DummySpringIncrementalScore.class.getSimpleName(),
            "that implements IncrementalScoreCalculator were found in the classpath.");
  }

  @Test
  void multipleEasyScoreConstraintsXml_property() {
    assertThatCode(
            () ->
                noUserConfigurationContextRunner
                    .withUserConfiguration(MultipleEasyScoreConstraintSpringTestConfiguration.class)
                    .withPropertyValues("greycos.solver.solver.solver-config-xml=solverConfig.xml")
                    .run(context -> context.getBean("solver1")))
        .cause()
        .message()
        .contains(
            "Multiple score calculator classes",
            DummyGizmoEasyScore.class.getSimpleName(),
            DummySpringEasyScore.class.getSimpleName(),
            "that implements EasyScoreCalculator were found in the classpath");
  }

  @Test
  void multipleConstraintProviderConstraintsXml_property() {
    assertThatCode(
            () ->
                noUserConfigurationContextRunner
                    .withUserConfiguration(MultipleConstraintProviderSpringTestConfiguration.class)
                    .withPropertyValues(
                        "greycos.solver.solver-config-xml=greycos/solver/spring/boot/autoconfigure/normalSolverConfig.xml")
                    .run(context -> context.getBean("solver1")))
        .cause()
        .message()
        .contains(
            "Multiple score calculator classes",
            TestdataGizmoConstraintProvider.class.getSimpleName(),
            TestdataSpringConstraintProvider.class.getSimpleName(),
            "that implements ConstraintProvider were found in the classpath.");
  }

  @Test
  void multipleIncrementalScoreConstraintsXml_property() {
    assertThatCode(
            () ->
                noUserConfigurationContextRunner
                    .withUserConfiguration(
                        MultipleIncrementalScoreConstraintSpringTestConfiguration.class)
                    .withPropertyValues(
                        "greycos.solver.solver-config-xml=greycos/solver/spring/boot/autoconfigure/normalSolverConfig.xml")
                    .run(context -> context.getBean("solver1")))
        .cause()
        .message()
        .contains(
            "Multiple score calculator classes",
            DummyGizmoIncrementalScore.class.getSimpleName(),
            DummySpringIncrementalScore.class.getSimpleName(),
            "that implements IncrementalScoreCalculator were found in the classpath.");
  }

  @Test
  void invalidSolution() {
    assertThatCode(
            () ->
                noUserConfigurationContextRunner
                    .withUserConfiguration(InvalidSolutionSpringTestConfiguration.class)
                    .withPropertyValues(
                        "greycos.solver.solver-config-xml=greycos/solver/spring/boot/autoconfigure/invalidSolverConfig.xml")
                    .run(context -> context.getBean("solver1")))
        .cause()
        .message()
        .contains("cannot be a record as it needs to be mutable.");
  }
}
