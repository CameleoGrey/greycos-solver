package ai.greycos.solver.core.impl.partitionedsearch;

import ai.greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import ai.greycos.solver.core.enterprise.GreycosSolverEnterpriseService;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.phase.AbstractPhaseFactory;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.termination.SolverTermination;

public class DefaultPartitionedSearchPhaseFactory<Solution_>
    extends AbstractPhaseFactory<Solution_, PartitionedSearchPhaseConfig> {

  public DefaultPartitionedSearchPhaseFactory(PartitionedSearchPhaseConfig phaseConfig) {
    super(phaseConfig);
  }

  @Override
  public PartitionedSearchPhase<Solution_> buildPhase(
      int phaseIndex,
      boolean lastInitializingPhase,
      HeuristicConfigPolicy<Solution_> solverConfigPolicy,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      SolverTermination<Solution_> solverTermination) {
    return GreycosSolverEnterpriseService.loadOrFail(
            GreycosSolverEnterpriseService.Feature.PARTITIONED_SEARCH)
        .buildPartitionedSearch(
            phaseIndex,
            phaseConfig,
            solverConfigPolicy,
            solverTermination,
            this::buildPhaseTermination);
  }
}
