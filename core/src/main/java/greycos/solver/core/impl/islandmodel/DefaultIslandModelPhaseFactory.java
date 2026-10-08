package greycos.solver.core.impl.islandmodel;

import java.util.List;

import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.impl.geneticalgorithm.DefaultGeneticAlgorithmPhaseFactory;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.phase.AbstractPhaseFactory;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.termination.IslandTerminationBinding;
import greycos.solver.core.impl.solver.termination.SolverTermination;
import greycos.solver.core.impl.solver.termination.TerminationFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating island model phases that run multiple independent island agents in parallel.
 */
public class DefaultIslandModelPhaseFactory<Solution_>
    extends AbstractPhaseFactory<Solution_, IslandModelPhaseConfig> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultIslandModelPhaseFactory.class);

  public DefaultIslandModelPhaseFactory(IslandModelPhaseConfig phaseConfig) {
    super(phaseConfig);
  }

  @Override
  public DefaultIslandModelPhase<Solution_> buildPhase(
      int phaseIndex,
      boolean lastInitializingPhase,
      HeuristicConfigPolicy<Solution_> solverConfigPolicy,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      SolverTermination<Solution_> solverTermination) {

    var configurationPath = "phase[" + phaseIndex + "].islandModel";
    DefaultGeneticAlgorithmPhaseFactory.validateIslandPlacement(phaseConfig, configurationPath);
    validateConfig(phaseConfig, configurationPath);
    validateConfigList(phaseConfig.getPhaseConfigList(), configurationPath);
    validateTerminationConfigs(phaseConfig, solverConfigPolicy, configurationPath);
    IslandTerminationBinding.validate(solverTermination, "solver.termination");

    int islandCount =
        phaseConfig.getIslandCount() != null
            ? phaseConfig.getIslandCount()
            : IslandModelPhaseConfig.DEFAULT_ISLAND_COUNT;
    int migrationFrequency =
        phaseConfig.getMigrationFrequency() != null
            ? phaseConfig.getMigrationFrequency()
            : IslandModelPhaseConfig.DEFAULT_MIGRATION_FREQUENCY;
    boolean compareGlobalEnabled =
        phaseConfig.getCompareGlobalEnabled() != null
            ? phaseConfig.getCompareGlobalEnabled()
            : true;

    int receiveGlobalUpdateFrequency =
        phaseConfig.getReceiveGlobalUpdateFrequency() != null
            ? phaseConfig.getReceiveGlobalUpdateFrequency()
            : IslandModelPhaseConfig.DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY;

    if (phaseConfig.getReceiveGlobalUpdateFrequency() == null
        && phaseConfig.getCompareGlobalFrequency() != null) {
      receiveGlobalUpdateFrequency = phaseConfig.getCompareGlobalFrequency();
      LOGGER.warn(
          "Using deprecated 'compareGlobalFrequency' parameter. "
              + "Please use 'receiveGlobalUpdateFrequency' instead.");
    }

    long migrationTimeout =
        phaseConfig.getMigrationTimeout() != null
            ? phaseConfig.getMigrationTimeout()
            : IslandModelPhaseConfig.DEFAULT_MIGRATION_TIMEOUT;

    LOGGER.debug("Building island model with {} independent agents", islandCount);

    var environmentMode = resolveEnvironmentMode(solverConfigPolicy);
    var phaseConfigPolicy = solverConfigPolicy.copyPhaseConfigPolicy(environmentMode);
    var phaseTermination = buildPhaseTermination(phaseConfigPolicy, solverTermination);

    return new DefaultIslandModelPhase.Builder<>(phaseIndex, environmentMode, "", phaseTermination)
        .withIslandModelConfig(phaseConfig)
        .withConfigPolicy(phaseConfigPolicy)
        .withBestSolutionRecaller(bestSolutionRecaller)
        .withSolverTermination(solverTermination)
        .withIslandCount(islandCount)
        .withMigrationFrequency(migrationFrequency)
        .withCompareGlobalEnabled(compareGlobalEnabled)
        .withReceiveGlobalUpdateFrequency(receiveGlobalUpdateFrequency)
        .withMigrationTimeout(migrationTimeout)
        .enableAssertions()
        .build();
  }

  /** Validate Java-configured descendants before any enclosing phase starts worker threads. */
  public static void validateConfigList(
      List<? extends PhaseConfig> phases, String configurationPath) {
    if (phases == null) {
      return;
    }
    for (int index = 0; index < phases.size(); index++) {
      var phase = phases.get(index);
      var childPath = configurationPath + ".phase[" + index + "]";
      if (phase instanceof IslandModelPhaseConfig island) {
        childPath += ".islandModel";
        DefaultGeneticAlgorithmPhaseFactory.validateIslandPlacement(island, childPath);
        validateConfig(island, childPath);
        validateConfigList(island.getPhaseConfigList(), childPath);
      } else if (phase instanceof PartitionedSearchPhaseConfig partition) {
        DefaultGeneticAlgorithmPhaseFactory.validateNoNestedPhases(
            partition.getPhaseConfigList(), childPath + ".partitionedSearch");
        validateConfigList(partition.getPhaseConfigList(), childPath + ".partitionedSearch");
      }
    }
  }

  private void validateTerminationConfigs(
      PhaseConfig<?> phase,
      HeuristicConfigPolicy<Solution_> configPolicy,
      String configurationPath) {
    if (phase.getTerminationConfig() != null) {
      try {
        TerminationFactory.<Solution_>create(phase.getTerminationConfig())
            .buildTermination(configPolicy);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Invalid termination at (" + configurationPath + ".termination): " + e.getMessage(), e);
      } catch (IllegalStateException e) {
        throw new IllegalStateException(
            "Invalid termination at (" + configurationPath + ".termination): " + e.getMessage(), e);
      }
    }
    List<? extends PhaseConfig> children = null;
    if (phase instanceof IslandModelPhaseConfig island) {
      children = island.getPhaseConfigList();
    } else if (phase instanceof PartitionedSearchPhaseConfig partition) {
      children = partition.getPhaseConfigList();
    }
    if (children != null) {
      for (int index = 0; index < children.size(); index++) {
        validateTerminationConfigs(
            children.get(index), configPolicy, configurationPath + ".phase[" + index + "]");
      }
    }
  }

  private static void validateConfig(IslandModelPhaseConfig config, String configurationPath) {
    int islandCount =
        config.getIslandCount() != null
            ? config.getIslandCount()
            : IslandModelPhaseConfig.DEFAULT_ISLAND_COUNT;

    if (islandCount < 1) {
      throw new IllegalArgumentException(
          "Island count must be at least 1, but was: "
              + islandCount
              + " at ("
              + configurationPath
              + ".islandCount).");
    }

    if (islandCount > 100) {
      throw new IllegalArgumentException(
          "Island count must not exceed 100, but was: "
              + islandCount
              + " at ("
              + configurationPath
              + ".islandCount).");
    }

    Integer migrationFrequency = config.getMigrationFrequency();
    if (migrationFrequency != null && migrationFrequency < 1) {
      throw new IllegalArgumentException(
          "Migration frequency must be at least 1, but was: "
              + migrationFrequency
              + " at ("
              + configurationPath
              + ".migrationFrequency).");
    }

    Integer receiveGlobalUpdateFrequency = config.getReceiveGlobalUpdateFrequency();
    if (receiveGlobalUpdateFrequency != null && receiveGlobalUpdateFrequency < 1) {
      throw new IllegalArgumentException(
          "Receive global update frequency must be at least 1, but was: "
              + receiveGlobalUpdateFrequency
              + " at ("
              + configurationPath
              + ".receiveGlobalUpdateFrequency).");
    }

    Integer compareGlobalFrequency = config.getCompareGlobalFrequency();
    if (compareGlobalFrequency != null && compareGlobalFrequency < 1) {
      throw new IllegalArgumentException(
          "Compare global frequency must be at least 1, but was: "
              + compareGlobalFrequency
              + " at ("
              + configurationPath
              + ".compareGlobalFrequency).");
    }

    Long migrationTimeout = config.getMigrationTimeout();
    if (migrationTimeout != null && migrationTimeout < 1) {
      throw new IllegalArgumentException(
          "Migration timeout must be at least 1, but was: "
              + migrationTimeout
              + " at ("
              + configurationPath
              + ".migrationTimeout).");
    }
  }
}
