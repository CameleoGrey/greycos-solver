package greycos.solver.core.impl.phase.event;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.event.SolverLifecycleListener;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @see PhaseLifecycleListenerAdapter
 */
public interface PhaseLifecycleListener<Solution_> extends SolverLifecycleListener<Solution_> {

  void phaseStarted(AbstractPhaseScope<Solution_> phaseScope);

  void stepStarted(AbstractStepScope<Solution_> stepScope);

  void stepEnded(AbstractStepScope<Solution_> stepScope);

  void phaseEnded(AbstractPhaseScope<Solution_> phaseScope);
}
