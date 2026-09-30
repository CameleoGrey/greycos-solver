package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
import greycos.solver.core.config.localsearch.GuidedLocalSearchLevelScaleConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class GuidedLocalSearchAllLevelsTest {

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void implicitAllLevelsCrossesAnEarlierHardBarrierAndThenImprovesSoft(String threads) {
    var solver = build(null, BridgeScore.class, 3, threads);
    var trace = new ArrayList<BendableScore>();
    var focuses = observe(solver, trace);
    var best = solver.solve(solution("A", "B", "C", "D"));
    assertThat(trace).containsExactly(score(-1, -2, -8), score(0, 0, -5), score(0, 0, 0));
    assertThat(best.getScore()).isEqualTo(score(0, 0, 0));
    assertThat(focuses).containsExactly(1, 1, 2);
    assertThat(decider(solver).getStatistics().penaltyUpdates()).isPositive();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void repeatedFeasibilityRecoveryDoesNotRestartTheFocusSchedule(String threads) {
    var solver = build(new GuidedLocalSearchConfig(), OscillatingScore.class, 34, threads);
    var trace = new ArrayList<BendableScore>();
    var focuses = observe(solver, trace);
    var best = solver.solve(solution("A", "B"));
    assertThat(best.getScore()).isEqualTo(score(0, 0, 0));
    assertThat(trace.get(0)).isEqualTo(score(-1, 0, 0));
    assertThat(trace.get(1)).isEqualTo(score(0, 0, 0));
    assertThat(focuses.subList(0, 15)).containsOnly(2);
    assertThat(focuses.get(15)).isEqualTo(1);
    // Recovering feasibility at step 15 must not preempt the newly started level-1 epoch.
    assertThat(focuses.get(16)).isEqualTo(1);
    assertThat(focuses).contains(0);
    assertThat(decider(solver).getFocusSwitchCount()).isGreaterThanOrEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void committedMoveLimitCyclesAllLevelsAndScaleOverrideRemainsExact(String threads) {
    var gls =
        new GuidedLocalSearchConfig()
            .withFocusStepLimit(3)
            .withFocusPenaltyUpdateLimit(100)
            .withLevelScaleList(
                List.of(
                    new GuidedLocalSearchLevelScaleConfig()
                        .withScoreLevelIndex(0)
                        .withScale(new BigDecimal("0.0125"))));
    var solver = build(gls, FlatScore.class, 10, threads);
    var focuses = observe(solver, new ArrayList<>());
    solver.solve(solution("A", "B"));
    assertThat(focuses).containsExactly(2, 2, 2, 1, 1, 1, 0, 0, 0, 2);
    assertThat(
            decider(solver)
                .getAutomaticScale(0)
                .compareTo(GuidedLocalSearchScale.of(new BigDecimal("0.0125"))))
        .isZero();
  }

  @Test
  void explicitAllLevelsRejectsTargetAndInvalidScaleOverrides() {
    assertThatThrownBy(
            () ->
                build(
                    new GuidedLocalSearchConfig()
                        .withGuidanceMode(GuidedLocalSearchGuidanceMode.ALL_LEVELS)
                        .withTargetScoreLevelIndex(0),
                    FlatScore.class,
                    1,
                    "NONE"))
        .hasMessageContaining("targetScoreLevelIndex");
    assertThatThrownBy(
            () ->
                build(
                    new GuidedLocalSearchConfig()
                        .withLevelScaleList(
                            List.of(
                                new GuidedLocalSearchLevelScaleConfig()
                                    .withScoreLevelIndex(3)
                                    .withScale(BigDecimal.ONE))),
                    FlatScore.class,
                    1,
                    "NONE"))
        .hasMessageContaining("scoreLevelIndex");
    var duplicate =
        new GuidedLocalSearchLevelScaleConfig().withScoreLevelIndex(0).withScale(BigDecimal.ONE);
    assertThatThrownBy(
            () ->
                build(
                    new GuidedLocalSearchConfig().withLevelScaleList(List.of(duplicate, duplicate)),
                    FlatScore.class,
                    1,
                    "NONE"))
        .hasMessageContaining("Duplicate");
  }

  private static MultiHardSolution solution(String... codes) {
    var solution = new MultiHardSolution();
    solution.setValueList(java.util.Arrays.stream(codes).map(TestdataValue::new).toList());
    solution.setEntityList(
        List.of(new TestdataEntity("entity", solution.getValueList().getFirst())));
    return solution;
  }

  private static DefaultSolver<MultiHardSolution> build(
      GuidedLocalSearchConfig gls,
      Class<? extends EasyScoreCalculator<MultiHardSolution, BendableScore>> calculator,
      int steps,
      String threads) {
    var phase =
        new LocalSearchPhaseConfig()
            .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
            .withMoveSelectorConfig(
                new MoveIteratorFactoryConfig()
                    .withMoveIteratorFactoryClass(NextValueMoves.class)
                    .withSelectionOrder(SelectionOrder.ORIGINAL))
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
    if (gls != null) phase.withGuidedLocalSearchConfig(gls);
    var config =
        new SolverConfig()
            .withSolutionClass(MultiHardSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(calculator)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(threads)
            .withPhases(phase);
    return (DefaultSolver<MultiHardSolution>)
        SolverFactory.<MultiHardSolution>create(config).buildSolver();
  }

  @SuppressWarnings("unchecked")
  private static GuidedLocalSearchDecider<MultiHardSolution> decider(
      DefaultSolver<MultiHardSolution> solver) {
    return (GuidedLocalSearchDecider<MultiHardSolution>)
        ((DefaultLocalSearchPhase<MultiHardSolution>) solver.getPhaseList().getFirst())
            .getDecider();
  }

  private static List<Integer> observe(
      DefaultSolver<MultiHardSolution> solver, List<BendableScore> trace) {
    var focuses = new ArrayList<Integer>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<MultiHardSolution> scope) {
            focuses.add(decider(solver).getFocusScoreLevelIndex());
          }

          @Override
          public void stepEnded(AbstractStepScope<MultiHardSolution> scope) {
            trace.add((BendableScore) scope.getScore().raw());
          }
        });
    return focuses;
  }

  private static BendableScore score(long firstHard, long secondHard, long soft) {
    return BendableScore.of(new long[] {firstHard, secondHard}, new long[] {soft});
  }

  public static class BridgeScore implements EasyScoreCalculator<MultiHardSolution, BendableScore> {
    @Override
    public BendableScore calculateScore(MultiHardSolution solution) {
      return switch (solution.getEntityList().getFirst().getValue().getCode()) {
        case "A" -> score(0, -1, -9);
        case "B" -> score(-1, -2, -8);
        case "C" -> score(0, 0, -5);
        default -> score(0, 0, 0);
      };
    }
  }

  public static class OscillatingScore
      implements EasyScoreCalculator<MultiHardSolution, BendableScore> {
    @Override
    public BendableScore calculateScore(MultiHardSolution solution) {
      return score(
          solution.getEntityList().getFirst().getValue().getCode().equals("A") ? 0 : -1, 0, 0);
    }
  }

  public static class FlatScore implements EasyScoreCalculator<MultiHardSolution, BendableScore> {
    @Override
    public BendableScore calculateScore(MultiHardSolution solution) {
      return score(0, 0, 0);
    }
  }

  public static class NextValueMoves
      implements MoveIteratorFactory<MultiHardSolution, Move<MultiHardSolution>> {
    @Override
    public long getSize(ScoreDirector<MultiHardSolution> director) {
      return 1;
    }

    @Override
    public Iterator<Move<MultiHardSolution>> createOriginalMoveIterator(
        ScoreDirector<MultiHardSolution> director) {
      var solution = director.getWorkingSolution();
      var entity = solution.getEntityList().getFirst();
      int index = solution.getValueList().indexOf(entity.getValue());
      var target = solution.getValueList().get((index + 1) % solution.getValueList().size());
      var descriptor =
          ((InnerScoreDirector<MultiHardSolution, ?>) director)
              .getSolutionDescriptor()
              .findEntityDescriptorOrFail(TestdataEntity.class)
              .getGenuineVariableDescriptor("value");
      return List.<Move<MultiHardSolution>>of(new ChangeMove<>(descriptor, entity, target))
          .iterator();
    }

    @Override
    public Iterator<Move<MultiHardSolution>> createRandomMoveIterator(
        ScoreDirector<MultiHardSolution> director, RandomGenerator random) {
      return createOriginalMoveIterator(director);
    }
  }

  @PlanningSolution
  public static class MultiHardSolution {
    private List<TestdataValue> valueList;
    private List<TestdataEntity> entityList;
    private BendableScore score;

    @ValueRangeProvider(id = "valueRange")
    @ProblemFactCollectionProperty
    public List<TestdataValue> getValueList() {
      return valueList;
    }

    public void setValueList(List<TestdataValue> valueList) {
      this.valueList = valueList;
    }

    @PlanningEntityCollectionProperty
    public List<TestdataEntity> getEntityList() {
      return entityList;
    }

    public void setEntityList(List<TestdataEntity> entityList) {
      this.entityList = entityList;
    }

    @PlanningScore(bendableHardLevelsSize = 2, bendableSoftLevelsSize = 1)
    public BendableScore getScore() {
      return score;
    }

    public void setScore(BendableScore score) {
      this.score = score;
    }
  }
}
