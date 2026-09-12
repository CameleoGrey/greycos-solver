package greycos.solver.benchmark.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import greycos.solver.benchmark.config.PlannerBenchmarkConfig;
import greycos.solver.benchmark.config.ProblemBenchmarksConfig;
import greycos.solver.benchmark.config.SolverBenchmarkConfig;
import greycos.solver.benchmark.config.statistic.SingleStatisticType;
import greycos.solver.benchmark.impl.DefaultPlannerBenchmark;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.score.TestdataHardSoftScoreSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlannerBenchmarkTest {

  @Test
  void singleStatisticScoreLevelTabsAreUniquePerSolver(@TempDir Path benchmarkTestDir)
      throws IOException {
    var inheritedSolverConfig =
        new SolverBenchmarkConfig()
            .withSolverConfig(
                new SolverConfig()
                    .withSolutionClass(TestdataHardSoftScoreSolution.class)
                    .withEntityClasses(TestdataEntity.class)
                    .withConstraintProviderClass(HardSoftConstraintProvider.class)
                    .withPhases(
                        new LocalSearchPhaseConfig()
                            .withTerminationConfig(new TerminationConfig().withStepCountLimit(2))))
            .withProblemBenchmarksConfig(
                new ProblemBenchmarksConfig()
                    .withSingleStatisticTypes(
                        SingleStatisticType.CONSTRAINT_MATCH_TOTAL_STEP_SCORE));
    var benchmarkConfig =
        new PlannerBenchmarkConfig()
            .withBenchmarkDirectory(benchmarkTestDir.toFile())
            .withWarmUpMillisecondsSpentLimit(0L)
            .withInheritedSolverBenchmarkConfig(inheritedSolverConfig)
            .withSolverBenchmarkConfigList(
                List.of(
                    new SolverBenchmarkConfig().withName("First solver"),
                    new SolverBenchmarkConfig().withName("Second solver")));
    var plannerBenchmark =
        (DefaultPlannerBenchmark)
            PlannerBenchmarkFactory.create(benchmarkConfig)
                .buildPlannerBenchmark(TestdataHardSoftScoreSolution.generateSolution(2, 2));
    plannerBenchmark.benchmark();

    var html =
        Files.readString(plannerBenchmark.getBenchmarkReport().getHtmlOverviewFile().toPath());
    var paneIds =
        Pattern.compile("id=\"(singleStatistic_[^\"]+_chart_[01]-tab-pane)\"")
            .matcher(html)
            .results()
            .map(match -> match.group(1))
            .toList();
    var buttonTargets =
        Pattern.compile("data-bs-target=\"#(singleStatistic_[^\"]+_chart_[01]-tab-pane)\"")
            .matcher(html)
            .results()
            .map(match -> match.group(1))
            .toList();
    // Both solvers need separate hard/soft panes, each selected by exactly one button.
    assertThat(paneIds).hasSize(4).doesNotHaveDuplicates();
    assertThat(buttonTargets).containsExactlyInAnyOrderElementsOf(paneIds);
  }

  public static final class HardSoftConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
      return new Constraint[] {
        constraintFactory
            .forEach(TestdataEntity.class)
            .penalize(HardSoftScore.of(1, 1))
            .asConstraint("Both score levels")
      };
    }
  }

  @Test
  void runPlannerBenchmark(@TempDir Path benchmarkTestDir) {
    var benchmarkConfig = new PlannerBenchmarkConfig();
    benchmarkConfig.setBenchmarkDirectory(benchmarkTestDir.toFile());
    benchmarkConfig.setWarmUpMillisecondsSpentLimit(1L); // Minimize warmup.
    var inheritedSolverConfig = new SolverBenchmarkConfig();
    inheritedSolverConfig.setSolverConfig(
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(TestdataConstraintProvider.class)
            // Only run for a short amount of time.
            .withTerminationConfig(
                new TerminationConfig().withUnimprovedMillisecondsSpentLimit(100L)));
    benchmarkConfig.setInheritedSolverBenchmarkConfig(inheritedSolverConfig);
    benchmarkConfig.setSolverBenchmarkConfigList(List.of(new SolverBenchmarkConfig()));
    var benchmarkFactory = PlannerBenchmarkFactory.create(benchmarkConfig);

    var solution1 = new TestdataSolution("s1");
    solution1.setEntityList(
        Arrays.asList(
            new TestdataEntity("e1"), new TestdataEntity("e2"), new TestdataEntity("e3")));
    solution1.setValueList(Arrays.asList(new TestdataValue("v1"), new TestdataValue("v2")));

    var plannerBenchmark =
        (DefaultPlannerBenchmark) benchmarkFactory.buildPlannerBenchmark(solution1);
    plannerBenchmark.benchmark(); // Run the benchmark.
    var folder = plannerBenchmark.getBenchmarkReport().getHtmlOverviewFile().toPath().getParent();
    var csv = folder.resolve(Path.of("Problem_0", "Config_0", "sub0", "BEST_SCORE.csv"));
    assertThat(csv).exists();

    try (var lines = Files.lines(csv)) {
      var lineList = lines.toList();
      assertThat(lineList).hasSizeGreaterThan(1);
      assertSoftly(
          softly -> {
            // Proper header.
            softly
                .assertThat(lineList)
                .first()
                .isEqualTo(
                    """
                                "timeMillisSpent","score","initialized"
                                """
                        .trim());
            // Checks that best score was recorded at least once.
            // Requires LS to have started, as CH does not store best score.
            // We only check score+initialized, as "timeMillisSpent" can be anything.
            softly
                .assertThat(lineList)
                .last()
                .asString()
                .endsWith(
                    """
                                "-3","true"
                                """
                        .trim());
          });
    } catch (IOException e) {
      fail(e);
    }
  }
}
