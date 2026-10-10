package greycos.solver.core.impl.phase.event;

import java.util.List;

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

  public void fireSolvingStarted(SolverScope<Solution_> solverScope) {
    boolean boundedSetup = SelectionAttemptContext.isSetup();
    if (boundedSetup) setupSolvingStartedCount = 0;
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      SelectionAttemptContext.beforeSelection();
      if (boundedSetup) setupSolvingStartedCount++;
      listener.solvingStarted(solverScope);
    }
    setupSolvingStartedCount = -1;
  }

  public void firePhaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    boolean boundedSetup = SelectionAttemptContext.isSetup();
    if (boundedSetup) setupPhaseStartedCount = 0;
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      SelectionAttemptContext.beforeSelection();
      if (boundedSetup) setupPhaseStartedCount++;
      listener.phaseStarted(phaseScope);
    }
    setupPhaseStartedCount = -1;
  }

  public void fireStepStarted(AbstractStepScope<Solution_> stepScope) {
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      SelectionAttemptContext.beforeSelection();
      listener.stepStarted(stepScope);
    }
  }

  public void fireStepEnded(AbstractStepScope<Solution_> stepScope) {
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      listener.stepEnded(stepScope);
    }
  }

  public void firePhaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    int count = 0;
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      if (setupPhaseStartedCount >= 0 && count++ >= setupPhaseStartedCount) break;
      listener.phaseEnded(phaseScope);
    }
    setupPhaseStartedCount = -1;
  }

  public void fireSolvingEnded(SolverScope<Solution_> solverScope) {
    int count = 0;
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      if (setupSolvingStartedCount >= 0 && count++ >= setupSolvingStartedCount) break;
      listener.solvingEnded(solverScope);
    }
    setupSolvingStartedCount = -1;
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
