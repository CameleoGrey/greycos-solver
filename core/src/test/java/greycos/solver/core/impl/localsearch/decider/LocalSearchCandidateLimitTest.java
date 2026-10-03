package greycos.solver.core.impl.localsearch.decider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.move.PreparableMove;
import greycos.solver.core.impl.move.PreparedMoveEvaluation;
import greycos.solver.core.impl.neighborhood.MoveSelectorBasedMoveRepository;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalSearchCandidateLimitTest {

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2"})
  @Timeout(15)
  void tabuWithNoEligibleEntityStillCompletesSteps(String workers) {
    var steps = solve(workers, false);
    assertThat(steps).hasSize(3);
    assertThat(steps.getFirst().getAcceptedMoveCount()).isEqualTo(1000);
    assertThat(steps.subList(1, 3))
        .allSatisfy(
            step -> {
              assertThat(step.getAcceptedMoveCount()).isZero();
              assertThat(step.getSelectedMoveCount()).isBetween(1L, 327_670L);
              assertThat(step.getStep()).isNotNull();
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  @Timeout(15)
  void explicitFiniteSelectionLimitRemainsAuthoritative(String workers) {
    assertThat(solve(workers, true))
        .hasSize(3)
        .allSatisfy(step -> assertThat(step.getSelectedMoveCount()).isEqualTo(400));
  }

  private static List<LocalSearchStepScope<TestdataListSolution>> solve(
      String workers, boolean finite) {
    var phase =
        new LocalSearchPhaseConfig()
            .withLocalSearchType(LocalSearchType.TABU_SEARCH)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(3));
    if (finite) {
      phase.withMoveSelectorConfig(new ListChangeMoveSelectorConfig().withSelectedCountLimit(400L));
    }
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataListSolution.class)
            .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
            .withEasyScoreCalculatorClass(ConstantScore.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(workers)
            .withPhases(phase);
    var solver =
        (DefaultSolver<TestdataListSolution>)
            SolverFactory.<TestdataListSolution>create(config).buildSolver();
    var steps = new ArrayList<LocalSearchStepScope<TestdataListSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataListSolution> step) {
            steps.add((LocalSearchStepScope<TestdataListSolution>) step);
          }
        });
    var solution = solver.solve(TestdataListSolution.generateInitializedSolution(5, 1));
    assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
    return steps;
  }

  @Test
  @SuppressWarnings("unchecked")
  void incompletePreparationsCountTowardTheBound() {
    InnerScoreDirector<Object, SimpleScore> director = mock(InnerScoreDirector.class);
    PreparableMove<Object> move = mock(PreparableMove.class);
    when(move.<SimpleScore>prepare(any(), any(), anyBoolean(), isNull()))
        .thenReturn(
            new PreparedMoveEvaluation<>(PreparedMoveEvaluation.Status.EMPTY, null, null, 0));
    MoveSelectorBasedMoveRepository<Object> repository =
        mock(MoveSelectorBasedMoveRepository.class);
    when(repository.isNeverEnding()).thenReturn(true);
    when(repository.getSizeEstimate()).thenReturn(1L);
    when(repository.iterator()).thenReturn(Collections.<Move<Object>>nCopies(100, move).iterator());
    var solverScope = new SolverScope<Object>();
    solverScope.setScoreDirector(director);
    var phase = new LocalSearchPhaseScope<>(solverScope, 0);
    var step = new LocalSearchStepScope<>(phase, 0);
    Acceptor<Object> acceptor = mock(Acceptor.class);
    LocalSearchForager<Object> forager = mock(LocalSearchForager.class);
    var decider =
        new LocalSearchDecider<>(
            "",
            PhaseTermination.bridge(new BasicPlumbingTermination<>(false)),
            repository,
            acceptor,
            forager);
    decider.stepStarted(step);
    decider.decideNextStep(step);
    verify(move, org.mockito.Mockito.times(10)).prepare(any(), any(), anyBoolean(), isNull());
    verify(acceptor, never()).isAccepted(any());
    verify(forager, never()).addMove(any());
    assertThat(step.getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.NO_ADMISSIBLE_MOVE);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void nonDoableCandidatesCountOnlyWhenConsumed(boolean threaded) throws InterruptedException {
    InnerScoreDirector<Object, SimpleScore> director = mock(InnerScoreDirector.class);
    AbstractSelectorBasedMove<Object> move = mock(AbstractSelectorBasedMove.class);
    MoveSelectorBasedMoveRepository<Object> repository =
        mock(MoveSelectorBasedMoveRepository.class);
    when(repository.isNeverEnding()).thenReturn(true);
    when(repository.getSizeEstimate()).thenReturn(1L);
    when(repository.iterator()).thenReturn(Collections.<Move<Object>>nCopies(100, move).iterator());
    var solverScope = new SolverScope<Object>();
    solverScope.setScoreDirector(director);
    var phase = new LocalSearchPhaseScope<>(solverScope, 0);
    var step = new LocalSearchStepScope<>(phase, 0);
    Acceptor<Object> acceptor = mock(Acceptor.class);
    LocalSearchForager<Object> forager = mock(LocalSearchForager.class);
    var termination = PhaseTermination.bridge(new BasicPlumbingTermination<Object>(false));
    MoveEvaluationPipeline<Object> pipeline = mock(MoveEvaluationPipeline.class);
    var consumed = new AtomicInteger();
    when(pipeline.take())
        .thenAnswer(
            ignored ->
                new MoveEvaluationPipeline.Result<>(0, consumed.getAndIncrement(), move, null));
    LocalSearchDecider<Object> decider =
        threaded
            ? new MultiThreadedLocalSearchDecider<>(
                "", termination, repository, acceptor, forager, Thread::new, 2, 20) {
              {
                moveEvaluationPipeline = pipeline;
              }
            }
            : new LocalSearchDecider<>("", termination, repository, acceptor, forager);
    decider.stepStarted(step);
    decider.decideNextStep(step);
    assertThat(step.getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.NO_ADMISSIBLE_MOVE);
    verify(acceptor, never()).isAccepted(any());
    verify(forager, never()).addMove(any());
    if (threaded) {
      assertThat(consumed).hasValue(10);
      verify(pipeline).cancelStep();
    } else {
      verify(move, org.mockito.Mockito.times(10)).isMoveDoable(director);
    }
  }

  @Test
  void acceptedCandidateResetsConsecutiveRejectionCount() {
    var limit = new LocalSearchCandidateLimit(1);
    for (int i = 0; i < 9; i++) assertThat(limit.record(false)).isFalse();
    assertThat(limit.record(true)).isFalse();
    for (int i = 0; i < 9; i++) assertThat(limit.record(false)).isFalse();
    assertThat(limit.record(false)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, Long.MIN_VALUE, Long.MAX_VALUE, 100_000})
  void unknownAndHugeEstimatesHaveAFiniteBound(long estimate) {
    var limit = new LocalSearchCandidateLimit(estimate);
    int consumed = 0;
    boolean exhausted = false;
    while (!exhausted && consumed <= 327_670) {
      exhausted = limit.record(false);
      consumed++;
    }
    assertThat(exhausted).isTrue();
    assertThat(consumed).isEqualTo(327_670);
  }

  public static class ConstantScore
      implements EasyScoreCalculator<TestdataListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataListSolution solution) {
      return SimpleScore.ZERO;
    }
  }
}
