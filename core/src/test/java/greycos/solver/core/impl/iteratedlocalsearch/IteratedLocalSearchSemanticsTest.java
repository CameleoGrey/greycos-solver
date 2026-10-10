package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tiny exact landscapes distinguish the accepted incumbent, episode best and solver best. */
@Timeout(30)
class IteratedLocalSearchSemanticsTest {

  @Test
  void equalAndWorseEpisodesRetainIncumbentAndStrictImprovementResetsStrength() {
    var phase =
        phase(EmptyMoves.class, NextMoves.class)
            .withPerturbationStrengths(1, 2, 3)
            .withIterationCountLimit(3);
    var run = run(phase, 0, -1, 2, -3, -4);
    assertThat(run.steps).extracting(Step::value).containsExactly(1, 1, 2, 3);
    assertThat(run.steps).extracting(Step::strength).containsExactly(1, 2, 2, 1);
    assertThat(run.scope.getAcceptedIterations()).isEqualTo(1);
    assertThat(run.scope.getRejectedIterations()).isEqualTo(2);
    assertThat(run.scope.getCompletedIterations()).isEqualTo(3);
    assertThat(run.scope.getEpisodes()).isEqualTo(4);
    assertThat(run.phaseEndValue).isEqualTo(2);
    assertThat(value(run.result)).isEqualTo(2);
    assertThat(run.result.getScore()).isEqualTo(SimpleScore.of(2));
  }

  @Test
  void equalScoreOuterTrialsAdvanceWrapAndKeepFirstAssignment() {
    var phase =
        phase(EmptyMoves.class, NextMoves.class)
            .withPerturbationStrengths(1, 2, 3)
            .withIterationCountLimit(4);
    var run = run(phase, 0, 0, 0, 0, 0);
    assertThat(run.steps).extracting(Step::strength).containsExactly(1, 2, 2, 3, 3, 3, 1);
    assertThat(run.scope.getAcceptedIterations()).isZero();
    assertThat(run.scope.getRejectedIterations()).isEqualTo(4);
    assertThat(run.scope.getCompletedIterations()).isEqualTo(4);
    assertThat(run.phaseEndValue).isZero();
    assertThat(value(run.result)).isZero();
  }

  @Test
  void initialEpisodeRestoresItsBestInsteadOfItsLastAcceptedState() {
    var phase = phase(NextMoves.class, EmptyMoves.class);
    var run = run(phase, 100, 150, 110);
    assertThat(run.steps).extracting(Step::value).containsExactly(1, 2);
    assertThat(run.steps)
        .extracting(Step::origin)
        .containsOnly(IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH);
    assertThat(run.phaseEndValue).isEqualTo(1);
    assertThat(value(run.result)).isEqualTo(1);
    assertThat(run.result.getScore()).isEqualTo(SimpleScore.of(150));
    assertThat(run.scope.getCompletedIterations()).isEqualTo(1);
    assertThat(run.scope.getEpisodes()).isEqualTo(1);
  }

  @Test
  void initialEpisodeKeepsFirstAssignmentOnEqualBestTie() {
    var run = run(phase(NextMoves.class, EmptyMoves.class), 100, 100, 100);
    assertThat(run.steps).extracting(Step::value).containsExactly(1, 2);
    assertThat(run.phaseEndValue).isZero();
    assertThat(value(run.result)).isZero();
    assertThat(run.result.getScore()).isEqualTo(SimpleScore.of(100));
  }

  @Test
  void successfulInverseShakePublishesIntermediateBestBeforeRestoringIncumbent() {
    var phase =
        phase(EmptyMoves.class, NextMoves.class)
            .withPerturbationStrengths(2)
            .withPerturbationAttemptLimit(2);
    var run = run(phase, 0, 5);
    assertThat(run.steps).extracting(Step::value).containsExactly(1, 0);
    assertThat(run.scope.getCompletedIterations()).isEqualTo(1);
    assertThat(run.scope.getFailedPerturbations()).isEqualTo(1);
    assertThat(run.scope.getNoChangeCount()).isEqualTo(1);
    assertThat(run.scope.getCompletionReason()).isEqualTo("NO_PROGRESS");
    assertThat(run.phaseEndValue).isZero();
    assertThat(value(run.result)).isEqualTo(1);
    assertThat(run.result.getScore()).isEqualTo(SimpleScore.of(5));
  }

  @Test
  void finiteExhaustedPerturbationStopsAfterOneCompleteStrengthCycle() {
    var run =
        run(
            phase(EmptyMoves.class, EmptyMoves.class)
                .withPerturbationStrengths(1, 2, 4)
                .withIterationCountLimit(20),
            0,
            1);
    assertThat(run.steps).isEmpty();
    assertThat(run.scope.getCompletedIterations()).isEqualTo(3);
    assertThat(run.scope.getFailedPerturbations()).isEqualTo(3);
    assertThat(run.scope.getEpisodes()).isEqualTo(1);
    assertThat(run.scope.getCompletionReason()).isEqualTo("NO_PROGRESS");
  }

  @Test
  void failedPerturbationsConsumeOuterIterationCap() {
    var run =
        run(
            phase(EmptyMoves.class, EmptyMoves.class)
                .withPerturbationStrengths(1, 2, 4)
                .withIterationCountLimit(2),
            0,
            1);
    assertThat(run.scope.getCompletedIterations()).isEqualTo(2);
    assertThat(run.scope.getFailedPerturbations()).isEqualTo(2);
    assertThat(run.scope.getEpisodes()).isEqualTo(1);
    assertThat(run.steps).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void endlessNonDoableEpisodesAndPerturbationsAreBounded(String threads) {
    var phase =
        phase(NoChangeMoves.class, NoChangeMoves.class)
            .withMoveThreadCount(threads)
            .withPerturbationStrengths(1, 2)
            .withPerturbationAttemptLimit(3)
            .withEpisodeCandidateAttemptLimit(5);
    var run = run(phase, 0, 1);
    assertThat(run.steps).isEmpty();
    assertThat(run.scope.getEpisodes()).isEqualTo(1);
    assertThat(run.scope.getCompletedIterations()).isEqualTo(2);
    assertThat(run.scope.getFailedPerturbations()).isEqualTo(2);
    assertThat(run.scope.getCompletionReason()).isEqualTo("NO_PROGRESS");
    assertThat(run.scope.episodeAttempts).isEqualTo(5);
    assertThat(run.scope.perturbationAttempts).isEqualTo(6);
    assertThat(value(run.result)).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void incompleteForagerDecisionAtAttemptCapDoesNotCommitOrPublishCandidate(String threads) {
    var phase =
        phase(NextMoves.class, EmptyMoves.class)
            .withMoveThreadCount(threads)
            .withEpisodeCandidateAttemptLimit(1);
    phase
        .getLocalSearchConfig()
        .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(2));
    var run = run(phase, 0, 5, 10);
    assertThat(run.steps).isEmpty();
    assertThat(run.phaseEndValue).isZero();
    assertThat(value(run.result)).isZero();
    assertThat(run.result.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(run.scope.episodeAttempts).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void naturallyCompletedDecisionOnLastPermittedAttemptCommits(String threads) {
    var phase =
        phase(NextMoves.class, EmptyMoves.class)
            .withMoveThreadCount(threads)
            .withEpisodeCandidateAttemptLimit(1);
    var run = run(phase, 0, 5, 10);
    assertThat(run.steps).extracting(Step::value).containsExactly(1);
    assertThat(run.phaseEndValue).isEqualTo(1);
    assertThat(value(run.result)).isEqualTo(1);
    assertThat(run.result.getScore()).isEqualTo(SimpleScore.of(5));
    assertThat(run.scope.episodeAttempts).isEqualTo(1);
  }

  @Test
  void outerPrimitiveStepLimitDoesNotResetAcrossEpisodes() {
    var phase =
        phase(NextMoves.class, NextMoves.class)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(3));
    var run = run(phase, 0, 1, 2, 3, 4, 5);
    assertThat(run.steps).hasSize(3);
    assertThat(run.steps).extracting(Step::index).containsExactly(0, 1, 2);
    assertThat(run.steps)
        .extracting(Step::origin)
        .containsExactly(
            IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH,
            IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH,
            IteratedLocalSearchStepScope.Origin.PERTURBATION);
    assertThat(run.scope.getCompletedIterations()).isZero();
    assertThat(run.result.getScore()).isEqualTo(SimpleScore.of(3));
  }

  @Test
  void requiredAndInconsistentBudgetsFailDuringSolverConstruction() {
    assertInvalid(new IteratedLocalSearchPhaseConfig(), "localSearch");
    assertInvalid(
        phase(EmptyMoves.class, EmptyMoves.class).withPerturbationStrengths(),
        "perturbationStrengths");
    assertInvalid(
        phase(EmptyMoves.class, EmptyMoves.class).withPerturbationStrengths(0),
        "perturbationStrengths");
    assertInvalid(
        phase(EmptyMoves.class, EmptyMoves.class).withPerturbationStrengths(2, 1),
        "perturbationStrengths");
    assertInvalid(
        phase(EmptyMoves.class, EmptyMoves.class).withPerturbationStrengths(1, 1),
        "perturbationStrengths");
    assertInvalid(
        phase(EmptyMoves.class, EmptyMoves.class)
            .withPerturbationStrengths(4)
            .withPerturbationAttemptLimit(3),
        "perturbationAttemptLimit");
    assertInvalid(
        phase(EmptyMoves.class, EmptyMoves.class).withEpisodeCandidateAttemptLimit(0),
        "episodeCandidateAttemptLimit");
    assertInvalid(
        phase(EmptyMoves.class, EmptyMoves.class).withIterationCountLimit(0),
        "iterationCountLimit");
    var missingAttemptLimit = phase(EmptyMoves.class, EmptyMoves.class);
    missingAttemptLimit.setPerturbationAttemptLimit(null);
    assertInvalid(missingAttemptLimit, "perturbationAttemptLimit");
    var missingEpisodeLimit = phase(EmptyMoves.class, EmptyMoves.class);
    missingEpisodeLimit.setEpisodeCandidateAttemptLimit(null);
    assertInvalid(missingEpisodeLimit, "episodeCandidateAttemptLimit");
    var unboundedOuter = phase(EmptyMoves.class, EmptyMoves.class);
    unboundedOuter.setIterationCountLimit(null);
    assertInvalid(unboundedOuter, "iterationCountLimit");
    var conflicting = phase(EmptyMoves.class, EmptyMoves.class).withMoveThreadCount("2");
    conflicting.getLocalSearchConfig().withMoveThreadCount("1");
    assertInvalid(conflicting, "moveThreadCount");
  }

  private static void assertInvalid(IteratedLocalSearchPhaseConfig phase, String property) {
    assertThatThrownBy(() -> SolverFactory.create(config(phase)).buildSolver())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(property);
  }

  private static IteratedLocalSearchPhaseConfig phase(
      Class<? extends MoveIteratorFactory> improvement,
      Class<? extends MoveIteratorFactory> perturbation) {
    return new IteratedLocalSearchPhaseConfig()
        .withMoveThreadCount("NONE")
        .withLocalSearch(
            new LocalSearchPhaseConfig()
                .withMoveSelectorConfig(selector(improvement))
                .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(4))
                .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1))
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)))
        .withPerturbationMoveSelectorConfig(selector(perturbation))
        .withPerturbationStrengths(1)
        .withPerturbationAttemptLimit(8)
        .withEpisodeCandidateAttemptLimit(8)
        .withIterationCountLimit(5);
  }

  private static MoveIteratorFactoryConfig selector(Class<? extends MoveIteratorFactory> factory) {
    return new MoveIteratorFactoryConfig()
        .withMoveIteratorFactoryClass(factory)
        .withSelectionOrder(SelectionOrder.RANDOM);
  }

  private static SolverConfig config(IteratedLocalSearchPhaseConfig phase) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(LandscapeScoreCalculator.class)
        .withMoveThreadCount("NONE")
        .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
        .withRandomSeed(0L)
        .withPhases(phase);
  }

  private static Run run(IteratedLocalSearchPhaseConfig phase, int... scores) {
    var problem = new TestdataSolution("landscape");
    var values = new ArrayList<TestdataValue>();
    for (int index = 0; index < scores.length; index++)
      values.add(new TestdataValue(index + ":" + scores[index]));
    problem.setValueList(values);
    problem.setEntityList(List.of(new TestdataEntity("entity", values.getFirst())));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config(phase)).buildSolver();
    var run = new Run();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            var ilsStep = (IteratedLocalSearchStepScope<TestdataSolution>) step;
            assertThat(new LandscapeScoreCalculator().calculateScore(step.getWorkingSolution()))
                .isEqualTo(step.getScore().raw());
            run.steps.add(
                new Step(
                    step.getStepIndex(),
                    value(step.getWorkingSolution()),
                    ilsStep.getOrigin(),
                    ilsStep.getPhaseScope().getStrength()));
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> phaseScope) {
            run.scope = (IteratedLocalSearchPhaseScope<TestdataSolution>) phaseScope;
            run.phaseEndValue = value(phaseScope.getWorkingSolution());
          }
        });
    run.result = solver.solve(problem);
    assertThat(new LandscapeScoreCalculator().calculateScore(run.result))
        .isEqualTo(run.result.getScore());
    assertThat(run.result.getValueList())
        .anySatisfy(
            value -> assertThat(value).isSameAs(run.result.getEntityList().getFirst().getValue()));
    return run;
  }

  private static int value(TestdataSolution solution) {
    return Integer.parseInt(solution.getEntityList().getFirst().getValue().getCode().split(":")[0]);
  }

  public static final class LandscapeScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.of(
          Long.parseLong(solution.getEntityList().getFirst().getValue().getCode().split(":")[1]));
    }
  }

  public static class NextMoves
      implements MoveIteratorFactory<TestdataSolution, Move<TestdataSolution>> {
    protected int destination(TestdataSolution solution) {
      return (value(solution) + 1) % solution.getValueList().size();
    }

    @Override
    public long getSize(ScoreDirector<TestdataSolution> director) {
      return Long.MAX_VALUE;
    }

    @Override
    public Iterator<Move<TestdataSolution>> createOriginalMoveIterator(
        ScoreDirector<TestdataSolution> director) {
      return createRandomMoveIterator(director, null);
    }

    @Override
    public Iterator<Move<TestdataSolution>> createRandomMoveIterator(
        ScoreDirector<TestdataSolution> director, RandomGenerator random) {
      return new Iterator<>() {
        @Override
        public boolean hasNext() {
          return true;
        }

        @Override
        public Move<TestdataSolution> next() {
          var solution = director.getWorkingSolution();
          var descriptor =
              ((InnerScoreDirector<TestdataSolution, ?>) director)
                  .getSolutionDescriptor()
                  .findEntityDescriptorOrFail(TestdataEntity.class)
                  .getGenuineVariableDescriptor("value");
          return new ChangeMove<>(
              descriptor,
              solution.getEntityList().getFirst(),
              solution.getValueList().get(destination(solution)));
        }
      };
    }
  }

  public static final class NoChangeMoves extends NextMoves {
    @Override
    protected int destination(TestdataSolution solution) {
      return value(solution);
    }
  }

  public static final class EmptyMoves extends NextMoves {
    @Override
    public long getSize(ScoreDirector<TestdataSolution> director) {
      return 0;
    }

    @Override
    public Iterator<Move<TestdataSolution>> createRandomMoveIterator(
        ScoreDirector<TestdataSolution> director, RandomGenerator random) {
      return Collections.emptyIterator();
    }
  }

  private record Step(
      int index, int value, IteratedLocalSearchStepScope.Origin origin, int strength) {}

  private static final class Run {
    final List<Step> steps = new ArrayList<>();
    IteratedLocalSearchPhaseScope<TestdataSolution> scope;
    int phaseEndValue;
    TestdataSolution result;
  }
}
