package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.solver.SolutionManager;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.core.impl.solver.DefaultSolutionManager;
import ai.greycos.solver.core.impl.solver.DefaultSolverFactory;
import ai.greycos.solver.core.impl.solver.DefaultSolverManager;
import ai.greycos.solver.quarkus.testdomain.gizmo.DummyConstraintProvider;
import ai.greycos.solver.quarkus.testdomain.gizmo.DummyVariableListener;
import ai.greycos.solver.quarkus.testdomain.gizmo.TestDataKitchenSinkAutoDiscoverMethodSolution;
import ai.greycos.solver.quarkus.testdomain.gizmo.TestDataKitchenSinkEntity;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorGizmoKitchenSinkAutoDiscoverMethodTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0hard/0soft")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestDataKitchenSinkAutoDiscoverMethodSolution.class,
                          TestDataKitchenSinkEntity.class,
                          DummyConstraintProvider.class,
                          DummyVariableListener.class))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(UnsupportedOperationException.class)
                      .hasMessageContaining("autoDiscoverMemberType"));

  @Inject SolverFactory<TestDataKitchenSinkAutoDiscoverMethodSolution> solverFactory;
  @Inject SolverManager<TestDataKitchenSinkAutoDiscoverMethodSolution, Long> solverManager;

  @Inject
  SolutionManager<TestDataKitchenSinkAutoDiscoverMethodSolution, SimpleScore> solutionManager;

  @Test
  void singletonSolverFactory() {
    assertNotNull(solverFactory);
    // There is only one ScoreDirectorFactory instance
    assertSame(
        ((DefaultSolverFactory<?>) solverFactory).getScoreDirectorFactory(),
        ((DefaultSolutionManager<?, ?>) solutionManager).getScoreDirectorFactory());
    assertNotNull(solverManager);
    // There is only one SolverFactory instance
    assertSame(
        solverFactory,
        ((DefaultSolverManager<TestDataKitchenSinkAutoDiscoverMethodSolution, Long>) solverManager)
            .getSolverFactory());
  }

  @Test
  void solve() { // The method exists only so that the class is considered a test.
    throw new IllegalStateException("The test is expected to fail before it even gets here.");
  }
}
