package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.RuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Opt-in synthetic comparison. Outputs observations without asserting search-quality superiority.
 */
@EnabledIfSystemProperty(named = "greycos.alns.benchmark", matches = "true")
@Execution(ExecutionMode.SAME_THREAD)
class AlnsComparisonBenchmarkTest {
  @Test
  void compareEqualWallTimeAcrossSeeds() throws Exception {
    long millis = Long.getLong("greycos.alns.benchmark.millis", 1000L);
    int seeds = Integer.getInteger("greycos.alns.benchmark.seeds", 3);
    if (millis < 1 || seeds < 1) {
      throw new IllegalArgumentException("Benchmark duration and seed count must be positive.");
    }
    var rows = new ArrayList<String>();
    rows.add(
        "model,algorithm,seed,budgetMillis,elapsedMillis,bestScore,scoreCalculations,outerMoves,heapUsedBytesAfter");
    // Warm every algorithm/domain combination before collecting observations.
    for (var model : List.of("basic", "list", "mixed")) {
      for (var algorithm : List.of("LS", "LS_RR", "ALNS")) {
        run(model, algorithm, -1, 100);
      }
    }
    for (int seed = 0; seed < seeds; seed++) {
      for (var model : List.of("basic", "list", "mixed")) {
        // Rotate execution order to reduce systematic ordering effects.
        var algorithms = List.of("LS", "LS_RR", "ALNS");
        for (int offset = 0; offset < algorithms.size(); offset++) {
          rows.add(run(model, algorithms.get((seed + offset) % algorithms.size()), seed, millis));
        }
      }
    }
    var output = Path.of("target", "alns-comparison.csv");
    Files.createDirectories(output.getParent());
    Files.write(output, rows);
    Files.writeString(Path.of("target", "alns-comparison.html"), report(rows));
    rows.forEach(System.out::println);
  }

  private static String run(String model, String algorithm, long seed, long millis) {
    var config =
        new SolverConfig()
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withMoveThreadCount("NONE")
            .withRandomSeed(seed)
            .withPhases(phase(model, algorithm, millis));
    if (model.equals("mixed")) {
      config
          .withSolutionClass(TestdataMixedSolution.class)
          .withEntityClasses(
              TestdataMixedEntity.class, TestdataMixedValue.class, TestdataMixedOtherValue.class)
          .withEasyScoreCalculatorClass(MixedScoreCalculator.class);
      var problem = TestdataMixedSolution.generateUninitializedSolution(6, 48, 8);
      for (int i = 0; i < problem.getEntityList().size(); i++) {
        problem.getEntityList().get(i).setBasicValue(problem.getOtherValueList().get(i % 8));
        problem
            .getEntityList()
            .get(i)
            .setSecondBasicValue(problem.getOtherValueList().get((i + 2) % 8));
      }
      for (int i = 0; i < problem.getValueList().size(); i++) {
        problem.getEntityList().get(i % 6).getValueList().add(problem.getValueList().get(i));
      }
      SolutionManager.updateShadowVariables(problem);
      var solver =
          (DefaultSolver<TestdataMixedSolution>)
              SolverFactory.<TestdataMixedSolution>create(config).buildSolver();
      long started = System.nanoTime();
      var result = solver.solve(problem);
      long elapsed = (System.nanoTime() - started) / 1_000_000L;
      assertThat(result.getScore()).isEqualTo(new MixedScoreCalculator().calculateScore(result));
      assertThat(
              result.getEntityList().stream()
                  .flatMap(entity -> entity.getValueList().stream())
                  .toList())
          .containsExactlyInAnyOrderElementsOf(result.getValueList());
      assertThat(result.getEntityList())
          .allSatisfy(
              entity -> {
                assertThat(entity.getBasicValue()).isNotNull();
                assertThat(entity.getSecondBasicValue()).isNotNull();
              });
      return row(model, algorithm, seed, millis, elapsed, result.getScore(), solver);
    }
    if (model.equals("list")) {
      config
          .withSolutionClass(TestdataListSolution.class)
          .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
          .withEasyScoreCalculatorClass(RouteScoreCalculator.class);
      var problem = TestdataListSolution.generateInitializedSolution(48, 6);
      var solver =
          (DefaultSolver<TestdataListSolution>)
              SolverFactory.<TestdataListSolution>create(config).buildSolver();
      long started = System.nanoTime();
      var result = solver.solve(problem);
      long elapsed = (System.nanoTime() - started) / 1_000_000L;
      assertThat(result.getScore()).isEqualTo(new RouteScoreCalculator().calculateScore(result));
      assertThat(
              result.getEntityList().stream()
                  .flatMap(entity -> entity.getValueList().stream())
                  .toList())
          .containsExactlyInAnyOrderElementsOf(result.getValueList());
      return row("list", algorithm, seed, millis, elapsed, result.getScore(), solver);
    }
    config
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(AssignmentScoreCalculator.class);
    var problem = TestdataSolution.generateUninitializedSolution(8, 80);
    problem.getEntityList().forEach(entity -> entity.setValue(problem.getValueList().getFirst()));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    long started = System.nanoTime();
    var result = solver.solve(problem);
    long elapsed = (System.nanoTime() - started) / 1_000_000L;
    assertThat(result.getScore()).isEqualTo(new AssignmentScoreCalculator().calculateScore(result));
    return row("basic", algorithm, seed, millis, elapsed, result.getScore(), solver);
  }

  private static String row(
      String model,
      String algorithm,
      long seed,
      long budget,
      long elapsed,
      SimpleScore score,
      DefaultSolver<?> solver) {
    return model
        + ","
        + algorithm
        + ","
        + seed
        + ","
        + budget
        + ","
        + elapsed
        + ","
        + score
        + ","
        + solver.getScoreCalculationCount()
        + ","
        + solver.getMoveEvaluationCount()
        + ","
        + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
  }

  private static PhaseConfig<?> phase(String model, String algorithm, long millis) {
    var termination = new TerminationConfig().withSpentLimit(Duration.ofMillis(millis));
    if (algorithm.equals("ALNS")) {
      return new AlnsPhaseConfig().withTerminationConfig(termination);
    }
    List<MoveSelectorConfig> fine =
        model.equals("list")
            ? List.of(new ListChangeMoveSelectorConfig(), new ListSwapMoveSelectorConfig())
            : List.of(new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig());
    if (model.equals("mixed")) {
      fine =
          List.of(
              new ChangeMoveSelectorConfig(),
              new SwapMoveSelectorConfig(),
              new ListChangeMoveSelectorConfig(),
              new ListSwapMoveSelectorConfig());
    }
    MoveSelectorConfig selector = new UnionMoveSelectorConfig(fine);
    if (algorithm.equals("LS_RR")) {
      selector.withFixedProbabilityWeight(100.0);
      MoveSelectorConfig ruin =
          model.equals("list")
              ? new ListRuinRecreateMoveSelectorConfig()
              : new RuinRecreateMoveSelectorConfig();
      if (model.equals("mixed")) {
        ruin =
            new UnionMoveSelectorConfig(
                List.of(
                    new RuinRecreateMoveSelectorConfig().withVariableName("basicValue"),
                    new RuinRecreateMoveSelectorConfig().withVariableName("secondBasicValue"),
                    new ListRuinRecreateMoveSelectorConfig()));
      }
      ruin.withFixedProbabilityWeight(1.0);
      selector = new UnionMoveSelectorConfig(List.of(selector, ruin));
    }
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(LocalSearchType.LATE_ACCEPTANCE)
        .withMoveSelectorConfig(selector)
        .withTerminationConfig(termination);
  }

  private static String report(List<String> rows) {
    var html =
        new StringBuilder(
            """
        <!doctype html><html lang="en"><meta charset="utf-8"><title>ALNS comparison</title>
        <style>body{font:15px system-ui;max-width:1200px;margin:40px auto;padding:0 20px;color:#172330}
        table{border-collapse:collapse;width:100%;margin:24px 0}th,td{padding:9px 12px;text-align:right;border-bottom:1px solid #ddd}
        th{background:#edf2f7}td:first-child,th:first-child{text-align:left}tr:hover{background:#f8fafc}
        .note{line-height:1.6;color:#46566a}h1{font-size:28px}</style>
        <h1>ALNS comparison</h1><p class="note">Synthetic basic assignment, list routing, and mixed-variable workloads.
        Every algorithm uses the same phase time budget for a given row, initialized input, and sequential execution.
        Higher scores are better. Each result was checked using a fresh business-score calculation.
        Heap is sampled after solve and result validation; it includes retained JVM allocations and excludes process RSS.
        These measurements describe this workload and do not establish production performance.</p>
        <h2>Mean observations by configuration</h2><table><thead><tr><th>Model</th><th>Algorithm</th>
        <th>Runs</th><th>Mean score</th><th>Mean elapsed ms</th><th>Mean score calculations</th><th>Mean outer moves</th><th>Mean heap MiB</th></tr></thead><tbody>
        """);
    for (var model : List.of("basic", "list", "mixed")) {
      for (var algorithm : List.of("LS", "LS_RR", "ALNS")) {
        var samples =
            rows.stream()
                .skip(1)
                .map(row -> row.split(","))
                .filter(columns -> columns[0].equals(model) && columns[1].equals(algorithm))
                .toList();
        html.append("<tr><td>")
            .append(model)
            .append("</td><td>")
            .append(algorithm)
            .append("</td><td>")
            .append(samples.size())
            .append("</td>");
        for (int column : new int[] {5, 4, 6, 7, 8}) {
          double average =
              samples.stream()
                  .mapToDouble(columns -> Double.parseDouble(columns[column]))
                  .average()
                  .orElse(0);
          if (column == 8) average /= 1024.0 * 1024.0;
          html.append("<td>")
              .append(String.format(java.util.Locale.ROOT, "%.2f", average))
              .append("</td>");
        }
        html.append("</tr>");
      }
    }
    html.append("</tbody></table><h2>All runs</h2><table>");
    for (int i = 0; i < rows.size(); i++) {
      html.append("<tr>");
      var cell = i == 0 ? "th" : "td";
      for (var value : rows.get(i).split(",")) {
        html.append("<")
            .append(cell)
            .append(">")
            .append(value)
            .append("</")
            .append(cell)
            .append(">");
      }
      html.append("</tr>");
    }
    return html.append("</table></html>").toString();
  }

  public static final class AssignmentScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      var indices = new IdentityHashMap<TestdataValue, Integer>();
      for (int i = 0; i < solution.getValueList().size(); i++) {
        indices.put(solution.getValueList().get(i), i);
      }
      int[] loads = new int[indices.size()];
      int penalty = 0;
      for (int i = 0; i < solution.getEntityList().size(); i++) {
        var value = solution.getEntityList().get(i).getValue();
        if (value == null) {
          penalty += 1000;
          continue;
        }
        int valueIndex = indices.get(value);
        loads[valueIndex]++;
        int preferred = (i * 17 + i / 7) % loads.length;
        penalty += 3 * Math.abs(preferred - valueIndex);
        for (int distance = 1; distance <= 3 && i >= distance; distance++) {
          if (solution.getEntityList().get(i - distance).getValue() == value) {
            penalty += 13 - distance;
          }
        }
      }
      for (int load : loads) {
        penalty += load * load;
      }
      return SimpleScore.of(-penalty);
    }
  }

  public static final class RouteScoreCalculator
      implements EasyScoreCalculator<TestdataListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataListSolution solution) {
      var indices = new IdentityHashMap<TestdataListValue, Integer>();
      for (int i = 0; i < solution.getValueList().size(); i++) {
        indices.put(solution.getValueList().get(i), i + 1);
      }
      int penalty = 0;
      for (var entity : solution.getEntityList()) {
        int previousX = 0;
        int previousY = 0;
        for (var value : entity.getValueList()) {
          int index = indices.get(value);
          int x = index * 37 % 101;
          int y = index * 53 % 103;
          penalty += Math.abs(x - previousX) + Math.abs(y - previousY);
          previousX = x;
          previousY = y;
        }
        penalty +=
            previousX + previousY + 5 * entity.getValueList().size() * entity.getValueList().size();
      }
      return SimpleScore.of(-penalty);
    }
  }

  public static final class MixedScoreCalculator
      implements EasyScoreCalculator<TestdataMixedSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataMixedSolution solution) {
      var indices = new IdentityHashMap<TestdataMixedValue, Integer>();
      for (int i = 0; i < solution.getValueList().size(); i++) {
        indices.put(solution.getValueList().get(i), i + 1);
      }
      int penalty = 0;
      for (var entity : solution.getEntityList()) {
        int first = entity.getBasicValue() == null ? 0 : entity.getBasicValue().getStrength();
        int second =
            entity.getSecondBasicValue() == null ? 0 : entity.getSecondBasicValue().getStrength();
        if (entity.getBasicValue() == null || entity.getSecondBasicValue() == null) penalty += 1000;
        int previousX = 0;
        int previousY = 0;
        for (var value : entity.getValueList()) {
          int index = indices.get(value);
          int x = index * 37 % 101;
          int y = index * 53 % 103;
          penalty +=
              Math.abs(x - previousX)
                  + Math.abs(y - previousY)
                  + Math.abs(first - (40 + index % 8))
                  + Math.abs(second - (40 + index * 3 % 8));
          previousX = x;
          previousY = y;
        }
        penalty +=
            previousX + previousY + 5 * entity.getValueList().size() * entity.getValueList().size();
      }
      return SimpleScore.of(-penalty);
    }
  }
}
