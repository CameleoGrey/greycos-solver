package ai.greycos.solver.benchmark.quarkus.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

/** During build time, this is translated into GreyCOS's Config classes. */
@ConfigMapping(prefix = "quarkus.greycos.benchmark")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface GreyCOSBenchmarkBuildTimeConfig {

  String DEFAULT_SOLVER_BENCHMARK_CONFIG_URL = "solverBenchmarkConfig.xml";

  /**
   * A classpath resource to read the benchmark configuration XML. Defaults to {@value
   * DEFAULT_SOLVER_BENCHMARK_CONFIG_URL}. If this property isn't specified, that
   * solverBenchmarkConfig.xml is optional.
   */
  Optional<String> solverBenchmarkConfigXml();
}
