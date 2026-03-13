package ai.greycos.solver.quarkus.inheritance.solution;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childtoo.TestdataBothAnnotatedChildEntity;
import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childtoo.TestdataBothAnnotatedConstraintProvider;
import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childtoo.TestdataBothAnnotatedExtendedConstraintProvider;
import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childtoo.TestdataBothAnnotatedSolution;
import ai.greycos.solver.quarkus.testcotwin.inheritance.solution.TestdataBothAnnotatedNoRawListExtendedSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorBothClassesAnnotatedConfiguredXmlTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
          .overrideConfigKey(
              "quarkus.greycos.solver-config-xml",
              "ai/greycos/solver/quarkus/inheritance/bothClassAnnotatedConfig.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataBothAnnotatedExtendedConstraintProvider.class,
                          TestdataBothAnnotatedConstraintProvider.class,
                          TestdataBothAnnotatedNoRawListExtendedSolution.class,
                          TestdataBothAnnotatedSolution.class,
                          TestdataBothAnnotatedChildEntity.class)
                      .addAsResource(
                          "ai/greycos/solver/quarkus/inheritance/bothClassAnnotatedConfig.xml"));

  @Inject SolverFactory<TestdataBothAnnotatedNoRawListExtendedSolution> solverFactory;

  /**
   * This test validates the behavior of the solver when both child and parent solution classes are
   * annotated with {@code @PlanningSolution}.
   */
  @Test
  void testBothClassesAnnotated() {
    var problem = TestdataBothAnnotatedNoRawListExtendedSolution.generateSolution(3, 2);
    var solution = solverFactory.buildSolver().solve(problem);
    assertNotNull(solution);
    assertThat(solution.getScore()).isEqualTo(SimpleScore.of(12));
  }
}
