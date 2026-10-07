package greycos.solver.benchmark.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import greycos.solver.benchmark.config.PlannerBenchmarkConfig;
import greycos.solver.benchmark.impl.DefaultPlannerBenchmark;
import greycos.solver.benchmark.impl.io.jaxb.PlannerBenchmarkConfigIO;
import greycos.solver.benchmark.impl.result.BenchmarkResultIO;
import greycos.solver.benchmark.impl.statistic.StatisticPoint;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class GeneticAlgorithmPlannerBenchmarkTest {

  @Test
  @Timeout(30)
  void xmlConfiguredGeneticAlgorithmProducesReportStatisticsAndReloadableResult(
      @TempDir Path directory) throws Exception {
    var xml =
        """
        <plannerBenchmark xmlns="%s">
          <warmUpMillisecondsSpentLimit>0</warmUpMillisecondsSpentLimit>
          <solverBenchmark>
            <name>Genetic Algorithm</name>
            <solver>
              <randomSeed>0</randomSeed>
              <solutionClass>%s</solutionClass>
              <entityClass>%s</entityClass>
              <scoreDirectorFactory>
                <constraintProviderClass>%s</constraintProviderClass>
              </scoreDirectorFactory>
              <geneticAlgorithm>
                <termination><stepCountLimit>8</stepCountLimit></termination>
                <populationSize>4</populationSize>
                <mutationOperator><type>CHANGE</type><probability>1.0</probability></mutationOperator>
              </geneticAlgorithm>
            </solver>
            <problemBenchmarks>
              <problemStatisticType>BEST_SCORE</problemStatisticType>
              <problemStatisticType>STEP_SCORE</problemStatisticType>
              <problemStatisticType>MOVE_COUNT_PER_STEP</problemStatisticType>
              <problemStatisticType>MOVE_COUNT_PER_TYPE</problemStatisticType>
              <singleStatisticType>PICKED_MOVE_TYPE_STEP_SCORE_DIFF</singleStatisticType>
              <singleStatisticType>CONSTRAINT_MATCH_TOTAL_STEP_SCORE</singleStatisticType>
            </problemBenchmarks>
          </solverBenchmark>
        </plannerBenchmark>
        """
            .formatted(
                PlannerBenchmarkConfig.XML_NAMESPACE,
                TestdataSolution.class.getName(),
                TestdataEntity.class.getName(),
                TestdataConstraintProvider.class.getName());
    var io = new PlannerBenchmarkConfigIO();
    var parsed = io.read(new StringReader(xml));
    var writer = new StringWriter();
    io.write(parsed, writer);
    var config = io.read(new StringReader(writer.toString()));
    assertThat(config).usingRecursiveComparison().isEqualTo(parsed);
    config.setBenchmarkDirectory(directory.toFile());
    var benchmark =
        (DefaultPlannerBenchmark)
            PlannerBenchmarkFactory.create(config)
                .buildPlannerBenchmark(TestdataSolution.generateSolution(3, 6));
    benchmark.benchmark();
    var htmlPath = benchmark.getBenchmarkReport().getHtmlOverviewFile().toPath();
    assertThat(htmlPath).exists();
    assertThat(Files.readString(htmlPath)).contains("Genetic Algorithm");
    try (var paths = Files.walk(htmlPath.getParent())) {
      var csvFiles = paths.filter(path -> path.toString().endsWith(".csv")).toList();
      for (var statistic :
          List.of(
              "BEST_SCORE",
              "STEP_SCORE",
              "MOVE_COUNT_PER_STEP",
              "MOVE_COUNT_PER_TYPE",
              "PICKED_MOVE_TYPE_STEP_SCORE_DIFF",
              "CONSTRAINT_MATCH_TOTAL_STEP_SCORE")) {
        assertThat(Files.readAllLines(csv(csvFiles, statistic))).hasSizeGreaterThan(1);
      }
      var moveCountRows =
          Files.readAllLines(csv(csvFiles, "MOVE_COUNT_PER_STEP")).stream()
              .skip(1)
              .map(StatisticPoint::parseCsvLine)
              .toList();
      assertThat(moveCountRows)
          .hasSize(8)
          .allSatisfy(
              row -> {
                assertThat(Long.parseLong(row.get(1))).isBetween(0L, 1L);
                assertThat(Long.parseLong(row.get(2))).isEqualTo(1L);
              });
      var moveTypeRows =
          Files.readAllLines(csv(csvFiles, "MOVE_COUNT_PER_TYPE")).stream()
              .skip(1)
              .map(StatisticPoint::parseCsvLine)
              .toList();
      assertThat(moveTypeRows)
          .allSatisfy(row -> assertThat(row.get(1)).startsWith("GeneticAlgorithm/"));
      assertThat(moveTypeRows.stream().mapToLong(row -> Long.parseLong(row.get(2))).sum())
          .isEqualTo(8L);
      assertThat(Files.readString(csv(csvFiles, "PICKED_MOVE_TYPE_STEP_SCORE_DIFF")))
          .contains("GeneticAlgorithm/");
    }

    var restored = new BenchmarkResultIO().readPlannerBenchmarkResultList(directory.toFile());
    assertThat(restored).hasSize(1);
    var solverResult = restored.getFirst().getSolverBenchmarkResultList().getFirst();
    assertThat(solverResult.getSolverConfig().getPhaseConfigList())
        .singleElement()
        .isInstanceOf(GeneticAlgorithmPhaseConfig.class);
    var runResult =
        solverResult
            .getSingleBenchmarkResultList()
            .getFirst()
            .getSubSingleBenchmarkResultList()
            .getFirst();
    assertThat(runResult.getMoveEvaluationCount()).isEqualTo(8L);
    assertThat(runResult.getScore()).isEqualTo(SimpleScore.of(-6));
  }

  private static Path csv(List<Path> csvFiles, String statistic) {
    return csvFiles.stream()
        .filter(path -> path.getFileName().toString().equals(statistic + ".csv"))
        .findFirst()
        .orElseThrow();
  }
}
