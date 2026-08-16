package greycos.solver.benchmark.impl.ranking;

import static greycos.solver.core.testutil.PlannerAssert.assertCompareToOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import greycos.solver.benchmark.impl.result.ProblemBenchmarkResult;
import greycos.solver.benchmark.impl.result.SingleBenchmarkResult;
import greycos.solver.benchmark.impl.result.SolverBenchmarkResult;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;

import org.junit.jupiter.api.Test;

class TotalScoreSingleBenchmarkRankingComparatorTest {

  @Test
  void compareTo() {
    var solverBenchmarkResult = mock(SolverBenchmarkResult.class);
    when(solverBenchmarkResult.getScoreDefinition()).thenReturn(new SimpleScoreDefinition());
    var comparator = new TotalScoreSingleBenchmarkRankingComparator();
    var a = new SingleBenchmarkResult(solverBenchmarkResult, mock(ProblemBenchmarkResult.class));
    a.setFailureCount(1);
    a.setAverageAndTotalScoreForTesting(null, false);
    var b = new SingleBenchmarkResult(solverBenchmarkResult, mock(ProblemBenchmarkResult.class));
    b.setFailureCount(0);
    b.setAverageAndTotalScoreForTesting(SimpleScore.of(-1), false);
    var c = new SingleBenchmarkResult(solverBenchmarkResult, mock(ProblemBenchmarkResult.class));
    c.setFailureCount(0);
    c.setAverageAndTotalScoreForTesting(SimpleScore.of(-300), true);
    when(solverBenchmarkResult.getScoreDefinition()).thenReturn(new SimpleScoreDefinition());
    var d = new SingleBenchmarkResult(solverBenchmarkResult, mock(ProblemBenchmarkResult.class));
    d.setFailureCount(0);
    d.setAverageAndTotalScoreForTesting(SimpleScore.of(-20), true);
    assertCompareToOrder(comparator, a, b, c, d);
  }
}
