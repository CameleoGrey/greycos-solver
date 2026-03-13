package ai.greycos.solver.quarkus.inheritance.entity;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.childtoo.TestBothAnnotatedConstraintProvider;
import ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.childtoo.TestdataBothAnnotatedChildEntity;
import ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.childtoo.TestdataBothAnnotatedSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorBothAnnotatedTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestBothAnnotatedConstraintProvider.class,
                          TestdataBothAnnotatedSolution.class,
                          TestdataBothAnnotatedChildEntity.class));

  @Inject SolverFactory<TestdataBothAnnotatedSolution> solverFactory;

  /**
   * This test validates the behavior of the solver when both child and parent classes are annotated
   * with {@code @PlanningEntity}.
   */
  @Test
  void testBothClassesAnnotated() {
    var problem = TestdataBothAnnotatedSolution.generateSolution(3, 2, false);
    var solution = solverFactory.buildSolver().solve(problem);
    assertNotNull(solution);
    assertThat(solution.getScore()).isEqualTo(SimpleScore.of(2));
  }
}
