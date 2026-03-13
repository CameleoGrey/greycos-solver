package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.solver.SolutionManager;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.api.solver.SolverJob;
import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.core.impl.solver.DefaultSolutionManager;
import ai.greycos.solver.core.impl.solver.DefaultSolverFactory;
import ai.greycos.solver.core.impl.solver.DefaultSolverManager;
import ai.greycos.solver.quarkus.testcotwin.shadowvariable.TestdataQuarkusShadowVariableConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.shadowvariable.TestdataQuarkusShadowVariableEntity;
import ai.greycos.solver.quarkus.testcotwin.shadowvariable.TestdataQuarkusShadowVariableListener;
import ai.greycos.solver.quarkus.testcotwin.shadowvariable.TestdataQuarkusShadowVariableSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorShadowVariableSolveTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusShadowVariableEntity.class,
                          TestdataQuarkusShadowVariableSolution.class,
                          TestdataQuarkusShadowVariableConstraintProvider.class,
                          TestdataQuarkusShadowVariableListener.class));

  @Inject SolverFactory<TestdataQuarkusShadowVariableSolution> solverFactory;
  @Inject SolverManager<TestdataQuarkusShadowVariableSolution> solverManager;
  @Inject SolutionManager<TestdataQuarkusShadowVariableSolution, SimpleScore> solutionManager;

  @Test
  void singletonSolverFactory() {
    assertNotNull(solverFactory);
    assertSame(
        ((DefaultSolverFactory<TestdataQuarkusShadowVariableSolution>) solverFactory)
            .getScoreDirectorFactory(),
        ((DefaultSolutionManager<TestdataQuarkusShadowVariableSolution, SimpleScore>)
                solutionManager)
            .getScoreDirectorFactory());
    assertNotNull(solverManager);
    // There is only one SolverFactory instance
    assertSame(
        solverFactory,
        ((DefaultSolverManager<TestdataQuarkusShadowVariableSolution>) solverManager)
            .getSolverFactory());
    assertNotNull(solutionManager);
  }

  @Test
  void solve() throws ExecutionException, InterruptedException {
    TestdataQuarkusShadowVariableSolution problem = new TestdataQuarkusShadowVariableSolution();
    problem.setValueList(IntStream.range(1, 3).mapToObj(i -> "v" + i).collect(Collectors.toList()));
    problem.setEntityList(
        IntStream.range(1, 3)
            .mapToObj(i -> new TestdataQuarkusShadowVariableEntity())
            .collect(Collectors.toList()));
    SolverJob<TestdataQuarkusShadowVariableSolution> solverJob = solverManager.solve(1L, problem);
    TestdataQuarkusShadowVariableSolution solution = solverJob.getFinalBestSolution();
    assertNotNull(solution);
    assertTrue(solution.getScore().score() >= 0);
  }
}
