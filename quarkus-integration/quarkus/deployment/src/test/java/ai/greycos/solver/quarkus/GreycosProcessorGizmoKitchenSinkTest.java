package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.score.ScoreManager;
import ai.greycos.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.solver.SolutionManager;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.api.solver.SolverJob;
import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.core.impl.solver.DefaultSolutionManager;
import ai.greycos.solver.core.impl.solver.DefaultSolverFactory;
import ai.greycos.solver.core.impl.solver.DefaultSolverManager;
import ai.greycos.solver.quarkus.testdomain.gizmo.DummyConstraintProvider;
import ai.greycos.solver.quarkus.testdomain.gizmo.DummyVariableListener;
import ai.greycos.solver.quarkus.testdomain.gizmo.TestDataKitchenSinkEntity;
import ai.greycos.solver.quarkus.testdomain.gizmo.TestDataKitchenSinkSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorGizmoKitchenSinkTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0hard/0soft")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestDataKitchenSinkEntity.class,
                          TestDataKitchenSinkSolution.class,
                          DummyConstraintProvider.class,
                          DummyVariableListener.class));

  @Inject SolverFactory<TestDataKitchenSinkSolution> solverFactory;
  @Inject SolverManager<TestDataKitchenSinkSolution, Long> solverManager;
  @Inject ScoreManager<TestDataKitchenSinkSolution, SimpleScore> scoreManager;
  @Inject SolutionManager<TestDataKitchenSinkSolution, SimpleScore> solutionManager;

  @Test
  void singletonSolverFactory() {
    assertNotNull(solverFactory);
    assertNotNull(scoreManager);
    // There is only one ScoreDirectorFactory instance
    assertSame(
        ((DefaultSolverFactory<?>) solverFactory).getScoreDirectorFactory(),
        ((DefaultSolutionManager<?, ?>) solutionManager).getScoreDirectorFactory());
    assertNotNull(solverManager);
    // There is only one SolverFactory instance
    assertSame(
        solverFactory,
        ((DefaultSolverManager<TestDataKitchenSinkSolution, Long>) solverManager)
            .getSolverFactory());
  }

  @Test
  void solve() throws ExecutionException, InterruptedException {
    TestDataKitchenSinkSolution problem =
        new TestDataKitchenSinkSolution(
            new TestDataKitchenSinkEntity(),
            Collections.emptyList(),
            "Test",
            Collections.emptyList(),
            HardSoftLongScore.ZERO);

    SolverJob<TestDataKitchenSinkSolution, Long> solverJob = solverManager.solve(1L, problem);
    TestDataKitchenSinkSolution solution = solverJob.getFinalBestSolution();
    assertNotNull(solution);
    assertEquals(1, solution.getPlanningEntityProperty().testGetIntVariable());
    assertEquals("A", solution.getPlanningEntityProperty().testGetStringVariable());
  }
}
