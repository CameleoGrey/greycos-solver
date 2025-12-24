package ai.greycos.solver.core.impl.partitionedsearch.scope;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * Scope for partitioned search phase execution.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
public final class PartitionedSearchPhaseScope<Solution_> extends AbstractPhaseScope<Solution_> {

  private PartitionedSearchStepScope<Solution_> lastCompletedStepScope;

  public PartitionedSearchPhaseScope(SolverScope<Solution_> solverScope, int phaseIndex) {
    super(solverScope, phaseIndex);
    this.lastCompletedStepScope = new PartitionedSearchStepScope<>(this, 0);
  }

  @Override
  public PartitionedSearchStepScope<Solution_> getLastCompletedStepScope() {
    return lastCompletedStepScope;
  }

  public void setLastCompletedStepScope(
      PartitionedSearchStepScope<Solution_> lastCompletedStepScope) {
    this.lastCompletedStepScope = lastCompletedStepScope;
  }

  /**
   * Adds the score calculation count from child threads to the phase scope. This method is used to
   * track the total score calculation count across all partition threads.
   *
   * @param addition the number of score calculations to add
   */
  public void addChildThreadsScoreCalculationCount(long addition) {
    super.addChildThreadsScoreCalculationCount(addition);
  }
}
