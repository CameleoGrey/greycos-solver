package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchPhaseScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class IteratedLocalSearchMigrationRevisionTest {

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void repeatedSolveDiscardsLegacyMigrantWithCompatiblePlanningIds(String threads) {
    var solver = solver(threads);
    var queued = new AtomicBoolean();
    var pendingAtStart = new ArrayList<Boolean>();
    var restarts = new ArrayList<Long>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> phase) {
            pendingAtStart.add(phase.getSolverScope().hasPendingMove());
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> phase) {
            restarts.add(
                ((IteratedLocalSearchPhaseScope<TestdataSolution>) phase).getMigrantRestarts());
            if (queued.compareAndSet(false, true)) queueLegacyMigrant(phase);
          }
        });
    var first = solver.solve(problem(10));
    assertUnchanged(first);
    assertThat(solver.getSolverScope().hasPendingMove()).isTrue();

    // Same entity/value IDs and legal assignments deliberately allow legacy rebasing to succeed.
    // Empty search selectors ensure any improvement here could only come from the stale migrant.
    var second = solver.solve(problem(20));
    assertUnchanged(second);
    assertThat(((RewardValue) second.getValueList().getLast()).reward).isEqualTo(20);
    assertThat(second.getValueList().getLast()).isNotSameAs(first.getValueList().getLast());
    assertThat(pendingAtStart).containsExactly(false, false);
    assertThat(restarts).containsExactly(0L, 0L);
    assertThat(solver.getSolverScope().hasPendingMove()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void problemFactRevisionDiscardsLegacyMigrantBeforeApplyingChanges(String threads) {
    var solver = solver(threads);
    var queued = new AtomicBoolean();
    var pendingAtStart = new ArrayList<Boolean>();
    var pendingDuringChange = new ArrayList<Boolean>();
    var restarts = new ArrayList<Long>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> phase) {
            pendingAtStart.add(phase.getSolverScope().hasPendingMove());
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> phase) {
            restarts.add(
                ((IteratedLocalSearchPhaseScope<TestdataSolution>) phase).getMigrantRestarts());
            if (queued.compareAndSet(false, true)) {
              queueLegacyMigrant(phase);
              solver.addProblemChange(
                  (solution, director) -> {
                    pendingDuringChange.add(solver.getSolverScope().hasPendingMove());
                    // Preserve IDs, ranges and pins; fresh scoring would still favor the stale
                    // target.
                    director.changeProblemProperty(
                        (RewardValue) solution.getValueList().getLast(),
                        value -> value.reward = 20);
                  });
            }
          }
        });

    var result = solver.solve(problem(10));
    assertUnchanged(result);
    assertThat(((RewardValue) result.getValueList().getLast()).reward).isEqualTo(20);
    assertThat(solver.isEveryProblemChangeProcessed()).isTrue();
    assertThat(pendingDuringChange).containsExactly(false);
    assertThat(pendingAtStart).containsExactly(false, false);
    assertThat(restarts).containsExactly(0L, 0L);
    assertThat(solver.getSolverScope().hasPendingMove()).isFalse();
  }

  private static void queueLegacyMigrant(AbstractPhaseScope<TestdataSolution> phase) {
    var director = phase.getScoreDirector();
    var migrant = director.cloneWorkingSolution();
    migrant.getEntityList().getFirst().setValue(migrant.getValueList().getLast());
    var score = new RewardCalculator().calculateScore(migrant);
    assertThat(score).isGreaterThan(phase.<SimpleScore>getBestScore().raw());
    phase
        .getSolverScope()
        .setPendingMoveIfBetter(
            SolutionSyncMove.createMove(director, migrant), InnerScore.fullyAssigned(score), true);
    assertThat(phase.getSolverScope().hasPendingMove()).isTrue();
  }

  private static void assertUnchanged(TestdataSolution solution) {
    assertThat(solution.getEntityList().getFirst().getValue())
        .isSameAs(solution.getValueList().getFirst());
    assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(new RewardCalculator().calculateScore(solution)).isEqualTo(solution.getScore());
  }

  private static TestdataSolution problem(int reward) {
    var solution = new TestdataSolution("problem");
    var values = List.<TestdataValue>of(new RewardValue("a", 0), new RewardValue("b", reward));
    solution.setValueList(values);
    solution.setEntityList(List.of(new TestdataEntity("entity", values.getFirst())));
    return solution;
  }

  private static DefaultSolver<TestdataSolution> solver(String threads) {
    var phase =
        new IteratedLocalSearchPhaseConfig()
            .withLocalSearch(new LocalSearchPhaseConfig().withMoveSelectorConfig(emptyMoves()))
            .withPerturbationMoveSelectorConfig(emptyMoves())
            .withPerturbationStrengths(1)
            .withPerturbationAttemptLimit(2)
            .withEpisodeCandidateAttemptLimit(2)
            .withIterationCountLimit(1);
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(RewardCalculator.class)
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withMoveThreadCount(threads)
            .withRandomSeed(0L)
            .withPhases(phase);
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }

  private static MoveIteratorFactoryConfig emptyMoves() {
    return new MoveIteratorFactoryConfig().withMoveIteratorFactoryClass(EmptyMoves.class);
  }

  public static final class RewardValue extends TestdataValue {
    private int reward;

    public RewardValue() {}

    public RewardValue(String code, int reward) {
      super(code);
      this.reward = reward;
    }
  }

  public static final class RewardCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.of(((RewardValue) solution.getEntityList().getFirst().getValue()).reward);
    }
  }

  public static final class EmptyMoves
      implements MoveIteratorFactory<TestdataSolution, Move<TestdataSolution>> {
    @Override
    public long getSize(ScoreDirector<TestdataSolution> director) {
      return 0;
    }

    @Override
    public Iterator<Move<TestdataSolution>> createOriginalMoveIterator(
        ScoreDirector<TestdataSolution> director) {
      return Collections.emptyIterator();
    }

    @Override
    public Iterator<Move<TestdataSolution>> createRandomMoveIterator(
        ScoreDirector<TestdataSolution> director, RandomGenerator random) {
      return Collections.emptyIterator();
    }
  }
}
