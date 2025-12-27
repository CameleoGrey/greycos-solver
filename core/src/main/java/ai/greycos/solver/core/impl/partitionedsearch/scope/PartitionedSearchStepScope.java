package ai.greycos.solver.core.impl.partitionedsearch.scope;

import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;

/**
 * Scope for partitioned search step execution.
 *
 * @param <Solution_> solution type, class with {@link
 *     ai.greycos.solver.core.api.domain.solution.PlanningSolution} annotation
 */
public final class PartitionedSearchStepScope<Solution_> extends AbstractStepScope<Solution_> {

  private final PartitionedSearchPhaseScope<Solution_> phaseScope;
  private PartitionChangeMove<Solution_> step = null;

  public PartitionedSearchStepScope(PartitionedSearchPhaseScope<Solution_> phaseScope) {
    super(phaseScope.getNextStepIndex());
    this.phaseScope = phaseScope;
  }

  public PartitionedSearchStepScope(
      PartitionedSearchPhaseScope<Solution_> phaseScope, int stepIndex) {
    super(stepIndex);
    this.phaseScope = phaseScope;
  }

  @Override
  public PartitionedSearchPhaseScope<Solution_> getPhaseScope() {
    return phaseScope;
  }

  public PartitionChangeMove<Solution_> getStep() {
    return step;
  }

  public void setStep(PartitionChangeMove<Solution_> step) {
    this.step = step;
  }
}
