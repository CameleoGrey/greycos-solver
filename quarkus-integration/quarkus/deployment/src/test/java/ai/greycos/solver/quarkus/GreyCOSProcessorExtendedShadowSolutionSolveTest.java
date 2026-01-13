package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.shadow.TestdataExtendedShadowEntity;
import ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.shadow.TestdataExtendedShadowExtendedShadowEntity;
import ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.shadow.TestdataExtendedShadowShadowEntity;
import ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.shadow.TestdataExtendedShadowSolution;
import ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.shadow.TestdataExtendedShadowVariable;
import ai.greycos.solver.quarkus.testcotwin.inheritance.solution.TestdataExtendedShadowSolutionConstraintProvider;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorExtendedShadowSolutionSolveTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataExtendedShadowSolution.class,
                          TestdataExtendedShadowEntity.class,
                          TestdataExtendedShadowShadowEntity.class,
                          TestdataExtendedShadowExtendedShadowEntity.class,
                          TestdataExtendedShadowVariable.class,
                          TestdataEntity.class,
                          TestdataObject.class,
                          TestdataValue.class,
                          TestdataExtendedShadowSolutionConstraintProvider.class));

  @Inject SolverManager<TestdataExtendedShadowSolution, Long> solverManager;

  @Test
  void solve() throws ExecutionException, InterruptedException {
    var shadowEntity = new TestdataExtendedShadowExtendedShadowEntity();
    var problem = new TestdataExtendedShadowSolution(shadowEntity);
    var solverJob = solverManager.solve(1L, problem);
    var solution = solverJob.getFinalBestSolution();
    assertNotNull(solution);
    assertNotSame(solution, problem);
    assertEquals(0, solution.score.score());
    assertNotSame(solution.shadowEntityList.get(0), problem.shadowEntityList.get(0));
  }
}
