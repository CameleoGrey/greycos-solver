package ai.greycos.solver.benchmark.quarkus;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import ai.greycos.solver.benchmark.api.PlannerBenchmarkFactory;
import ai.greycos.solver.benchmark.config.PlannerBenchmarkConfig;

import io.quarkus.arc.DefaultBean;

public class GreyCOSBenchmarkBeanProvider {

  @DefaultBean
  @Singleton
  @Produces
  PlannerBenchmarkFactory benchmarkFactory(PlannerBenchmarkConfig plannerBenchmarkConfig) {
    return PlannerBenchmarkFactory.create(plannerBenchmarkConfig);
  }
}
