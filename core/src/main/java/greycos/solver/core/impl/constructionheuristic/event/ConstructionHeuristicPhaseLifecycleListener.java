package greycos.solver.core.impl.constructionheuristic.event;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.solver.event.SolverLifecycleListener;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @see ConstructionHeuristicPhaseLifecycleListenerAdapter
 */
public interface ConstructionHeuristicPhaseLifecycleListener<Solution_>
    extends SolverLifecycleListener<Solution_> {

  void phaseStarted(ConstructionHeuristicPhaseScope<Solution_> phaseScope);

  void stepStarted(ConstructionHeuristicStepScope<Solution_> stepScope);

  void stepEnded(ConstructionHeuristicStepScope<Solution_> stepScope);

  void phaseEnded(ConstructionHeuristicPhaseScope<Solution_> phaseScope);
}
