package ai.greycos.solver.benchmark.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;

import ai.greycos.solver.benchmark.config.report.BenchmarkReportConfig;
import ai.greycos.solver.benchmark.impl.report.BenchmarkReportFactory;
import ai.greycos.solver.benchmark.impl.result.BenchmarkResultIO;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class PlannerBenchmarkResultTest {

  private static final String TEST_PLANNER_BENCHMARK_RESULT = "testPlannerBenchmarkResult.xml";

  @Test
  void xmlReadBenchmarkResultAggregated() throws URISyntaxException, IOException {
    var benchmarkAggregator = new BenchmarkAggregator();
    benchmarkAggregator.setBenchmarkDirectory(
        Files.createTempDirectory(getClass().getSimpleName()).toFile());
    benchmarkAggregator.setBenchmarkReportConfig(new BenchmarkReportConfig());

    var plannerBenchmarkResultFile =
        new File(
            PlannerBenchmarkResultTest.class.getResource(TEST_PLANNER_BENCHMARK_RESULT).toURI());

    var plannerBenchmarkResult = new TestableBenchmarkResultIO().read(plannerBenchmarkResultFile);

    var benchmarkReportConfig = benchmarkAggregator.getBenchmarkReportConfig();
    var benchmarkReport =
        new BenchmarkReportFactory(benchmarkReportConfig)
            .buildBenchmarkReport(plannerBenchmarkResult);
    plannerBenchmarkResult.accumulateResults(benchmarkReport);

    var aggregatedPlannerBenchmarkResult = benchmarkReport.getPlannerBenchmarkResult();

    assertThat(aggregatedPlannerBenchmarkResult.getSolverBenchmarkResultList()).hasSize(6);
    assertThat(aggregatedPlannerBenchmarkResult.getUnifiedProblemBenchmarkResultList()).hasSize(2);
    assertThat(aggregatedPlannerBenchmarkResult.getFailureCount()).isZero();
  }

  // nested class below are used in the testPlannerBenchmarkResult.xml

  private abstract static class DummyIncrementalScoreCalculator
      implements IncrementalScoreCalculator<TestdataSolution, SimpleScore> {}

  private abstract static class DummyDistanceNearbyMeter
      implements NearbyDistanceMeter<TestdataSolution, TestdataEntity> {}

  private static final class TestableBenchmarkResultIO extends BenchmarkResultIO {

    private ai.greycos.solver.benchmark.impl.result.PlannerBenchmarkResult read(File file) {
      return super.readPlannerBenchmarkResult(file);
    }
  }
}
