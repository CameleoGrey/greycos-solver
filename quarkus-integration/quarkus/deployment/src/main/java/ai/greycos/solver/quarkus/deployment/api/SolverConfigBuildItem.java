package ai.greycos.solver.quarkus.deployment.api;

import java.util.Map;

import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.quarkus.deployment.config.GreyCOSBuildTimeConfig;

import io.quarkus.builder.item.SimpleBuildItem;

public final class SolverConfigBuildItem extends SimpleBuildItem {
  private final Map<String, SolverConfig> solverConfigurations;
  private final GeneratedGizmoClasses generatedGizmoClasses;

  /** Constructor for multiple solver configurations. */
  public SolverConfigBuildItem(
      Map<String, SolverConfig> solverConfig, GeneratedGizmoClasses generatedGizmoClasses) {
    this.solverConfigurations = Map.copyOf(solverConfig);
    this.generatedGizmoClasses = generatedGizmoClasses;
  }

  public boolean isDefaultSolverConfig(String solverName) {
    return solverConfigurations.size() <= 1
        || GreyCOSBuildTimeConfig.DEFAULT_SOLVER_NAME.equals(solverName);
  }

  public Map<String, SolverConfig> getSolverConfigMap() {
    return solverConfigurations;
  }

  public GeneratedGizmoClasses getGeneratedGizmoClasses() {
    return generatedGizmoClasses;
  }
}
