package ai.greycos.solver.spring.boot.autoconfigure;

import static java.util.Objects.requireNonNullElse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import ai.greycos.solver.benchmark.api.PlannerBenchmarkFactory;
import ai.greycos.solver.benchmark.config.PlannerBenchmarkConfig;
import ai.greycos.solver.benchmark.config.SolverBenchmarkConfig;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.spring.boot.autoconfigure.config.BenchmarkProperties;
import ai.greycos.solver.spring.boot.autoconfigure.config.GreycosProperties;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@AutoConfigureAfter(GreycosSolverAutoConfiguration.class)
@ConditionalOnClass({PlannerBenchmarkFactory.class})
@ConditionalOnMissingBean({PlannerBenchmarkFactory.class})
@EnableConfigurationProperties({GreycosProperties.class})
public class GreycosBenchmarkAutoConfiguration
    implements BeanClassLoaderAware, ApplicationContextAware {

  private final GreycosProperties greycosProperties;
  private ClassLoader beanClassLoader;
  private ApplicationContext context;

  protected GreycosBenchmarkAutoConfiguration(GreycosProperties greycosProperties) {
    this.greycosProperties = greycosProperties;
  }

  @Override
  public void setApplicationContext(ApplicationContext context) throws BeansException {
    this.context = context;
  }

  @Bean
  @Lazy
  public PlannerBenchmarkConfig plannerBenchmarkConfig() {
    assertSingleSolver();
    SolverConfig solverConfig = context.getBean(SolverConfig.class);
    PlannerBenchmarkConfig benchmarkConfig;
    if (greycosProperties.getBenchmark() != null
        && greycosProperties.getBenchmark().getSolverBenchmarkConfigXml() != null) {
      if (beanClassLoader.getResource(
              greycosProperties.getBenchmark().getSolverBenchmarkConfigXml())
          == null) {
        throw new IllegalStateException(
            "Invalid greycos.benchmark.solverBenchmarkConfigXml property (%s): that classpath resource does not exist."
                .formatted(greycosProperties.getBenchmark().getSolverBenchmarkConfigXml()));
      }
      benchmarkConfig =
          PlannerBenchmarkConfig.createFromXmlResource(
              greycosProperties.getBenchmark().getSolverBenchmarkConfigXml(), beanClassLoader);
    } else if (beanClassLoader.getResource(BenchmarkProperties.DEFAULT_SOLVER_BENCHMARK_CONFIG_URL)
        != null) {
      benchmarkConfig =
          PlannerBenchmarkConfig.createFromXmlResource(
              GreycosProperties.DEFAULT_SOLVER_BENCHMARK_CONFIG_URL, beanClassLoader);
    } else {
      benchmarkConfig = PlannerBenchmarkConfig.createFromSolverConfig(solverConfig);
      benchmarkConfig.setBenchmarkDirectory(
          new File(BenchmarkProperties.DEFAULT_BENCHMARK_RESULT_DIRECTORY));
    }

    if (greycosProperties.getBenchmark() != null
        && greycosProperties.getBenchmark().getResultDirectory() != null) {
      benchmarkConfig.setBenchmarkDirectory(
          new File(greycosProperties.getBenchmark().getResultDirectory()));
    }

    if (benchmarkConfig.getBenchmarkDirectory() == null) {
      benchmarkConfig.setBenchmarkDirectory(
          new File(BenchmarkProperties.DEFAULT_BENCHMARK_RESULT_DIRECTORY));
    }

    var inheritedBenchmarkConfig = benchmarkConfig.getInheritedSolverBenchmarkConfig();
    if (inheritedBenchmarkConfig == null) {
      inheritedBenchmarkConfig = new SolverBenchmarkConfig();
      benchmarkConfig.setInheritedSolverBenchmarkConfig(inheritedBenchmarkConfig);
      inheritedBenchmarkConfig.setSolverConfig(solverConfig.copyConfig());
    }

    var inheritedBenchmarkSolverConfig =
        Objects.requireNonNull(inheritedBenchmarkConfig.getSolverConfig());
    if (greycosProperties.getBenchmark() != null
        && greycosProperties.getBenchmark().getSolver() != null) {
      GreycosSolverAutoConfiguration.applyTerminationProperties(
          inheritedBenchmarkSolverConfig,
          greycosProperties.getBenchmark().getSolver().getTermination());
    }

    var inheritedTerminationConfig = inheritedBenchmarkSolverConfig.getTerminationConfig();
    if (inheritedTerminationConfig == null || !inheritedTerminationConfig.isConfigured()) {
      List<SolverBenchmarkConfig> solverBenchmarkConfigList =
          benchmarkConfig.getSolverBenchmarkConfigList();
      List<String> unconfiguredTerminationSolverBenchmarkList = new ArrayList<>();
      if (solverBenchmarkConfigList == null) {
        throw new IllegalStateException(
            """
                        At least one of the properties \
                        greycos.benchmark.solver.termination.spent-limit, \
                        greycos.benchmark.solver.termination.best-score-limit, \
                        greycos.benchmark.solver.termination.unimproved-spent-limit \
                        is required if termination is not configured in the \
                        inherited solver benchmark config and solverBenchmarkBluePrint is used.
                        """);
      }
      if (solverBenchmarkConfigList.size() == 1
          && solverBenchmarkConfigList.get(0).getSolverConfig() == null) {
        // Benchmark config was created from solver config, which means only the inherited solver
        // config exists.
        if (!inheritedBenchmarkSolverConfig.canTerminate()) {
          String benchmarkConfigName =
              requireNonNullElse(
                  inheritedBenchmarkConfig.getName(), "InheritedSolverBenchmarkConfig");
          unconfiguredTerminationSolverBenchmarkList.add(benchmarkConfigName);
        }
      } else {
        for (int i = 0; i < solverBenchmarkConfigList.size(); i++) {
          SolverBenchmarkConfig solverBenchmarkConfig = solverBenchmarkConfigList.get(i);
          if (!solverConfig.canTerminate()) {
            String benchmarkConfigName =
                requireNonNullElse(solverBenchmarkConfig.getName(), "SolverBenchmarkConfig" + i);
            unconfiguredTerminationSolverBenchmarkList.add(benchmarkConfigName);
          }
        }
      }
      if (!unconfiguredTerminationSolverBenchmarkList.isEmpty()) {
        throw new IllegalStateException(
            """
                        The following %s do not \
                        have termination configured: \
                        %s. \
                        At least one of the properties \
                        greycos.benchmark.solver.termination.spent-limit, \
                        greycos.benchmark.solver.termination.best-score-limit, \
                        greycos.benchmark.solver.termination.unimproved-spent-limit \
                        is required if termination is not configured in a solver benchmark and the \
                        inherited solver benchmark config.
                        """
                .formatted(
                    SolverBenchmarkConfig.class.getSimpleName(),
                    unconfiguredTerminationSolverBenchmarkList.stream()
                        .collect(Collectors.joining(", ", "[", "]"))));
      }
    }
    return benchmarkConfig;
  }

  @Bean
  @Lazy
  public PlannerBenchmarkFactory plannerBenchmarkFactory(PlannerBenchmarkConfig benchmarkConfig) {
    if (benchmarkConfig == null) {
      return null;
    }
    assertSingleSolver();
    return PlannerBenchmarkFactory.create(benchmarkConfig);
  }

  private void assertSingleSolver() {
    if (greycosProperties.getSolver() != null && greycosProperties.getSolver().size() > 1) {
      throw new IllegalStateException(
          """
                    When defining multiple solvers, the benchmark feature is not enabled.
                    Consider using separate <solverBenchmark> instances for evaluating different solver configurations.""");
    }
  }

  @Override
  public void setBeanClassLoader(ClassLoader classLoader) {
    this.beanClassLoader = classLoader;
  }
}
