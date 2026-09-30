package greycos.solver.benchmark.impl.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import greycos.solver.benchmark.impl.report.BenchmarkReport;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.definition.ScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleDoubleScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleFloatScoreDefinition;

import org.junit.jupiter.api.Test;

class FloatingBenchmarkResultTest {

  @Test
  void repeatedMaximumRunsHaveAFiniteNativeAverage() {
    for (var maximum :
        List.<Score<?>>of(
            SimpleFloatScore.of(Float.MAX_VALUE), SimpleDoubleScore.of(Double.MAX_VALUE))) {
      var solver = solver(maximum);
      var result = single(solver, maximum, maximum);
      result.accumulateResults(null);
      assertThat(result.getAverageScore())
          .isExactlyInstanceOf(maximum.getClass())
          .isEqualTo(maximum);
      assertThat(result.getTotalScore()).isInstanceOf(SimpleBigDecimalScore.class);
      assertThat(((SimpleBigDecimalScore) result.getTotalScore()).score())
          .isEqualByComparingTo(
              FloatingBenchmarkScoreArithmetic.decimal(maximum.toLevelNumbers()[0])
                  .multiply(BigDecimal.valueOf(2)));
    }
  }

  @Test
  void solverAveragePreservesLowBitsAcrossProblems() {
    for (var maximum :
        List.<Score<?>>of(
            SimpleFloatScore.of(Float.MAX_VALUE), SimpleDoubleScore.of(Double.MAX_VALUE))) {
      var solver = solver(maximum);
      boolean floatPrecision = maximum instanceof SimpleFloatScore;
      var one = floatPrecision ? SimpleFloatScore.of(1) : SimpleDoubleScore.of(1);
      var minimum =
          floatPrecision
              ? SimpleFloatScore.of(-Float.MAX_VALUE)
              : SimpleDoubleScore.of(-Double.MAX_VALUE);
      var results = new ArrayList<SingleBenchmarkResult>();
      for (var score : List.of(maximum, one, one, minimum)) {
        var result = single(solver, score);
        result.accumulateResults(null);
        result.setWinningScoreDifference(FloatingBenchmarkScoreArithmetic.difference(score, score));
        result.setWorstScoreDifferencePercentage(
            new ScoreDifferencePercentage(new double[] {Double.MAX_VALUE}));
        result.setWorstScoreCalculationSpeedDifferencePercentage(0.0);
        results.add(result);
      }
      solver.setSingleBenchmarkResultList(results);
      solver.accumulateResults(null);
      assertThat(solver.getAverageScore()).isExactlyInstanceOf(maximum.getClass());
      assertThat(solver.getAverageScore().toLevelNumbers()[0].doubleValue()).isEqualTo(0.5);
      assertThat(((SimpleBigDecimalScore) solver.getTotalScore()).score())
          .isEqualByComparingTo("2");
      assertThat(solver.getAverageWorstScoreDifferencePercentage().percentageLevels())
          .containsExactly(Double.MAX_VALUE);
    }
  }

  @Test
  void plannerCompatibilityUsesTheOriginalTypeInsteadOfTheWidenedTotal() {
    for (var maximum :
        List.<Score<?>>of(
            SimpleFloatScore.of(Float.MAX_VALUE), SimpleDoubleScore.of(Double.MAX_VALUE))) {
      var planner = new PlannerBenchmarkResult();
      var a = mock(SolverBenchmarkResult.class);
      var b = mock(SolverBenchmarkResult.class);
      for (var result : List.of(a, b)) {
        when(result.getAverageScore()).thenReturn(maximum);
        when(result.getScoreDefinition()).thenReturn(definition(maximum));
        when(result.getEnvironmentMode()).thenReturn(EnvironmentMode.NO_ASSERT);
      }
      planner.setSolverBenchmarkResultList(List.of(a, b));
      planner.setUnifiedProblemBenchmarkResultList(List.of());
      var report = mock(BenchmarkReport.class);
      when(report.getSolverRankingComparator()).thenReturn((left, right) -> 0);
      planner.accumulateResults(report);
      assertThat(planner.getAverageScore())
          .isExactlyInstanceOf(maximum.getClass())
          .isEqualTo(maximum);
    }
  }

  @Test
  void problemWinningDifferencesSpanTheWholeFiniteRange() {
    var maximum = SimpleDoubleScore.of(Double.MAX_VALUE);
    var minimum = SimpleDoubleScore.of(-Double.MAX_VALUE);
    var solver = solver(maximum);
    var problem = new ProblemBenchmarkResult<>(null);
    var winner = single(solver, maximum);
    var loser = single(solver, minimum);
    problem.setSingleBenchmarkResultList(List.of(winner, loser));
    problem.accumulateResults(null);
    var difference = (SimpleBigDecimalScore) loser.getWinningScoreDifference();
    assertThat(difference.score())
        .isEqualByComparingTo(new BigDecimal(Double.MAX_VALUE).multiply(BigDecimal.valueOf(-2)));
    assertThat(winner.getWorstScoreDifferencePercentage().percentageLevels()).containsExactly(2.0);
  }

  private static SolverBenchmarkResult solver(Score<?> score) {
    var result = new SolverBenchmarkResult(null);
    result.setScoreDefinition(definition(score));
    return result;
  }

  private static ScoreDefinition<?> definition(Score<?> score) {
    return score instanceof SimpleFloatScore
        ? new SimpleFloatScoreDefinition()
        : new SimpleDoubleScoreDefinition();
  }

  private static SingleBenchmarkResult single(SolverBenchmarkResult solver, Score<?>... scores) {
    var result = new SingleBenchmarkResult(solver, new ProblemBenchmarkResult<>(null));
    var children = new ArrayList<SubSingleBenchmarkResult>();
    for (int i = 0; i < scores.length; i++) {
      var child = new SubSingleBenchmarkResult(result, i);
      child.setSucceeded(true);
      child.setScore(scores[i], true);
      child.setTimeMillisSpent(1);
      child.setScoreCalculationCount(1);
      child.setMoveEvaluationCount(1);
      children.add(child);
    }
    result.setSubSingleBenchmarkResultList(children);
    return result;
  }
}
