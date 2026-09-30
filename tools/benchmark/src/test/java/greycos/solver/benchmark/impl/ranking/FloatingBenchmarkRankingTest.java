package greycos.solver.benchmark.impl.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import greycos.solver.benchmark.impl.result.SingleBenchmarkResult;
import greycos.solver.benchmark.impl.result.SolverBenchmarkResult;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.impl.score.definition.SimpleDoubleScoreDefinition;

import org.junit.jupiter.api.Test;

class FloatingBenchmarkRankingTest {

  @Test
  void mixedNativeAndWidenedScoresCompareTheirExactValues() {
    var comparator = new ResilientScoreComparator(new SimpleDoubleScoreDefinition());
    var binaryFraction = SimpleDoubleScore.of(0.1);
    assertThat(comparator.compare(binaryFraction, SimpleBigDecimalScore.of(new BigDecimal("0.1"))))
        .isPositive();
    var exactFraction = SimpleBigDecimalScore.of(new BigDecimal(0.1));
    assertThat(comparator.compare(binaryFraction, exactFraction)).isZero();
    assertThat(comparator.compare(exactFraction, binaryFraction)).isZero();
    assertThat(comparator.compare(SimpleFloatScore.of(0.1f), binaryFraction)).isPositive();
    assertThat(
            comparator.compare(
                new SimpleDoubleScore(-1, Double.MAX_VALUE),
                SimpleBigDecimalScore.of(BigDecimal.ZERO)))
        .isNegative();
  }

  @Test
  void widenedTotalsBeyondDoubleRangeRemainOrderedForSingleAndSolverRankings() {
    var twiceMaximum = new BigDecimal(Double.MAX_VALUE).multiply(BigDecimal.valueOf(2));
    var low = SimpleBigDecimalScore.of(twiceMaximum);
    var high = SimpleBigDecimalScore.of(twiceMaximum.add(BigDecimal.ONE));
    var definition = new SimpleDoubleScoreDefinition();
    var lowSolver = mock(SolverBenchmarkResult.class);
    var highSolver = mock(SolverBenchmarkResult.class);
    when(lowSolver.getScoreDefinition()).thenReturn(definition);
    when(highSolver.getScoreDefinition()).thenReturn(definition);
    when(lowSolver.getFailureCount()).thenReturn(0);
    when(highSolver.getFailureCount()).thenReturn(0);
    when(lowSolver.getTotalScore()).thenReturn(low);
    when(highSolver.getTotalScore()).thenReturn(high);
    assertThat(new TotalScoreSolverRankingComparator().compare(lowSolver, highSolver)).isNegative();
    assertThat(new TotalScoreSolverRankingComparator().compare(highSolver, lowSolver)).isPositive();
    var lowSingle = single(lowSolver, low);
    var highSingle = single(highSolver, high);
    assertThat(new TotalScoreSingleBenchmarkRankingComparator().compare(lowSingle, highSingle))
        .isNegative();
  }

  private static SingleBenchmarkResult single(SolverBenchmarkResult solver, Score<?> score) {
    var result = new SingleBenchmarkResult(solver, null);
    result.setFailureCount(0);
    result.setAverageAndTotalScoreForTesting(score, true);
    return result;
  }
}
