package ai.greycos.solver.core.impl.localsearch.event;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.solver.event.SolverLifecycleListener;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @see LocalSearchPhaseLifecycleListenerAdapter
 */
public interface LocalSearchPhaseLifecycleListener<Solution_>
    extends SolverLifecycleListener<Solution_> {

  void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope);

  void stepStarted(LocalSearchStepScope<Solution_> stepScope);

  void stepEnded(LocalSearchStepScope<Solution_> stepScope);

  void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope);
}
