package greycos.solver.benchmark.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import greycos.solver.benchmark.config.PlannerBenchmarkConfig;
import greycos.solver.benchmark.config.ProblemBenchmarksConfig;
import greycos.solver.benchmark.config.SolverBenchmarkConfig;
import greycos.solver.benchmark.config.statistic.ProblemStatisticType;
import greycos.solver.benchmark.config.statistic.SingleStatisticType;
import greycos.solver.benchmark.impl.DefaultPlannerBenchmark;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class AlnsPlannerBenchmarkTest {
  @Test
  @Timeout(30)
  void producesReportAndAlnsScoreAndMoveCsv(@TempDir Path directory) throws Exception {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(TestdataConstraintProvider.class)
            .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                new AlnsPhaseConfig()
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(4)));
    var config =
        new PlannerBenchmarkConfig()
            .withBenchmarkDirectory(directory.toFile())
            .withWarmUpMillisecondsSpentLimit(0L)
            .withSolverBenchmarkConfigList(
                List.of(
                    new SolverBenchmarkConfig()
                        .withName("ALNS")
                        .withSolverConfig(solverConfig)
                        .withProblemBenchmarksConfig(
                            new ProblemBenchmarksConfig()
                                .withProblemStatisticTypes(
                                    ProblemStatisticType.BEST_SCORE,
                                    ProblemStatisticType.STEP_SCORE,
                                    ProblemStatisticType.MOVE_COUNT_PER_STEP,
                                    ProblemStatisticType.MOVE_COUNT_PER_TYPE)
                                .withSingleStatisticTypes(
                                    SingleStatisticType.PICKED_MOVE_TYPE_STEP_SCORE_DIFF,
                                    SingleStatisticType.CONSTRAINT_MATCH_TOTAL_STEP_SCORE))));
    var benchmark =
        (DefaultPlannerBenchmark)
            PlannerBenchmarkFactory.create(config)
                .buildPlannerBenchmark(TestdataSolution.generateUninitializedSolution(3, 6));
    benchmark.benchmark();
    var htmlPath = benchmark.getBenchmarkReport().getHtmlOverviewFile().toPath();
    assertThat(htmlPath).exists();
    assertThat(Files.readString(htmlPath)).contains("ALNS");
    try (var paths = Files.walk(htmlPath.getParent())) {
      var csvFiles = paths.filter(path -> path.toString().endsWith(".csv")).toList();
      var pickedMoveCsv =
          csvFiles.stream()
              .filter(
                  path ->
                      path.getFileName().toString().equals("PICKED_MOVE_TYPE_STEP_SCORE_DIFF.csv"))
              .findFirst()
              .orElseThrow();
      assertThat(Files.readAllLines(pickedMoveCsv)).hasSizeGreaterThan(1);
      assertThat(Files.readString(pickedMoveCsv)).contains("/");
      var moveCountCsv =
          csvFiles.stream()
              .filter(path -> path.getFileName().toString().equals("MOVE_COUNT_PER_STEP.csv"))
              .findFirst()
              .orElseThrow();
      assertThat(Files.readAllLines(moveCountCsv)).hasSizeGreaterThan(1);
    }
  }
}
