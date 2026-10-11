package greycos.solver.greybay.examples;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import greycos.solver.benchmark.api.PlannerBenchmarkFactory;
import greycos.solver.benchmark.config.PlannerBenchmarkConfig;
import greycos.solver.benchmark.config.SolverBenchmarkConfig;
import greycos.solver.core.config.solver.SolverConfig;

/** Optional ordinary benchmark rerun. Its scores never enter the Greybay study journal. */
public final class ExportedBenchmarkExample {
  private ExportedBenchmarkExample() {}

  public static PlannerBenchmarkConfig configuration(
      SolverConfig baseline, SolverConfig selected, List<Long> seeds, Path output) {
    if (seeds.isEmpty() || seeds.stream().distinct().count() != seeds.size()) {
      throw new IllegalArgumentException("Benchmark seeds must be nonempty and distinct.");
    }
    var entries = new ArrayList<SolverBenchmarkConfig>();
    for (var seed : seeds) {
      entries.add(
          new SolverBenchmarkConfig()
              .withName("baseline-seed-" + seed)
              .withSubSingleCount(1)
              .withSolverConfig(baseline.copyConfig().withRandomSeed(seed)));
      entries.add(
          new SolverBenchmarkConfig()
              .withName("selected-seed-" + seed)
              .withSubSingleCount(1)
              .withSolverConfig(selected.copyConfig().withRandomSeed(seed)));
    }
    return new PlannerBenchmarkConfig()
        .withBenchmarkDirectory(output.toFile())
        .withParallelBenchmarkCount("1")
        .withWarmUpMillisecondsSpentLimit(0L)
        .withSolverBenchmarkConfigList(entries);
  }

  public static void main(String[] args) {
    if (args.length != 4) {
      throw new IllegalArgumentException(
          "Usage: ExportedBenchmarkExample <scheduling|routing> <selected-solver.xml>"
              + " <new-benchmark-directory> <comma-separated-seeds>");
    }
    var selected = SolverConfig.createFromXmlFile(Path.of(args[1]).toFile());
    var seeds = Arrays.stream(args[3].split(",")).map(Long::parseLong).toList();
    var baseline =
        switch (args[0]) {
          case "scheduling" -> SchedulingExample.baseSolverConfig();
          case "routing" -> RoutingExample.baseSolverConfig();
          default -> throw new IllegalArgumentException("Unknown family (" + args[0] + ").");
        };
    if (!baseline.getSolutionClass().equals(selected.getSolutionClass())) {
      throw new IllegalArgumentException(
          "Selected configuration does not belong to family (" + args[0] + ").");
    }
    baseline.setTerminationConfig(selected.getTerminationConfig().copyConfig());
    baseline.setMoveThreadCount(selected.getMoveThreadCount());
    baseline.setEnvironmentMode(selected.getEnvironmentMode());
    var config = configuration(baseline, selected, seeds, Path.of(args[2]));
    var adapter =
        args[0].equals("scheduling")
            ? SchedulingExample.problemAdapter()
            : RoutingExample.problemAdapter();
    var problems = ExampleData.HELD_OUT_IDS.stream().map(adapter::loadFresh).toArray();
    var report = PlannerBenchmarkFactory.create(config).buildPlannerBenchmark(problems).benchmark();
    System.out.println(
        "Supplemental benchmark report: "
            + report
            + ". These returned-final-score reruns have separate cost and do not replace qualification evidence.");
  }
}
