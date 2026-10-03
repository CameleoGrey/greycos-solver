package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.decider.forager.AbstractLocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.DummySimpleScoreEasyScoreCalculator;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Timeout(15)
class SerialWorkCountTerminationTest {

  @ParameterizedTest
  @CsvSource({
    "move, false, false", "move, true, false", "calculation, false, false",
        "calculation, true, false",
    "move, false, true", "move, true, true", "calculation, false, true", "calculation, true, true"
  })
  void successivePhasesUseLocalBudgetsAndGlobalBridgesRemainCumulative(
      String kind, boolean construct, boolean global) {
    var firstSearch = phase().withTerminationConfig(budget(kind, 2));
    var secondSearch =
        phase()
            .withTerminationConfig(
                global ? new TerminationConfig().withStepCountLimit(20) : budget(kind, 5));
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(DummySimpleScoreEasyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(
                construct ? new ConstructionHeuristicPhaseConfig() : firstSearch, secondSearch);
    if (global) {
      config.withTerminationConfig(budget(kind, 7));
    }
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var phaseCounts = new ArrayList<Long>();
    var startingCounts = new ArrayList<Long>();
    var startingGradients = new ArrayList<Double>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            startingCounts.add(
                kind.equals("move")
                    ? solver.getMoveEvaluationCount()
                    : scope.getScoreDirector().getCalculationCount());
            startingGradients.add(
                ((greycos.solver.core.impl.phase.AbstractPhase<TestdataSolution>)
                        solver.getPhaseList().get(scope.getPhaseIndex()))
                    .getPhaseTermination()
                    .calculatePhaseTimeGradient(scope));
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            phaseCounts.add(
                kind.equals("move")
                    ? scope.getPhaseMoveEvaluationCount()
                    : scope.getPhaseScoreCalculationCount());
          }
        });
    var input = TestdataSolution.generateSolution(2, 2);
    if (construct) {
      input.getEntityList().forEach(entity -> entity.setValue(null));
    }
    var result = solver.solve(input);
    assertThat(result.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
    assertThat(phaseCounts).hasSize(2);
    assertThat(phaseCounts.getFirst()).isPositive();
    if (global) {
      assertThat(phaseCounts.get(1)).isEqualTo(7L - startingCounts.get(1));
      assertThat(startingGradients.get(1)).isEqualTo(startingCounts.get(1) / 7.0);
      assertThat(
              kind.equals("move")
                  ? solver.getMoveEvaluationCount()
                  : solver.getSolverScope().getScoreDirector().getCalculationCount())
          .isEqualTo(7L);
    } else {
      assertThat(phaseCounts.get(1)).isEqualTo(5L);
      assertThat(startingGradients.get(1)).isZero();
      if (!construct) {
        assertThat(phaseCounts.getFirst()).isEqualTo(2L);
      }
    }
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void configuredForagersCountEveryEvaluatedMoveExactlyOnce(boolean custom, boolean global) {
    var forager =
        custom
            ? new LocalSearchForagerConfig().withForagerClass(FirstAcceptedForager.class)
            : new LocalSearchForagerConfig().withAcceptedCountLimit(1);
    var phase =
        new LocalSearchPhaseConfig()
            .withForagerConfig(forager)
            .withTerminationConfig(
                global ? new TerminationConfig().withStepCountLimit(20) : budget("move", 5));
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(DummySimpleScoreEasyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(phase);
    if (global) {
      config.withTerminationConfig(budget("move", 5));
    }
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var completedSteps = new ArrayList<Integer>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            completedSteps.add(scope.getNextStepIndex());
            assertThat(scope.getPhaseMoveEvaluationCount()).isEqualTo(5L);
            assertThat(scope.getPhaseScoreCalculationCount()).isEqualTo(5L);
          }
        });
    solver.solve(TestdataSolution.generateSolution(2, 2));
    assertThat(completedSteps).containsExactly(5);
    assertThat(solver.getMoveEvaluationCount()).isEqualTo(5L);
  }

  private static LocalSearchPhaseConfig phase() {
    return new LocalSearchPhaseConfig()
        .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1));
  }

  private static TerminationConfig budget(String kind, long count) {
    return kind.equals("move")
        ? new TerminationConfig().withMoveCountLimit(count)
        : new TerminationConfig().withScoreCalculationCountLimit(count);
  }

  public static final class FirstAcceptedForager
      extends AbstractLocalSearchForager<TestdataSolution> {
    private LocalSearchMoveScope<TestdataSolution> picked;
    private long selected;

    @Override
    public boolean supportsNeverEndingMoveSelector() {
      return true;
    }

    @Override
    public void stepStarted(LocalSearchStepScope<TestdataSolution> step) {
      picked = null;
      selected = 0L;
    }

    @Override
    public void addMove(LocalSearchMoveScope<TestdataSolution> move) {
      selected++;
      if (move.getAccepted()) {
        picked = move;
      }
    }

    @Override
    public boolean isQuitEarly() {
      return picked != null;
    }

    @Override
    public LocalSearchMoveScope<TestdataSolution> pickMove(
        LocalSearchStepScope<TestdataSolution> step) {
      step.setSelectedMoveCount(selected);
      step.setAcceptedMoveCount(picked == null ? 0L : 1L);
      return picked;
    }
  }
}
