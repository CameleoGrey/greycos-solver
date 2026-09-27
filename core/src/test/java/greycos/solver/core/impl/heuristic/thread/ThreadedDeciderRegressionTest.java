package greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.decider.MultiThreadedConstructionHeuristicDecider;
import greycos.solver.core.impl.constructionheuristic.decider.forager.DefaultConstructionHeuristicForager;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.localsearch.decider.MultiThreadedLocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.neighborhood.MoveRepository;
import greycos.solver.core.impl.neighborhood.MoveSelectorBasedMoveRepository;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.solver.termination.TerminationFactory;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(10)
class ThreadedDeciderRegressionTest {

  private static final InnerScore<SimpleScore> ZERO = InnerScore.fullyAssigned(SimpleScore.ZERO);

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void terminatedOptionalListConstructionKeepsOriginalZeroScore(String moveThreadCount) {
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withForagerConfig(
                new ConstructionHeuristicForagerConfig()
                    .withPickEarlyType(ConstructionHeuristicPickEarlyType.NEVER))
            .withTerminationConfig(new TerminationConfig().withMoveCountLimit(1L));
    var config =
        new SolverConfig()
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(moveThreadCount)
            .withSolutionClass(TestdataAllowsUnassignedValuesListSolution.class)
            .withEntityClasses(
                TestdataAllowsUnassignedValuesListEntity.class,
                TestdataAllowsUnassignedValuesListValue.class)
            .withConstraintProviderClass(AssignedValuePenalty.class)
            .withPhases(phase);
    var solution =
        SolverFactory.<TestdataAllowsUnassignedValuesListSolution>create(config)
            .buildSolver()
            .solve(TestdataAllowsUnassignedValuesListSolution.generateUninitializedSolution(1, 2));

    assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(solution.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValueList()).isEmpty());
  }

  @Test
  void constructionCountTerminationDoesNotPickPartialResults() throws Exception {
    var fixture =
        new ConstructionFixture(
            new TerminationConfig().withScoreCalculationCountLimit(1L),
            ConstructionHeuristicPickEarlyType.NEVER);
    fixture.results(0, -1);
    fixture.decide();

    assertThat(fixture.step.getStep()).isNull();
    assertThat(fixture.calculationCount).hasValue(1);
    verify(fixture.pipeline, never()).applyStep(anyInt(), any(), any());
  }

  @Test
  void constructionControlledTimeTerminationDoesNotPickPartialResults() throws Exception {
    var fixture =
        new ConstructionFixture(
            new TerminationConfig().withMillisecondsSpentLimit(10L),
            ConstructionHeuristicPickEarlyType.NEVER);
    doAnswer(
            invocation -> {
              fixture.now.set(10);
              return new MoveEvaluationPipeline.Result<>(0, 0, fixture.moves.getFirst(), ZERO);
            })
        .when(fixture.pipeline)
        .take();
    fixture.decide();

    assertThat(fixture.step.getStep()).isNull();
    assertThat(fixture.calculationCount).hasValue(1);
    verify(fixture.pipeline, never()).applyStep(anyInt(), any(), any());
  }

  @Test
  void constructionTerminationWhileWaitingDoesNotPickPartialResults() throws Exception {
    var fixture = new ConstructionFixture(null, ConstructionHeuristicPickEarlyType.NEVER);
    when(fixture.pipeline.take())
        .thenReturn(
            new MoveEvaluationPipeline.Result<>(0, 0, fixture.moves.getFirst(), ZERO), null);
    fixture.decide();

    assertThat(fixture.step.getStep()).isNull();
    verify(fixture.pipeline, never()).applyStep(anyInt(), any(), any());
  }

  @Test
  void constructionInterruptionDoesNotPickPartialResultsAndPreservesInterrupt() throws Exception {
    var fixture = new ConstructionFixture(null, ConstructionHeuristicPickEarlyType.NEVER);
    when(fixture.pipeline.take())
        .thenReturn(new MoveEvaluationPipeline.Result<>(0, 0, fixture.moves.getFirst(), ZERO))
        .thenThrow(new InterruptedException("Interrupted ordered result wait"));
    try {
      fixture.decide();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(fixture.step.getStep()).isNull();
      verify(fixture.pipeline, never()).applyStep(anyInt(), any(), any());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void constructionIntentionalEarlyPickStillCommitsAtCountLimit() throws Exception {
    var fixture =
        new ConstructionFixture(
            new TerminationConfig().withScoreCalculationCountLimit(1L),
            ConstructionHeuristicPickEarlyType.FIRST_NON_DETERIORATING_SCORE);
    fixture.results(0, -1);
    fixture.decide();

    assertThat(fixture.step.getStep()).isSameAs(fixture.moves.getFirst());
    assertThat(fixture.step.getScore()).isEqualTo(ZERO);
    verify(fixture.pipeline).applyStep(1, fixture.moves.getFirst(), ZERO);
  }

  @Test
  void constructionCompleteStepPicksBestMove() throws Exception {
    var fixture = new ConstructionFixture(null, ConstructionHeuristicPickEarlyType.NEVER);
    fixture.results(-1, 0);
    fixture.decide();

    assertThat(fixture.step.getStep()).isSameAs(fixture.moves.get(1));
    verify(fixture.pipeline).applyStep(1, fixture.moves.get(1), ZERO);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void constructionCountsPhysicalCalculationsOnceAcrossPhases(boolean earlyPick) throws Exception {
    var fixture =
        new ConstructionFixture(
            null,
            earlyPick
                ? ConstructionHeuristicPickEarlyType.FIRST_NON_DETERIORATING_SCORE
                : ConstructionHeuristicPickEarlyType.NEVER);
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      fixture.beginPhase(phaseIndex);
      fixture.results(0, -1);
      // Includes worker initialization, consumed candidates and any discarded speculative work.
      when(fixture.pipeline.getCalculationCount()).thenReturn(7L);
      fixture.decide();
      assertThat(fixture.calculationCount).hasValue((phaseIndex + 1L) * (earlyPick ? 1 : 2));
      fixture.decider.phaseEnded(fixture.phase);
      fixture.phase.endingNow();

      assertThat(fixture.phase.getPhaseScoreCalculationCount()).isEqualTo(7);
      assertThat(fixture.solverScope.getScoreCalculationCount()).isEqualTo((phaseIndex + 1L) * 7);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void localSearchCountsOnlyScoredTransfersOnceAcrossPhases(boolean earlyPick) throws Exception {
    var calculationCount = new AtomicLong();
    InnerScoreDirector<Object, SimpleScore> director = mock(InnerScoreDirector.class);
    when(director.getCalculationCount()).thenAnswer(invocation -> calculationCount.get());
    doAnswer(invocation -> calculationCount.incrementAndGet())
        .when(director)
        .incrementCalculationCount();
    var solverScope = new SolverScope<Object>();
    solverScope.setScoreDirector(director);
    MoveRepository<Object> repository = mock(MoveSelectorBasedMoveRepository.class);
    LocalSearchForager<Object> forager = mock(LocalSearchForager.class);
    when(forager.isQuitEarly()).thenReturn(earlyPick);
    MoveEvaluationPipeline<Object> pipeline = mock(MoveEvaluationPipeline.class);
    var moves = List.of(move(), move(), move());
    var decider =
        new MultiThreadedLocalSearchDecider<>(
            "", noTermination(), repository, mock(Acceptor.class), forager, Thread::new, 2, 3) {
          @Override
          protected ExecutorService createThreadPoolExecutor() {
            return mock(ExecutorService.class);
          }

          @Override
          protected MoveEvaluationPipeline<Object> createMoveEvaluationPipeline(int phaseIndex) {
            return pipeline;
          }
        };
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      var phase = new LocalSearchPhaseScope<>(solverScope, phaseIndex);
      phase.startingNow();
      decider.phaseStarted(phase);
      when(repository.iterator()).thenReturn(moves.iterator());
      when(pipeline.take())
          .thenReturn(
              new MoveEvaluationPipeline.Result<>(0, 0, moves.get(0), null),
              new MoveEvaluationPipeline.Result<>(0, 1, moves.get(1), ZERO),
              new MoveEvaluationPipeline.Result<>(0, 2, moves.get(2), ZERO));
      when(pipeline.getCalculationCount()).thenReturn(7L);
      var step = new LocalSearchStepScope<>(phase, 0);
      decider.stepStarted(step);
      decider.decideNextStep(step);
      assertThat(calculationCount).hasValue((phaseIndex + 1L) * (earlyPick ? 1 : 2));
      decider.phaseEnded(phase);
      phase.endingNow();

      assertThat(phase.getPhaseScoreCalculationCount()).isEqualTo(7);
      assertThat(solverScope.getScoreCalculationCount()).isEqualTo((phaseIndex + 1L) * 7);
    }
  }

  private static PhaseTermination<Object> noTermination() {
    return PhaseTermination.bridge(new BasicPlumbingTermination<>(false));
  }

  @SuppressWarnings("unchecked")
  private static Move<Object> move() {
    return mock(Move.class);
  }

  public static final class AssignedValuePenalty implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataAllowsUnassignedValuesListValue.class)
            .penalize(SimpleScore.ONE)
            .asConstraint("Assigned value penalty")
      };
    }
  }

  private static final class ConstructionFixture {
    private final AtomicLong calculationCount = new AtomicLong();
    private final AtomicLong now = new AtomicLong();
    private final SolverScope<Object> solverScope;
    private final MoveEvaluationPipeline<Object> pipeline;
    private final List<Move<Object>> moves = List.of(move(), move());
    private final MultiThreadedConstructionHeuristicDecider<Object> decider;
    private ConstructionHeuristicPhaseScope<Object> phase;
    private ConstructionHeuristicStepScope<Object> step;

    @SuppressWarnings("unchecked")
    private ConstructionFixture(
        TerminationConfig terminationConfig, ConstructionHeuristicPickEarlyType pickEarly) {
      InnerScoreDirector<Object, SimpleScore> director = mock(InnerScoreDirector.class);
      when(director.getCalculationCount()).thenAnswer(invocation -> calculationCount.get());
      doAnswer(invocation -> calculationCount.incrementAndGet())
          .when(director)
          .incrementCalculationCount();
      Clock clock = mock(Clock.class);
      when(clock.millis()).thenAnswer(invocation -> now.get());
      solverScope = new SolverScope<>(clock);
      solverScope.setScoreDirector(director);
      pipeline = mock(MoveEvaluationPipeline.class);
      PhaseTermination<Object> termination =
          terminationConfig == null
              ? noTermination()
              : (PhaseTermination<Object>)
                  TerminationFactory.<Object>create(terminationConfig)
                      .buildTermination(mock(HeuristicConfigPolicy.class));
      decider =
          new MultiThreadedConstructionHeuristicDecider<>(
              "",
              termination,
              new DefaultConstructionHeuristicForager<>(pickEarly),
              Thread::new,
              2,
              2) {
            @Override
            protected ExecutorService createThreadPoolExecutor() {
              return mock(ExecutorService.class);
            }

            @Override
            protected MoveEvaluationPipeline<Object> createMoveEvaluationPipeline(int phaseIndex) {
              return pipeline;
            }
          };
      beginPhase(0);
    }

    private void beginPhase(int phaseIndex) {
      phase = new ConstructionHeuristicPhaseScope<>(solverScope, phaseIndex);
      phase.getLastCompletedStepScope().setScore(ZERO);
      phase.startingNow();
      decider.phaseStarted(phase);
      step = new ConstructionHeuristicStepScope<>(phase, 0);
      decider.stepStarted(step);
    }

    private void results(int firstScore, int secondScore) throws Exception {
      when(pipeline.take())
          .thenReturn(
              new MoveEvaluationPipeline.Result<>(
                  0, 0, moves.get(0), InnerScore.fullyAssigned(SimpleScore.of(firstScore))),
              new MoveEvaluationPipeline.Result<>(
                  0, 1, moves.get(1), InnerScore.fullyAssigned(SimpleScore.of(secondScore))));
    }

    private void decide() {
      decider.decideNextStep(step, moves.iterator());
    }
  }
}
