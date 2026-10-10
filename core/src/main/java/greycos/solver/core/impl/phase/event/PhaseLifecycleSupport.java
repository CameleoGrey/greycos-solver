package greycos.solver.core.impl.phase.event;

import java.util.List;
import java.util.function.Consumer;

import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptContext;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.event.AbstractEventSupport;
import greycos.solver.core.impl.solver.scope.SolverScope;

/** Internal API. */
public final class PhaseLifecycleSupport<Solution_>
    extends AbstractEventSupport<PhaseLifecycleListener<Solution_>> {

  private int setupSolvingStartedCount = -1;
  private int setupPhaseStartedCount = -1;
  private int stepStartedCount;

  public void fireSolvingStarted(SolverScope<Solution_> solverScope) {
    boolean boundedSetup = SelectionAttemptContext.isSetup();
    setupSolvingStartedCount = boundedSetup ? 0 : -1;
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      SelectionAttemptContext.beforeSelection();
      if (boundedSetup) setupSolvingStartedCount++;
      listener.solvingStarted(solverScope);
    }
  }

  public void firePhaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    boolean boundedSetup = SelectionAttemptContext.isSetup();
    setupPhaseStartedCount = boundedSetup ? 0 : -1;
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      SelectionAttemptContext.beforeSelection();
      if (boundedSetup) setupPhaseStartedCount++;
      listener.phaseStarted(phaseScope);
    }
  }

  public void fireStepStarted(AbstractStepScope<Solution_> stepScope) {
    boolean boundedSetup = SelectionAttemptContext.isSetup();
    stepStartedCount = boundedSetup ? 0 : -1;
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      SelectionAttemptContext.beforeSelection();
      if (boundedSetup) stepStartedCount++;
      listener.stepStarted(stepScope);
    }
  }

  public void fireStepEnded(AbstractStepScope<Solution_> stepScope) {
    int enteredCount = stepStartedCount;
    stepStartedCount = 0;
    if (enteredCount < 0) {
      // Preserve the ordinary local-search hot path; bounded setup owns aggregate cleanup.
      for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
        listener.stepEnded(stepScope);
      }
      return;
    }
    fireCleanup(enteredCount, listener -> listener.stepEnded(stepScope));
  }

  public void fireStepAborted(AbstractStepScope<Solution_> stepScope) {
    int enteredCount = stepStartedCount;
    stepStartedCount = 0;
    fireCleanup(enteredCount, listener -> listener.stepAborted(stepScope));
  }

  public void firePhaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    int enteredCount = setupPhaseStartedCount;
    setupPhaseStartedCount = -1;
    fireCleanup(enteredCount, listener -> listener.phaseEnded(phaseScope));
  }

  public void fireSolvingEnded(SolverScope<Solution_> solverScope) {
    int enteredCount = setupSolvingStartedCount;
    setupSolvingStartedCount = -1;
    fireCleanup(enteredCount, listener -> listener.solvingEnded(solverScope));
  }

  private void fireCleanup(int enteredCount, Consumer<PhaseLifecycleListener<Solution_>> callback) {
    Throwable failure = null;
    int count = 0;
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      if (enteredCount >= 0 && count++ >= enteredCount) break;
      try {
        callback.accept(listener);
      } catch (RuntimeException | Error cleanupFailure) {
        if (failure == null) failure = cleanupFailure;
        else if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
      }
    }
    if (failure instanceof RuntimeException runtime) throw runtime;
    if (failure instanceof Error error) throw error;
  }

  public void fireSolvingError(SolverScope<Solution_> solverScope, Throwable exception) {
    for (PhaseLifecycleListener<Solution_> listener : List.copyOf(getEventListeners())) {
      try {
        listener.solvingError(solverScope, exception);
      } catch (Throwable cleanupFailure) {
        if (cleanupFailure != exception) {
          exception.addSuppressed(cleanupFailure);
        }
      }
    }
  }
}
