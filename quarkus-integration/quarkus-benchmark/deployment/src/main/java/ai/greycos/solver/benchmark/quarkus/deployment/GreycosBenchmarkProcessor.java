package ai.greycos.solver.benchmark.quarkus.deployment;

import java.util.Optional;

import ai.greycos.solver.benchmark.config.PlannerBenchmarkConfig;
import ai.greycos.solver.benchmark.quarkus.GreycosBenchmarkBeanProvider;
import ai.greycos.solver.benchmark.quarkus.GreycosBenchmarkRecorder;
import ai.greycos.solver.benchmark.quarkus.UnavailableGreycosBenchmarkBeanProvider;
import ai.greycos.solver.benchmark.quarkus.config.GreycosBenchmarkRuntimeConfig;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.quarkus.deployment.SolverConfigBuildItem;

import org.jboss.logging.Logger;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class GreycosBenchmarkProcessor {

  private static final Logger log = Logger.getLogger(GreycosBenchmarkProcessor.class.getName());

  GreycosBenchmarkBuildTimeConfig greycosBenchmarkBuildTimeConfig;

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem("greycos-solver-benchmark");
  }

  @BuildStep
  HotDeploymentWatchedFileBuildItem watchSolverBenchmarkConfigXml() {
    String solverBenchmarkConfigXML =
        greycosBenchmarkBuildTimeConfig
            .solverBenchmarkConfigXml()
            .orElse(GreycosBenchmarkBuildTimeConfig.DEFAULT_SOLVER_BENCHMARK_CONFIG_URL);
    return new HotDeploymentWatchedFileBuildItem(solverBenchmarkConfigXML);
  }

  @BuildStep
  BenchmarkConfigBuildItem registerAdditionalBeans(
      BuildProducer<AdditionalBeanBuildItem> additionalBeans,
      BuildProducer<UnremovableBeanBuildItem> unremovableBeans,
      SolverConfigBuildItem solverConfigBuildItem) {
    // We don't support benchmarking for multiple solvers
    if (solverConfigBuildItem.getSolverConfigMap().size() > 1) {
      throw new ConfigurationException(
          """
                    When defining multiple solvers, the benchmark feature is not enabled.
                    Consider using separate <solverBenchmark> instances for evaluating different solver configurations.""");
    }
    if (solverConfigBuildItem.getGeneratedGizmoClasses() == null) {
      log.warn("Skipping Greycos Benchmark extension because the Greycos extension was skipped.");
      additionalBeans.produce(
          new AdditionalBeanBuildItem(UnavailableGreycosBenchmarkBeanProvider.class));
      return new BenchmarkConfigBuildItem(null);
    }
    PlannerBenchmarkConfig benchmarkConfig;
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Optional<String> benchmarkConfigFile =
        greycosBenchmarkBuildTimeConfig.solverBenchmarkConfigXml();
    if (benchmarkConfigFile.isPresent()) {
      String solverBenchmarkConfigXML = benchmarkConfigFile.get();
      if (classLoader.getResource(solverBenchmarkConfigXML) == null) {
        throw new ConfigurationException(
            "Invalid quarkus.greycos.benchmark.solver-benchmark-config-xml property ("
                + solverBenchmarkConfigXML
                + "): that classpath resource does not exist.");
      }
      benchmarkConfig = PlannerBenchmarkConfig.createFromXmlResource(solverBenchmarkConfigXML);
    } else if (classLoader.getResource(
            GreycosBenchmarkBuildTimeConfig.DEFAULT_SOLVER_BENCHMARK_CONFIG_URL)
        != null) {
      benchmarkConfig =
          PlannerBenchmarkConfig.createFromXmlResource(
              GreycosBenchmarkBuildTimeConfig.DEFAULT_SOLVER_BENCHMARK_CONFIG_URL);
    } else {
      benchmarkConfig = null;
    }
    additionalBeans.produce(new AdditionalBeanBuildItem(GreycosBenchmarkBeanProvider.class));
    unremovableBeans.produce(
        UnremovableBeanBuildItem.beanTypes(GreycosBenchmarkRuntimeConfig.class));
    unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(SolverConfig.class));
    return new BenchmarkConfigBuildItem(benchmarkConfig);
  }

  @BuildStep
  @Record(ExecutionTime.RUNTIME_INIT)
  void registerRuntimeBeans(
      GreycosBenchmarkRecorder recorder,
      BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
      SolverConfigBuildItem solverConfigBuildItem,
      BenchmarkConfigBuildItem benchmarkConfigBuildItem,
      GreycosBenchmarkRuntimeConfig runtimeConfig) {
    if (solverConfigBuildItem.getGeneratedGizmoClasses() == null) {
      return;
    }
    syntheticBeans.produce(
        SyntheticBeanBuildItem.configure(PlannerBenchmarkConfig.class)
            .supplier(
                recorder.benchmarkConfigSupplier(
                    benchmarkConfigBuildItem.getBenchmarkConfig(), runtimeConfig))
            .setRuntimeInit()
            .done());
  }
}
