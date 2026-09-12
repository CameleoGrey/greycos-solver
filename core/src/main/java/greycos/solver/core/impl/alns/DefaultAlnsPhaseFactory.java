package greycos.solver.core.impl.alns;

import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.phase.AbstractPhaseFactory;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.termination.SolverTermination;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

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
    var moveThreadCount =
        resolveMoveThreadCount(phaseConfig.getMoveThreadCount(), policy.getMoveThreadCount(), true);
    if (moveThreadCount != null && policy.isConstraintStreamProfilingEnabled()) {
      throw new UnsupportedOperationException(
          "ALNS move workers are not supported together with constraintStreamProfilingEnabled (true).");
    }
    var builder =
        new DefaultAlnsPhase.Builder<>(
                phaseIndex,
                policy.getLogIndentation(),
                buildPhaseTermination(policy, solverTermination),
                phaseConfig.copyConfig(),
                bestSolutionRecaller)
            .withMoveThreadCount(moveThreadCount)
            .withMoveThreadBufferSize(
                policy.getMoveThreadBufferSize() != null ? policy.getMoveThreadBufferSize() : 10);
    if (moveThreadCount != null) {
      builder.withThreadFactory(policy.buildThreadFactory(ChildThreadType.MOVE_THREAD));
    }
    return builder.enableAssertions(policy.getEnvironmentMode()).build();
  }
}
