package greycos.solver.core.impl.geneticalgorithm;

import java.util.List;

import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelectorFactory;
import greycos.solver.core.impl.phase.AbstractPhaseFactory;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.termination.SolverTermination;

/** Builds the serial genetic algorithm for basic, list, and mixed models. */
public final class DefaultGeneticAlgorithmPhaseFactory<Solution_>
    extends AbstractPhaseFactory<Solution_, GeneticAlgorithmPhaseConfig> {

  public DefaultGeneticAlgorithmPhaseFactory(GeneticAlgorithmPhaseConfig config) {
    super(config);
  }

  @Override
  public DefaultGeneticAlgorithmPhase<Solution_> buildPhase(
      int phaseIndex,
      boolean lastInitializingPhase,
      HeuristicConfigPolicy<Solution_> solverConfigPolicy,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      SolverTermination<Solution_> solverTermination) {
    var environmentMode = resolveEnvironmentMode(solverConfigPolicy);
    var policy = solverConfigPolicy.copyPhaseConfigPolicy(environmentMode);
    var resolvedConfig = phaseConfig.resolve();
    var moveThreadCount =
        resolveMoveThreadCount(
            resolvedConfig.getMoveThreadCount(), policy.getMoveThreadCount(), true);
    if (moveThreadCount != null) {
      throw new UnsupportedOperationException(
          "The geneticAlgorithm phase does not support moveThreadCount ("
              + moveThreadCount
              + "). Set its moveThreadCount to NONE to use serial evaluation.");
    }
    var builder =
        new DefaultGeneticAlgorithmPhase.Builder<>(
            phaseIndex,
            environmentMode,
            policy.getLogIndentation(),
            buildPhaseTermination(policy, solverTermination),
            resolvedConfig,
            bestSolutionRecaller);
    if (resolvedConfig.getLocalImprovementMoveCountLimit() > 0L) {
      // Each solve gets fresh selector state and mimic registrations, including problem restarts.
      builder.withLocalImprovementMovesFactory(
          random ->
              buildLocalImprovementMoves(
                  policy
                      .copyPhaseConfigPolicy(environmentMode)
                      .cloneBuilder()
                      .withRandom(random)
                      .build()));
    }
    return builder.enableAssertions().build();
  }

  static <Solution_> GeneticAlgorithmLocalImprovementMoves<Solution_> buildLocalImprovementMoves(
      HeuristicConfigPolicy<Solution_> policy) {
    if (!policy.getSolutionDescriptor().hasListVariable()) {
      return new GeneticAlgorithmLocalImprovementMoves<>(List.of());
    }
    var listChange =
        MoveSelectorFactory.<Solution_>create(new ListChangeMoveSelectorConfig())
            .buildMoveSelector(
                policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM, false);
    var listSwap =
        MoveSelectorFactory.<Solution_>create(new ListSwapMoveSelectorConfig())
            .buildMoveSelector(
                policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM, false);
    return new GeneticAlgorithmLocalImprovementMoves<>(List.of(listChange, listSwap));
  }

  /** Rejects GA below a partitioned-search ancestor, including intervening island phases. */
  public static void validateNoNestedPhases(
      List<? extends PhaseConfig> phases, String configurationPath) {
    if (phases == null) {
      return;
    }
    for (int index = 0; index < phases.size(); index++) {
      var phase = phases.get(index);
      var childPath = configurationPath + ".phase[" + index + "]";
      if (phase instanceof GeneticAlgorithmPhaseConfig) {
        throw new UnsupportedOperationException(
            "The geneticAlgorithm phase at ("
                + childPath
                + ") cannot be nested under partitionedSearch.");
      } else if (phase instanceof IslandModelPhaseConfig island) {
        validateNoNestedPhases(island.getPhaseConfigList(), childPath + ".islandModel");
      } else if (phase instanceof PartitionedSearchPhaseConfig partition) {
        validateNoNestedPhases(partition.getPhaseConfigList(), childPath + ".partitionedSearch");
      }
    }
  }

  /** Validates the effective configuration of every island GA before workers are created. */
  public static void validateIslandPlacement(
      IslandModelPhaseConfig island, String configurationPath) {
    var phases = island.getPhaseConfigList();
    if (phases == null) {
      return;
    }
    var inheritedMoveThreadCount = island.getMoveThreadCount();
    if (inheritedMoveThreadCount == null) {
      inheritedMoveThreadCount = SolverConfig.MOVE_THREAD_COUNT_NONE;
    }
    for (int index = 0; index < phases.size(); index++) {
      var phase = phases.get(index);
      var childPath = configurationPath + ".phase[" + index + "]";
      if (phase instanceof GeneticAlgorithmPhaseConfig geneticAlgorithm) {
        var resolved = geneticAlgorithm.resolve();
        var configuredCount = resolved.getMoveThreadCount();
        var effectiveCount = configuredCount == null ? inheritedMoveThreadCount : configuredCount;
        var moveThreadCount =
            new DefaultGeneticAlgorithmPhaseFactory<>(resolved)
                .resolveMoveThreadCount(effectiveCount, true);
        if (moveThreadCount != null) {
          throw new UnsupportedOperationException(
              "The geneticAlgorithm phase at (%s) does not support moveThreadCount (%s). Set its moveThreadCount to NONE to use serial evaluation."
                  .formatted(childPath, moveThreadCount));
        }
      } else if (phase instanceof IslandModelPhaseConfig nestedIsland) {
        validateIslandPlacement(nestedIsland, childPath + ".islandModel");
      } else if (phase instanceof PartitionedSearchPhaseConfig partition) {
        validateNoNestedPhases(partition.getPhaseConfigList(), childPath + ".partitionedSearch");
      }
    }
  }
}
