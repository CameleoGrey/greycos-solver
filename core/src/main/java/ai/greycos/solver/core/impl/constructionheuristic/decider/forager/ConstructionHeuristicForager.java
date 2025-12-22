package ai.greycos.solver.core.impl.constructionheuristic.decider.forager;

import ai.greycos.solver.core.impl.constructionheuristic.event.ConstructionHeuristicPhaseLifecycleListener;
import ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;

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
