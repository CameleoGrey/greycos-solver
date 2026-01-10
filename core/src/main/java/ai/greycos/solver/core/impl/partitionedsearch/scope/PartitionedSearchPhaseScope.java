package ai.greycos.solver.core.impl.partitionedsearch.scope;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * Scope for partitioned search phase execution.
 *
 * <p>Maintains partition count and tracks completed steps during phase lifetime.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
public final class PartitionedSearchPhaseScope<Solution_> extends AbstractPhaseScope<Solution_> {

  private Integer partCount;
  private PartitionedSearchStepScope<Solution_> lastCompletedStepScope;

  public PartitionedSearchPhaseScope(SolverScope<Solution_> solverScope, int phaseIndex) {
    super(solverScope, phaseIndex);
    this.lastCompletedStepScope = new PartitionedSearchStepScope<>(this, -1);
  }

  public Integer getPartCount() {
    return partCount;
  }

  public void setPartCount(Integer partCount) {
    this.partCount = partCount;
  }

  @Override
  public PartitionedSearchStepScope<Solution_> getLastCompletedStepScope() {
    return lastCompletedStepScope;
  }

  public void setLastCompletedStepScope(
      PartitionedSearchStepScope<Solution_> lastCompletedStepScope) {
    this.lastCompletedStepScope = lastCompletedStepScope;
  }

  public void addChildThreadsScoreCalculationCount(long addition) {
    super.addChildThreadsScoreCalculationCount(addition);
  }
}
