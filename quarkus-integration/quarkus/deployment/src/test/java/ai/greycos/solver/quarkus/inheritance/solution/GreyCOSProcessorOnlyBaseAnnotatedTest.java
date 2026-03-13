package ai.greycos.solver.quarkus.inheritance.solution;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot.TestdataOnlyBaseAnnotatedBaseEntity;
import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot.TestdataOnlyBaseAnnotatedChildEntity;
import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot.TestdataOnlyBaseAnnotatedConstraintProvider;
import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot.TestdataOnlyBaseAnnotatedExtendedSolution;
import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot.TestdataOnlyBaseAnnotatedSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorOnlyBaseAnnotatedTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataOnlyBaseAnnotatedConstraintProvider.class,
                          TestdataOnlyBaseAnnotatedExtendedSolution.class,
                          TestdataOnlyBaseAnnotatedSolution.class,
                          TestdataOnlyBaseAnnotatedChildEntity.class,
                          TestdataOnlyBaseAnnotatedBaseEntity.class));

  @Inject SolverFactory<TestdataOnlyBaseAnnotatedExtendedSolution> solverFactory;

  /**
   * This test validates the behavior of the solver when only base class is annotated with
   * {@code @PlanningSolution}.
   */
  @Test
  void testOnlyBaseClassAnnotated() {
    var problem = TestdataOnlyBaseAnnotatedExtendedSolution.generateSolution(3, 2);
    var solution = solverFactory.buildSolver().solve(problem);
    assertNotNull(solution);
    assertThat(solution.getScore()).isEqualTo(SimpleScore.of(2));
  }
}
