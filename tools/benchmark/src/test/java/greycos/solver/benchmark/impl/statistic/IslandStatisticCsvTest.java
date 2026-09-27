package greycos.solver.benchmark.impl.statistic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import greycos.solver.benchmark.impl.result.PlannerBenchmarkResult;
import greycos.solver.benchmark.impl.result.ProblemBenchmarkResult;
import greycos.solver.benchmark.impl.result.SingleBenchmarkResult;
import greycos.solver.benchmark.impl.result.SolverBenchmarkResult;
import greycos.solver.benchmark.impl.result.SubSingleBenchmarkResult;
import greycos.solver.benchmark.impl.statistic.movecountperstep.MoveCountPerStepStatisticPoint;
import greycos.solver.benchmark.impl.statistic.movecountperstep.MoveCountPerStepSubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.stepscore.StepScoreStatisticPoint;
import greycos.solver.benchmark.impl.statistic.stepscore.StepScoreSubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.subsingle.constraintmatchtotalbestscore.ConstraintMatchTotalBestScoreStatisticPoint;
import greycos.solver.benchmark.impl.statistic.subsingle.constraintmatchtotalbestscore.ConstraintMatchTotalBestScoreSubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.subsingle.constraintmatchtotalstepscore.ConstraintMatchTotalStepScoreStatisticPoint;
import greycos.solver.benchmark.impl.statistic.subsingle.constraintmatchtotalstepscore.ConstraintMatchTotalStepScoreSubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.subsingle.pickedmovetypebestscore.PickedMoveTypeBestScoreDiffStatisticPoint;
import greycos.solver.benchmark.impl.statistic.subsingle.pickedmovetypebestscore.PickedMoveTypeBestScoreDiffSubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.subsingle.pickedmovetypestepscore.PickedMoveTypeStepScoreDiffStatisticPoint;
import greycos.solver.benchmark.impl.statistic.subsingle.pickedmovetypestepscore.PickedMoveTypeStepScoreDiffSubSingleStatistic;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IslandStatisticCsvTest {

  private static final String SOURCE = "phase-1/island-0";

  @ParameterizedTest
  @MethodSource("statistics")
  @SuppressWarnings({"rawtypes", "unchecked"})
  void sourceMetadataSurvivesHibernation(
      SubSingleStatistic statistic, StatisticPoint point, @TempDir Path outputDirectory)
      throws Exception {
    statistic.setSubSingleBenchmarkResult(result(outputDirectory));
    var rootPoint =
        statistic.createPointFromCsvLine(
            TestdataSolution.buildSolutionDescriptor().getScoreDefinition(),
            StatisticPoint.parseCsvLine(point.toCsvLine()));
    statistic.setPointList(List.of(point, rootPoint));
    var originalHeader = statistic.getCsvHeader();
    statistic.hibernatePointList();
    assertThat(Files.readAllLines(statistic.getCsvFile().toPath()).getFirst())
        .isEqualTo(originalHeader + ",\"source\"");
    statistic.unhibernatePointList();
    assertThat((List<StatisticPoint>) statistic.getPointList()).hasSize(2);
    var restored = (StatisticPoint) statistic.getPointList().getFirst();
    assertThat(restored)
        .satisfies(
            value -> {
              assertThat(value.getSource()).isEqualTo(SOURCE);
              assertThat(value.toCsvLine()).isEqualTo(point.toCsvLine());
              assertThat(value.getSeriesLabel("series")).isEqualTo("series [" + SOURCE + "]");
            });
    assertThat(((StatisticPoint) statistic.getPointList().getLast()).getSource()).isNull();
  }

  static Stream<Arguments> statistics() {
    var score = SimpleScore.of(-1);
    var constraint = ConstraintRef.of("test constraint");
    return Stream.of(
        Arguments.of(
            new StepScoreSubSingleStatistic<>(null),
            new StepScoreStatisticPoint(10, score, true, SOURCE)),
        Arguments.of(
            new MoveCountPerStepSubSingleStatistic<>(null),
            new MoveCountPerStepStatisticPoint(10, 2, 3, SOURCE)),
        Arguments.of(
            new PickedMoveTypeStepScoreDiffSubSingleStatistic<>(null),
            new PickedMoveTypeStepScoreDiffStatisticPoint(10, "ChangeMove", score, SOURCE)),
        Arguments.of(
            new PickedMoveTypeBestScoreDiffSubSingleStatistic<>(null),
            new PickedMoveTypeBestScoreDiffStatisticPoint(10, "ChangeMove", score, SOURCE)),
        Arguments.of(
            new ConstraintMatchTotalStepScoreSubSingleStatistic<>(null),
            new ConstraintMatchTotalStepScoreStatisticPoint(10, constraint, 1, score, SOURCE)),
        Arguments.of(
            new ConstraintMatchTotalBestScoreSubSingleStatistic<>(null),
            new ConstraintMatchTotalBestScoreStatisticPoint(10, constraint, 1, score, SOURCE)));
  }

  private static SubSingleBenchmarkResult result(Path directory) {
    var planner = new PlannerBenchmarkResult();
    planner.setBenchmarkReportDirectory(directory.toFile());
    var problem = new ProblemBenchmarkResult<TestdataSolution>(planner);
    problem.setName("problem");
    var solver = new SolverBenchmarkResult(planner);
    solver.setName("solver");
    solver.setScoreDefinition(TestdataSolution.buildSolutionDescriptor().getScoreDefinition());
    var single = new SingleBenchmarkResult(solver, problem);
    var subSingle = new SubSingleBenchmarkResult(single, 0);
    single.setSubSingleBenchmarkResultList(List.of(subSingle));
    problem.setSingleBenchmarkResultList(List.of(single));
    problem.makeDirs();
    return subSingle;
  }
}
