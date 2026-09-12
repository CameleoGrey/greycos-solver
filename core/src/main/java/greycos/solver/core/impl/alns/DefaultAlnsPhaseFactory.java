package greycos.solver.core.impl.alns;

import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.phase.AbstractPhaseFactory;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.termination.SolverTermination;

public final class DefaultAlnsPhaseFactory<Solution_>
    extends AbstractPhaseFactory<Solution_, AlnsPhaseConfig> {
  public DefaultAlnsPhaseFactory(AlnsPhaseConfig config) {
    super(config);
  }

  @Override
  public AlnsPhase<Solution_> buildPhase(
      int phaseIndex,
      boolean lastInitializingPhase,
      HeuristicConfigPolicy<Solution_> solverConfigPolicy,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      SolverTermination<Solution_> solverTermination) {
    var policy = solverConfigPolicy.createPhaseConfigPolicy();
    return new DefaultAlnsPhase.Builder<>(
            phaseIndex,
            policy.getLogIndentation(),
            buildPhaseTermination(policy, solverTermination),
            phaseConfig.copyConfig(),
            bestSolutionRecaller)
        .enableAssertions(policy.getEnvironmentMode())
        .build();
  }
}
