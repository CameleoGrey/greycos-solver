package greycos.solver.core.impl.solver.termination;

import java.util.List;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.jspecify.annotations.Nullable;

/** One child's independent binding of the enclosing partition termination tree. */
final class PartitionTermination<Solution_> extends AbstractUniversalTermination<Solution_>
    implements ChildThreadSupportingTermination<Solution_, SolverScope<Solution_>> {

  private final PartitionTerminationBudget<Solution_> budget;
  private final PartitionTerminationBudget.Node<Solution_> root;
  private final List<SolverTermination<Solution_>> solverLeaves;
  private final List<PhaseTermination<Solution_>> phaseLeaves;
  private final boolean supportsRepairAttempts;
  private @Nullable AbstractPhaseScope<Solution_> activePhaseScope;

  @SuppressWarnings("unchecked")
  PartitionTermination(
      PartitionTerminationBudget<Solution_> budget,
      PartitionTerminationBudget.Node<Solution_> root,
      List<Termination<Solution_>> leaves) {
    this.budget = budget;
    this.root = root;
    solverLeaves =
        leaves.stream()
            .filter(SolverTermination.class::isInstance)
            .map(t -> (SolverTermination<Solution_>) t)
            .toList();
    phaseLeaves =
        leaves.stream()
            .filter(PhaseTermination.class::isInstance)
            .map(t -> (PhaseTermination<Solution_>) t)
            .toList();
    supportsRepairAttempts = leaves.stream().allMatch(AlnsTerminationPolling::isSupported);
  }

  @Override
  public boolean isSolverTerminated(SolverScope<Solution_> scope) {
    return root.evaluate(budget.progress(), scope, null, false).terminated();
  }

  @Override
  public double calculateSolverTimeGradient(SolverScope<Solution_> scope) {
    // Child phase bridges request the solver gradient, including local phase-only work leaves.
    return root.evaluate(budget.progress(), scope, activePhaseScope, true).gradient();
  }

  @Override
  public boolean isPhaseTerminated(AbstractPhaseScope<Solution_> scope) {
    return root.evaluate(budget.progress(), scope.getSolverScope(), scope, false).terminated();
  }

  @Override
  public double calculatePhaseTimeGradient(AbstractPhaseScope<Solution_> scope) {
    return root.evaluate(budget.progress(), scope.getSolverScope(), scope, true).gradient();
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> scope) {
    solverLeaves.forEach(t -> t.solvingStarted(scope));
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> scope) {
    solverLeaves.forEach(t -> t.solvingEnded(scope));
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> scope) {
    activePhaseScope = scope;
    phaseLeaves.forEach(t -> t.phaseStarted(scope));
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> scope) {
    phaseLeaves.forEach(t -> t.phaseEnded(scope));
    activePhaseScope = null;
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> scope) {
    phaseLeaves.forEach(t -> t.stepStarted(scope));
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> scope) {
    phaseLeaves.forEach(t -> t.stepEnded(scope));
  }

  @Override
  public void bestScoreImproved(AbstractStepScope<Solution_> scope) {
    phaseLeaves.forEach(t -> t.bestScoreImproved(scope));
  }

  @Override
  public Termination<Solution_> createChildThreadTermination(
      SolverScope<Solution_> scope, ChildThreadType childThreadType) {
    return budget.createChildTermination(scope);
  }

  boolean supportedForRepairAttempts() {
    return supportsRepairAttempts;
  }
}
