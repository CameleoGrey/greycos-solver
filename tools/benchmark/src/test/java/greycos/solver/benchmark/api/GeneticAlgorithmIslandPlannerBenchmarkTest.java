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
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeneticAlgorithmIslandPlannerBenchmarkTest {

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 0.5})
  @Timeout(30)
  void xmlConfiguredIslandsPreserveMigrationRateReportWorkAndReloadResults(
      double migrationRate, @TempDir Path directory) throws Exception {
    var xml =
        """
        <plannerBenchmark xmlns="%s">
          <warmUpMillisecondsSpentLimit>0</warmUpMillisecondsSpentLimit>
          <solverBenchmark>
            <name>Genetic Algorithm Islands</name>
            <solver>
              <randomSeed>0</randomSeed>
              <solutionClass>%s</solutionClass>
              <entityClass>%s</entityClass>
              <scoreDirectorFactory>
                <constraintProviderClass>%s</constraintProviderClass>
              </scoreDirectorFactory>
              <islandModel>
                <islandCount>2</islandCount>
                <migrationFrequency>1</migrationFrequency>
                <geneticAlgorithm>
                  <termination><stepCountLimit>11</stepCountLimit></termination>
                  <populationSize>4</populationSize>
                  <migrationRate>%s</migrationRate>
                  <moveThreadCount>NONE</moveThreadCount>
                  <mutationOperator><type>CHANGE</type><probability>1.0</probability></mutationOperator>
                </geneticAlgorithm>
              </islandModel>
            </solver>
            <problemBenchmarks>
              <problemStatisticType>BEST_SCORE</problemStatisticType>
              <problemStatisticType>STEP_SCORE</problemStatisticType>
              <problemStatisticType>MOVE_COUNT_PER_STEP</problemStatisticType>
              <problemStatisticType>MOVE_COUNT_PER_TYPE</problemStatisticType>
            </problemBenchmarks>
          </solverBenchmark>
        </plannerBenchmark>
        """
            .formatted(
                PlannerBenchmarkConfig.XML_NAMESPACE,
                TestdataSolution.class.getName(),
                TestdataEntity.class.getName(),
                TestdataConstraintProvider.class.getName(),
                migrationRate);
    var io = new PlannerBenchmarkConfigIO();
    var parsed = io.read(new StringReader(xml));
    var writer = new StringWriter();
    io.write(parsed, writer);
    var config = io.read(new StringReader(writer.toString()));
    assertThat(config).usingRecursiveComparison().isEqualTo(parsed);
    assertMigrationConfiguration(
        config.getSolverBenchmarkConfigList().getFirst().getSolverConfig(), migrationRate);
    config.setBenchmarkDirectory(directory.toFile());
    var benchmark =
        (DefaultPlannerBenchmark)
            PlannerBenchmarkFactory.create(config)
                .buildPlannerBenchmark(TestdataSolution.generateSolution(3, 6));

    benchmark.benchmark();

    var htmlPath = benchmark.getBenchmarkReport().getHtmlOverviewFile().toPath();
    assertThat(htmlPath).exists();
    assertThat(Files.readString(htmlPath)).contains("Genetic Algorithm Islands");
    try (var paths = Files.walk(htmlPath.getParent())) {
      var csvFiles = paths.filter(path -> path.toString().endsWith(".csv")).toList();
      for (var statistic : List.of("BEST_SCORE", "STEP_SCORE", "MOVE_COUNT_PER_TYPE")) {
        assertThat(Files.readAllLines(csv(csvFiles, statistic))).hasSizeGreaterThan(1);
      }
      var moveRows =
          Files.readAllLines(csv(csvFiles, "MOVE_COUNT_PER_STEP")).stream()
              .skip(1)
              .map(StatisticPoint::parseCsvLine)
              .toList();
      assertThat(moveRows)
          .hasSize(22)
          .allSatisfy(row -> assertThat(Long.parseLong(row.get(2))).isEqualTo(1L));
      assertThat(moveRows.stream().map(List::getLast))
          .contains("phase-0/island-0", "phase-0/island-1");
      assertThat(moveRows.stream().filter(row -> row.getLast().equals("phase-0/island-0")))
          .hasSize(11);
      assertThat(moveRows.stream().filter(row -> row.getLast().equals("phase-0/island-1")))
          .hasSize(11);
      var moveTypeRows =
          Files.readAllLines(csv(csvFiles, "MOVE_COUNT_PER_TYPE")).stream()
              .skip(1)
              .map(StatisticPoint::parseCsvLine)
              .toList();
      assertThat(moveTypeRows)
          .allSatisfy(row -> assertThat(row.get(1)).startsWith("GeneticAlgorithm/"));
      assertThat(moveTypeRows.stream().mapToLong(row -> Long.parseLong(row.get(2))).sum())
          .isEqualTo(22L);
    }

    var restored = new BenchmarkResultIO().readPlannerBenchmarkResultList(directory.toFile());
    assertThat(restored).hasSize(1);
    var solverResult = restored.getFirst().getSolverBenchmarkResultList().getFirst();
    assertMigrationConfiguration(solverResult.getSolverConfig(), migrationRate);
    var runResult =
        solverResult
            .getSingleBenchmarkResultList()
            .getFirst()
            .getSubSingleBenchmarkResultList()
            .getFirst();
    assertThat(runResult.getMoveEvaluationCount()).isEqualTo(22L);
    assertThat(runResult.getScoreCalculationCount()).isPositive();
    assertThat(runResult.getScore()).isEqualTo(SimpleScore.of(-6));
  }

  private static void assertMigrationConfiguration(SolverConfig config, double migrationRate) {
    assertThat(config.getPhaseConfigList())
        .singleElement()
        .isInstanceOf(IslandModelPhaseConfig.class);
    var island = (IslandModelPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(island.getIslandCount()).isEqualTo(2);
    assertThat(island.getMigrationFrequency()).isEqualTo(1);
    assertThat(island.getPhaseConfigList())
        .singleElement()
        .isInstanceOf(GeneticAlgorithmPhaseConfig.class);
    var ga = (GeneticAlgorithmPhaseConfig) island.getPhaseConfigList().getFirst();
    assertThat(ga.getMigrationRate()).isEqualTo(migrationRate);
    assertThat(ga.getMoveThreadCount()).isEqualTo(SolverConfig.MOVE_THREAD_COUNT_NONE);
  }

  private static Path csv(List<Path> csvFiles, String statistic) {
    return csvFiles.stream()
        .filter(path -> path.getFileName().toString().equals(statistic + ".csv"))
        .findFirst()
        .orElseThrow();
  }
}
