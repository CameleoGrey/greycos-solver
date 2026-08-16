package greycos.solver.core.impl.localsearch.decider.acceptor.tabu.size;

import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;

public sealed interface TabuSizeStrategy<Solution_> permits AbstractTabuSizeStrategy {

  /**
   * @param stepScope never null
   * @return {@code >= 0}
   */
  int determineTabuSize(LocalSearchStepScope<Solution_> stepScope);
}
