package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.score.ScoreManager;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.solver.SolutionManager;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.core.impl.solver.DefaultSolutionManager;
import ai.greycos.solver.core.impl.solver.DefaultSolverFactory;
import ai.greycos.solver.core.impl.solver.DefaultSolverManager;
import ai.greycos.solver.quarkus.testcotwin.inheritance.solution.TestdataExtendedQuarkusSolution;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorExtendedSolutionSolveTest {

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
                          TestdataExtendedQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class));

  @Inject SolverFactory<TestdataQuarkusSolution> solverFactory;
  @Inject SolverManager<TestdataQuarkusSolution, Long> solverManager;
  @Inject ScoreManager<TestdataQuarkusSolution, SimpleScore> scoreManager;
  @Inject SolutionManager<TestdataQuarkusSolution, SimpleScore> solutionManager;

  @Test
  void singletonSolverFactory() {
    assertNotNull(solverFactory);
    // There is only one ScoreDirectorFactory instance
    assertSame(
        ((DefaultSolverFactory<TestdataQuarkusSolution>) solverFactory).getScoreDirectorFactory(),
        ((DefaultSolutionManager<TestdataQuarkusSolution, SimpleScore>) solutionManager)
            .getScoreDirectorFactory());
    assertNotNull(solverManager);
    // There is only one SolverFactory instance
    assertSame(
        solverFactory,
        ((DefaultSolverManager<TestdataQuarkusSolution, Long>) solverManager).getSolverFactory());
    assertNotNull(scoreManager);
  }

  @Test
  void solve() throws ExecutionException, InterruptedException {
    var problem = new TestdataExtendedQuarkusSolution("Extra Data");
    problem.setValueList(IntStream.range(1, 3).mapToObj(i -> "v" + i).toList());
    problem.setEntityList(
        IntStream.range(1, 3).mapToObj(i -> new TestdataQuarkusEntity()).toList());
    var solverJob = solverManager.solve(1L, problem);
    var solution = (TestdataExtendedQuarkusSolution) solverJob.getFinalBestSolution();
    assertNotNull(solution);
    assertTrue(solution.getScore().score() >= 0);
    assertEquals("Extra Data", solution.getExtraData());
  }
}
