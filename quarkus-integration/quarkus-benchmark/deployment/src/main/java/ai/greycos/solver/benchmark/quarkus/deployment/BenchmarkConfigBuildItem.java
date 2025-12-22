package ai.greycos.solver.benchmark.quarkus.deployment;

import ai.greycos.solver.benchmark.config.PlannerBenchmarkConfig;

import io.quarkus.builder.item.SimpleBuildItem;

public final class BenchmarkConfigBuildItem extends SimpleBuildItem {
  private final PlannerBenchmarkConfig benchmarkConfig;

  public BenchmarkConfigBuildItem(PlannerBenchmarkConfig benchmarkConfig) {
    this.benchmarkConfig = benchmarkConfig;
  }

  public PlannerBenchmarkConfig getBenchmarkConfig() {
    return benchmarkConfig;
  }
}
