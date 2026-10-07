package greycos.solver.core.impl.geneticalgorithm;

import java.util.List;

import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.phase.AbstractPhaseFactory;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.termination.SolverTermination;

/** Builds the serial, basic-variable genetic algorithm. */
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
    if (policy.getSolutionDescriptor().hasListVariable()) {
      throw new UnsupportedOperationException(
          "The geneticAlgorithm phase supports only basic planning variables; list and mixed models are not supported.");
    }
    var moveThreadCount =
        resolveMoveThreadCount(
            resolvedConfig.getMoveThreadCount(), policy.getMoveThreadCount(), true);
    if (moveThreadCount != null) {
      throw new UnsupportedOperationException(
          "The geneticAlgorithm phase does not support moveThreadCount ("
              + moveThreadCount
              + "). Set its moveThreadCount to NONE to use serial evaluation.");
    }
    return new DefaultGeneticAlgorithmPhase.Builder<>(
            phaseIndex,
            environmentMode,
            policy.getLogIndentation(),
            buildPhaseTermination(policy, solverTermination),
            resolvedConfig,
            bestSolutionRecaller)
        .enableAssertions()
        .build();
  }

  /** Rejects unsupported descendants before any enclosing phase can start workers. */
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
                + ") cannot be nested under islandModel or partitionedSearch in Stage 1.");
      } else if (phase instanceof IslandModelPhaseConfig island) {
        validateNoNestedPhases(island.getPhaseConfigList(), childPath + ".islandModel");
      } else if (phase instanceof PartitionedSearchPhaseConfig partition) {
        validateNoNestedPhases(partition.getPhaseConfigList(), childPath + ".partitionedSearch");
      }
    }
  }
}
