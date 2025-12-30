package ai.greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import ai.greycos.solver.core.config.phase.PhaseConfig;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.phase.AbstractPhaseFactory;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.phase.PhaseFactory;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.termination.SolverTermination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating island model phases.
 *
 * <p>This factory builds {@link DefaultIslandModelPhase} instances that run multiple independent
 * island agents in parallel.
 *
 * @param <Solution_> solution type
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

    validateConfig(phaseConfig);

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

    // Read receive global update frequency
    int receiveGlobalUpdateFrequency =
        phaseConfig.getReceiveGlobalUpdateFrequency() != null
            ? phaseConfig.getReceiveGlobalUpdateFrequency()
            : IslandModelPhaseConfig.DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY;

    // Fall back to deprecated parameter if new parameter is not set
    if (phaseConfig.getReceiveGlobalUpdateFrequency() == null
        && phaseConfig.getCompareGlobalFrequency() != null) {
      receiveGlobalUpdateFrequency = phaseConfig.getCompareGlobalFrequency();
      LOGGER.warn(
          "Using deprecated 'compareGlobalFrequency' parameter. "
              + "Please use 'receiveGlobalUpdateFrequency' instead.");
    }

    List<PhaseConfig<?>> wrappedPhaseConfigList = phaseConfig.getPhaseConfigList();
    LOGGER.debug(
        "Found {} wrapped phase configs for island model",
        wrappedPhaseConfigList != null ? wrappedPhaseConfigList.size() : 0);

    var phaseTermination = buildPhaseTermination(solverConfigPolicy, solverTermination);

    return new DefaultIslandModelPhase.Builder<>(phaseIndex, "", phaseTermination)
        .withWrappedPhaseConfigs(wrappedPhaseConfigList)
        .withConfigPolicy(solverConfigPolicy)
        .withBestSolutionRecaller(bestSolutionRecaller)
        .withSolverTermination(solverTermination)
        .withIslandCount(islandCount)
        .withMigrationFrequency(migrationFrequency)
        .withCompareGlobalEnabled(compareGlobalEnabled)
        .withReceiveGlobalUpdateFrequency(receiveGlobalUpdateFrequency)
        .build();
  }

  private List<Phase<Solution_>> buildWrappedPhases(
      HeuristicConfigPolicy<Solution_> solverConfigPolicy,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      SolverTermination<Solution_> solverTermination) {

    List<PhaseConfig<?>> wrappedPhaseConfigList = phaseConfig.getPhaseConfigList();
    if (wrappedPhaseConfigList == null || wrappedPhaseConfigList.isEmpty()) {
      LOGGER.warn("IslandModelPhaseConfig has no wrapped phase config, using empty phase list");
      return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    List<PhaseConfig> rawPhaseConfigList = (List<PhaseConfig>) (List<?>) wrappedPhaseConfigList;
    List<Phase<Solution_>> wrappedPhases =
        PhaseFactory.buildPhases(
            rawPhaseConfigList, solverConfigPolicy, bestSolutionRecaller, solverTermination);

    LOGGER.debug("Built {} wrapped phases for island model", wrappedPhases.size());
    return wrappedPhases;
  }

  private void validateConfig(IslandModelPhaseConfig config) {
    int islandCount =
        config.getIslandCount() != null
            ? config.getIslandCount()
            : IslandModelPhaseConfig.DEFAULT_ISLAND_COUNT;

    if (islandCount < 1) {
      throw new IllegalArgumentException(
          "Island count must be at least 1, but was: " + islandCount);
    }

    if (islandCount > 100) {
      throw new IllegalArgumentException(
          "Island count must not exceed 100, but was: " + islandCount);
    }

    Integer migrationFrequency = config.getMigrationFrequency();
    if (migrationFrequency != null && migrationFrequency < 1) {
      throw new IllegalArgumentException(
          "Migration frequency must be at least 1, but was: " + migrationFrequency);
    }
  }
}
