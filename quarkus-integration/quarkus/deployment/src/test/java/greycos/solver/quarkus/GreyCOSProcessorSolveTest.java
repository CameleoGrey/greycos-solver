package greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.SolverJob;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.impl.solver.DefaultSolutionManager;
import greycos.solver.core.impl.solver.DefaultSolverFactory;
import greycos.solver.core.impl.solver.DefaultSolverManager;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusConstraintProvider;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorSolveTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class));

  @Inject SolverFactory<TestdataQuarkusSolution> solverFactory;
  @Inject SolverManager<TestdataQuarkusSolution> solverManager;
  @Inject SolutionManager<TestdataQuarkusSolution, SimpleScore> solutionManager;

  @Test
  void singletonSolverFactory() {
    assertNotNull(solverFactory);
    assertSame(
        ((DefaultSolverFactory<TestdataQuarkusSolution>) solverFactory).getScoreDirectorFactory(),
        ((DefaultSolutionManager<TestdataQuarkusSolution, SimpleScore>) solutionManager)
            .getScoreDirectorFactory());
    assertNotNull(solverManager);
    // There is only one SolverFactory instance
    assertSame(
        solverFactory,
        ((DefaultSolverManager<TestdataQuarkusSolution>) solverManager).getSolverFactory());
    assertNotNull(solutionManager);
  }

  @Test
  void solve() throws ExecutionException, InterruptedException {
    TestdataQuarkusSolution problem = new TestdataQuarkusSolution();
    problem.setValueList(IntStream.range(1, 3).mapToObj(i -> "v" + i).collect(Collectors.toList()));
    problem.setEntityList(
        IntStream.range(1, 3)
            .mapToObj(i -> new TestdataQuarkusEntity())
            .collect(Collectors.toList()));
    SolverJob<TestdataQuarkusSolution> solverJob = solverManager.solve(1L, problem);
    TestdataQuarkusSolution solution = solverJob.getFinalBestSolution();
    assertNotNull(solution);
    assertTrue(solution.getScore().score() >= 0);
  }
}
