package greycos.solver.benchmark.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import greycos.solver.benchmark.config.PlannerBenchmarkConfig;
import greycos.solver.benchmark.impl.DefaultPlannerBenchmark;
import greycos.solver.benchmark.impl.io.jaxb.PlannerBenchmarkConfigIO;
import greycos.solver.benchmark.impl.result.BenchmarkResultIO;
import greycos.solver.benchmark.impl.statistic.StatisticPoint;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class GeneticAlgorithmListPlannerBenchmarkTest {

  @Test
  @Timeout(60)
  void everyListMutationProducesReportStatisticsAndReloadableXmlResult(@TempDir Path directory)
      throws Exception {
    var solverBenchmarks =
        Arrays.stream(GeneticAlgorithmMutationType.values())
            .map(GeneticAlgorithmListPlannerBenchmarkTest::solverBenchmarkXml)
            .collect(Collectors.joining());
    var xml =
        """
        <plannerBenchmark xmlns="%s">
          <parallelBenchmarkCount>1</parallelBenchmarkCount>
          <warmUpMillisecondsSpentLimit>0</warmUpMillisecondsSpentLimit>
          %s
        </plannerBenchmark>
        """
            .formatted(PlannerBenchmarkConfig.XML_NAMESPACE, solverBenchmarks);
    var io = new PlannerBenchmarkConfigIO();
    var parsed = io.read(new StringReader(xml));
    var writer = new StringWriter();
    io.write(parsed, writer);
    var config = io.read(new StringReader(writer.toString()));
    assertThat(config).usingRecursiveComparison().isEqualTo(parsed);
    config.setBenchmarkDirectory(directory.toFile());

    // Nine values in three lists start at the minimum of the sum of squared list sizes.
    // This provides an independent best-score oracle for every operator and seed.
    var problem = TestdataListSolution.generateInitializedSolution(9, 3);
    var expectedScore =
        SimpleScore.of(
            -problem.getEntityList().stream()
                .mapToInt(entity -> entity.getValueList().size() * entity.getValueList().size())
                .sum());
    var benchmark =
        (DefaultPlannerBenchmark)
            PlannerBenchmarkFactory.create(config).buildPlannerBenchmark(problem);
    benchmark.benchmark();

    var htmlPath = benchmark.getBenchmarkReport().getHtmlOverviewFile().toPath();
    assertThat(htmlPath).exists();
    var report = Files.readString(htmlPath);
    var solverResults = benchmark.getPlannerBenchmarkResult().getSolverBenchmarkResultList();
    assertThat(solverResults).hasSize(GeneticAlgorithmMutationType.values().length);
    for (var solverResult : solverResults) {
      var type = GeneticAlgorithmMutationType.valueOf(solverResult.getName().substring(5));
      assertThat(report).contains(solverResult.getName());
      var runResult =
          solverResult
              .getSingleBenchmarkResultList()
              .getFirst()
              .getSubSingleBenchmarkResultList()
              .getFirst();
      assertThat(runResult.getSucceeded()).isTrue();
      assertThat(runResult.isInitialized()).isTrue();
      assertThat(runResult.getScore()).isEqualTo(expectedScore);
      assertThat(runResult.getMoveEvaluationCount()).isEqualTo(12L);
      var runDirectory = runResult.getResultDirectory().toPath();
      for (var statistic :
          List.of(
              "BEST_SCORE",
              "STEP_SCORE",
              "MOVE_COUNT_PER_STEP",
              "MOVE_COUNT_PER_TYPE",
              "PICKED_MOVE_TYPE_STEP_SCORE_DIFF",
              "CONSTRAINT_MATCH_TOTAL_STEP_SCORE")) {
        assertThat(Files.readAllLines(runDirectory.resolve(statistic + ".csv")))
            .hasSizeGreaterThan(1);
      }
      var moveCountRows = rows(runDirectory, "MOVE_COUNT_PER_STEP");
      assertThat(moveCountRows)
          .hasSize(12)
          .allSatisfy(
              row -> {
                assertThat(Long.parseLong(row.get(1))).isBetween(0L, 1L);
                assertThat(Long.parseLong(row.get(2))).isEqualTo(1L);
              });
      var moveTypeRows = rows(runDirectory, "MOVE_COUNT_PER_TYPE");
      assertThat(moveTypeRows)
          .allSatisfy(row -> assertThat(row.get(1)).startsWith("GeneticAlgorithm/"))
          .anySatisfy(row -> assertThat(row.get(1)).startsWith("GeneticAlgorithm/" + type + "/"));
      assertThat(moveTypeRows.stream().mapToLong(row -> Long.parseLong(row.get(2))).sum())
          .isEqualTo(12L);
    }

    var restored = new BenchmarkResultIO().readPlannerBenchmarkResultList(directory.toFile());
    assertThat(restored).hasSize(1);
    var restoredSolverResults = restored.getFirst().getSolverBenchmarkResultList();
    assertThat(restoredSolverResults).hasSize(GeneticAlgorithmMutationType.values().length);
    for (var solverResult : restoredSolverResults) {
      assertThat(solverResult.getSolverConfig().getSolutionClass())
          .isEqualTo(TestdataListSolution.class);
      assertThat(solverResult.getSolverConfig().getEntityClassList())
          .containsExactly(TestdataListEntity.class, TestdataListValue.class);
      assertThat(solverResult.getSolverConfig().getPhaseConfigList())
          .singleElement()
          .isInstanceOf(GeneticAlgorithmPhaseConfig.class);
      var phase =
          (GeneticAlgorithmPhaseConfig)
              solverResult.getSolverConfig().getPhaseConfigList().getFirst();
      assertThat(phase.getMoveThreadCount()).isEqualTo(SolverConfig.MOVE_THREAD_COUNT_NONE);
      assertThat(phase.getMutationOperatorConfigList())
          .singleElement()
          .satisfies(
              operator ->
                  assertThat(solverResult.getName()).isEqualTo("List " + operator.getType()));
      var runResult =
          solverResult
              .getSingleBenchmarkResultList()
              .getFirst()
              .getSubSingleBenchmarkResultList()
              .getFirst();
      assertThat(runResult.getSucceeded()).isTrue();
      assertThat(runResult.isInitialized()).isTrue();
      assertThat(runResult.getScore()).isEqualTo(expectedScore);
      assertThat(runResult.getMoveEvaluationCount()).isEqualTo(12L);
    }
    assertThat(problem.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValueList()).hasSize(3));
    assertThat(problem.getScore()).isNull();
  }

  private static List<List<String>> rows(Path directory, String statistic) throws Exception {
    return Files.readAllLines(directory.resolve(statistic + ".csv")).stream()
        .skip(1)
        .map(StatisticPoint::parseCsvLine)
        .toList();
  }

  private static String solverBenchmarkXml(GeneticAlgorithmMutationType type) {
    return """
        <solverBenchmark>
          <name>List %s</name>
          <solver>
            <environmentMode>FULL_ASSERT</environmentMode>
            <randomSeed>0</randomSeed>
            <solutionClass>%s</solutionClass>
            <entityClass>%s</entityClass>
            <entityClass>%s</entityClass>
            <scoreDirectorFactory>
              <constraintProviderClass>%s</constraintProviderClass>
            </scoreDirectorFactory>
            <geneticAlgorithm>
              <termination><stepCountLimit>12</stepCountLimit></termination>
              <populationSize>4</populationSize>
              <crossoverProbability>1.0</crossoverProbability>
              <moveThreadCount>NONE</moveThreadCount>
              <mutationOperator><type>%s</type><probability>1.0</probability></mutationOperator>
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
        """
        .formatted(
            type,
            TestdataListSolution.class.getName(),
            TestdataListEntity.class.getName(),
            TestdataListValue.class.getName(),
            ListConstraints.class.getName(),
            type);
  }

  public static final class ListConstraints implements ConstraintProvider {

    @Override
    public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataListEntity.class)
            .penalize(
                SimpleScore.ONE,
                entity -> entity.getValueList().size() * entity.getValueList().size())
            .asConstraint("List load")
      };
    }
  }
}
