package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.phase.AbstractPhaseFactory;
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

    // IslandModelPhaseConfig now extends LocalSearchPhaseConfig, so each island runs
    // the same local search configuration with independent random seeds and solution states
    LOGGER.debug(
        "Building island model with {} islands, inheriting LocalSearchPhaseConfig", islandCount);

    var phaseTermination = buildPhaseTermination(solverConfigPolicy, solverTermination);

    return new DefaultIslandModelPhase.Builder<>(phaseIndex, "", phaseTermination)
        .withIslandModelConfig(phaseConfig)
        .withConfigPolicy(solverConfigPolicy)
        .withBestSolutionRecaller(bestSolutionRecaller)
        .withSolverTermination(solverTermination)
        .withIslandCount(islandCount)
        .withMigrationFrequency(migrationFrequency)
        .withCompareGlobalEnabled(compareGlobalEnabled)
        .withReceiveGlobalUpdateFrequency(receiveGlobalUpdateFrequency)
        .build();
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
