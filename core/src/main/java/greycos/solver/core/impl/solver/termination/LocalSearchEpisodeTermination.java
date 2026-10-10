package greycos.solver.core.impl.solver.termination;

import java.util.function.BooleanSupplier;

import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

/** Episode-local limits with an enclosing stop condition that never participates in cooling. */
public final class LocalSearchEpisodeTermination<Solution_>
    extends AbstractPhaseTermination<Solution_> {

  private final Termination<Solution_> configured;
  private BooleanSupplier enclosingTerminated = () -> false;
  private BooleanSupplier adoptionPending = () -> false;

  public LocalSearchEpisodeTermination(Termination<Solution_> configured) {
    this.configured = configured;
  }

  public void setEnclosingTermination(BooleanSupplier terminated, BooleanSupplier adoptionPending) {
    this.enclosingTerminated = terminated;
    this.adoptionPending = adoptionPending;
  }

  public void solvingStarted(SolverScope<Solution_> scope) {
    if (configured instanceof SolverTermination<Solution_> solver) solver.solvingStarted(scope);
  }

  public void solvingEnded(SolverScope<Solution_> scope) {
    if (configured instanceof SolverTermination<Solution_> solver) solver.solvingEnded(scope);
  }

  public boolean isEnclosingTerminated() {
    return Thread.currentThread().isInterrupted() || enclosingTerminated.getAsBoolean();
  }

  public boolean isAdoptionPending() {
    return adoptionPending.getAsBoolean();
  }

  /**
   * Used after admitting a candidate: its own attempt reservation does not interrupt evaluation.
   */
  public boolean isNonAttemptTerminated(AbstractPhaseScope<Solution_> scope) {
    return isEnclosingTerminated()
        || isAdoptionPending()
        || (configured != null
            && TerminationEvaluation.isPhaseTerminated(configured, scope, false));
  }

  @Override
  public boolean isPhaseTerminated(AbstractPhaseScope<Solution_> scope) {
    return isNonAttemptTerminated(scope)
        || ((LocalSearchPhaseScope<Solution_>) scope).getSelectionAttemptLedger().isExhausted();
  }

  @Override
  public double calculatePhaseTimeGradient(AbstractPhaseScope<Solution_> scope) {
    var attempts = ((LocalSearchPhaseScope<Solution_>) scope).getSelectionAttemptLedger();
    double attemptGradient =
        TerminationGradient.ratio(attempts.getConsumedCount(), attempts.getLimit());
    double configuredGradient =
        configured == null
            ? -1.0
            : TerminationEvaluation.calculatePhaseTimeGradient(configured, scope, false);
    if (configuredGradient == -1.0) return attemptGradient;
    if (!Double.isFinite(configuredGradient)
        || configuredGradient < 0.0
        || configuredGradient > 1.0) {
      throw new IllegalStateException(
          "The configured episode time gradient ("
              + configuredGradient
              + ") must be -1 or a finite number between 0 and 1.");
    }
    return Math.max(attemptGradient, configuredGradient);
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> scope) {
    if (configured instanceof PhaseTermination<Solution_> phase) phase.phaseStarted(scope);
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> scope) {
    if (configured instanceof PhaseTermination<Solution_> phase) phase.stepStarted(scope);
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> scope) {
    if (configured instanceof PhaseTermination<Solution_> phase) phase.stepEnded(scope);
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> scope) {
    if (configured instanceof PhaseTermination<Solution_> phase) phase.phaseEnded(scope);
  }

  @Override
  public boolean isSolverTerminated(SolverScope<Solution_> scope) {
    return false;
  }

  @Override
  public double calculateSolverTimeGradient(SolverScope<Solution_> scope) {
    return -1.0;
  }
}
