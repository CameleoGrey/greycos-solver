package greycos.solver.core.impl.phase.event;

import java.util.List;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.event.AbstractEventSupport;
import greycos.solver.core.impl.solver.scope.SolverScope;

/** Internal API. */
public final class PhaseLifecycleSupport<Solution_>
    extends AbstractEventSupport<PhaseLifecycleListener<Solution_>> {

  public void fireSolvingStarted(SolverScope<Solution_> solverScope) {
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      listener.solvingStarted(solverScope);
    }
  }

  public void firePhaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      listener.phaseStarted(phaseScope);
    }
  }

  public void fireStepStarted(AbstractStepScope<Solution_> stepScope) {
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      listener.stepStarted(stepScope);
    }
  }

  public void fireStepEnded(AbstractStepScope<Solution_> stepScope) {
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      listener.stepEnded(stepScope);
    }
  }

  public void firePhaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      listener.phaseEnded(phaseScope);
    }
  }

  public void fireSolvingEnded(SolverScope<Solution_> solverScope) {
    for (PhaseLifecycleListener<Solution_> listener : getEventListeners()) {
      listener.solvingEnded(solverScope);
    }
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
