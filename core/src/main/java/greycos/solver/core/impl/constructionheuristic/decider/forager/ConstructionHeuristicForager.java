package greycos.solver.core.impl.constructionheuristic.decider.forager;

import greycos.solver.core.impl.constructionheuristic.event.ConstructionHeuristicPhaseLifecycleListener;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;

/**
 * @see AbstractConstructionHeuristicForager
 */
public interface ConstructionHeuristicForager<Solution_>
    extends ConstructionHeuristicPhaseLifecycleListener<Solution_> {

  void addMove(ConstructionHeuristicMoveScope<Solution_> moveScope);

  boolean isQuitEarly();

  ConstructionHeuristicMoveScope<Solution_> pickMove(
      ConstructionHeuristicStepScope<Solution_> stepScope);
}
