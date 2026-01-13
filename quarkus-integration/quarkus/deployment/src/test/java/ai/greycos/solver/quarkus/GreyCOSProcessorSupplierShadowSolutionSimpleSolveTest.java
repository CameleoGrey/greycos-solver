package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.List;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.quarkus.testcotwin.declarative.simple.TestdataQuarkusSupplierVariableSimpleConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.declarative.simple.TestdataQuarkusSupplierVariableSimpleEntity;
import ai.greycos.solver.quarkus.testcotwin.declarative.simple.TestdataQuarkusSupplierVariableSimpleSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorSupplierShadowSolutionSimpleSolveTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusSupplierVariableSimpleSolution.class,
                          TestdataQuarkusSupplierVariableSimpleEntity.class,
                          TestdataQuarkusSupplierVariableSimpleConstraintProvider.class));

  @Inject SolverManager<TestdataQuarkusSupplierVariableSimpleSolution, Long> solverManager;

  @Test
  void solve() throws ExecutionException, InterruptedException {
    var shadowEntity = new TestdataQuarkusSupplierVariableSimpleEntity();
    var problem = new TestdataQuarkusSupplierVariableSimpleSolution();
    problem.setEntityList(List.of(shadowEntity));
    problem.setValueList(List.of("a", "b"));
    var solverJob = solverManager.solve(1L, problem);
    var solution = solverJob.getFinalBestSolution();
    assertNotNull(solution);
    assertNotSame(solution, problem);
    assertEquals(0, solution.getScore().score());
    assertNotSame(solution.getEntityList().get(0), problem.getEntityList().get(0));
  }
}
