package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.score.director.AbstractScoreDirector;
import greycos.solver.core.impl.score.director.AbstractScoreDirector.AbstractScoreDirectorBuilder;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.score.director.WorkingSolutionMutationObserver;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EvaluationResourceLifecycleTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void assessmentClosesItsDirectorOnSuccessAndScorerFailure(boolean failScoring) throws Exception {
    var factory = factory();
    var failure = new IllegalStateException("scorer failure");
    var calls = new AtomicInteger();
    var director =
        new EasyScoreDirectorFactory<TestdataSolution, SimpleScore>(
                factory.getSolutionDescriptor(),
                solution -> {
                  calls.incrementAndGet();
                  if (failScoring) throw failure;
                  return SimpleScore.of(-solution.getEntityList().size());
                },
                EnvironmentMode.REPRODUCIBLE)
            .createScoreDirectorBuilder()
            .withExpectShadowVariablesInCorrectState(false)
            .build();
    var closes = new AtomicInteger();
    director.setWorkingSolutionMutationObserver(
        new WorkingSolutionMutationObserver<>() {
          @Override
          public void workingSolutionChanged() {}

          @Override
          public void close() {
            closes.incrementAndGet();
          }
        });
    installDirector(factory, director);
    var manager = SolutionManager.<TestdataSolution, SimpleScore>create(factory);
    var solution = TestdataSolution.generateSolution(2, 3);

    if (failScoring) {
      assertThatThrownBy(() -> manager.assess(solution)).isSameAs(failure);
    } else {
      assertThat(manager.assess(solution).score()).isEqualTo(SimpleScore.of(-3));
    }
    assertThat(calls).hasValue(1);
    assertThat(closes).hasValue(1);
    assertThat(director.getWorkingSolution()).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void buildFailureClosesAllocatedDirectorAndPreservesSuppressedCleanupFailure(boolean failClosing)
      throws Exception {
    var factory = factory();
    var director =
        factory.<SimpleScore>getScoreDirectorFactory().createScoreDirectorBuilder().build();
    director.setWorkingSolution(TestdataSolution.generateSolution(2, 2));
    var closes = new AtomicInteger();
    var cleanupFailure = new IllegalStateException("director cleanup failure");
    director.setWorkingSolutionMutationObserver(
        new WorkingSolutionMutationObserver<>() {
          @Override
          public void workingSolutionChanged() {}

          @Override
          public void close() {
            closes.incrementAndGet();
            if (failClosing) throw cleanupFailure;
          }
        });
    installDirector(factory, director);

    assertThatThrownBy(factory::buildSolver)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("moveThreadCount")
        .satisfies(
            failure ->
                assertThat(failure.getSuppressed())
                    .containsExactly(
                        failClosing ? new Throwable[] {cleanupFailure} : new Throwable[0]));
    assertThat(closes).hasValue(1);
    assertThat(director.getWorkingSolution()).isNull();
  }

  private static DefaultSolverFactory<TestdataSolution> factory() {
    return new DefaultSolverFactory<>(
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(TestdataConstraintProvider.class)
            .withEnvironmentMode(EnvironmentMode.REPRODUCIBLE)
            .withMoveThreadCount("-1"));
  }

  // Keep test-only allocation interception out of the production factory API.
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void installDirector(
      DefaultSolverFactory<TestdataSolution> factory, AbstractScoreDirector director)
      throws Exception {
    var scoreDirectorFactory = mock(ScoreDirectorFactory.class);
    var builder = mock(AbstractScoreDirectorBuilder.class, RETURNS_SELF);
    when(builder.build()).thenReturn(director);
    when(scoreDirectorFactory.createScoreDirectorBuilder()).thenReturn(builder);
    when(scoreDirectorFactory.createScoreDirectorBuilder(any(EnvironmentMode.class)))
        .thenReturn(builder);
    var field = DefaultSolverFactory.class.getDeclaredField("scoreDirectorFactory");
    field.setAccessible(true);
    field.set(factory, scoreDirectorFactory);
  }
}
