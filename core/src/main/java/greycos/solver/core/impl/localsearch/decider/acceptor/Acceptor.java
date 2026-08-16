package greycos.solver.core.impl.localsearch.decider.acceptor;

import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.event.LocalSearchPhaseLifecycleListener;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.preview.api.move.Move;

/**
 * An Acceptor accepts or rejects a selected {@link Move}. Note that the {@link LocalSearchForager}
 * can still ignore the advice of the {@link Acceptor}.
 *
 * @see AbstractAcceptor
 */
public interface Acceptor<Solution_> extends LocalSearchPhaseLifecycleListener<Solution_> {

  /**
   * @param moveScope not null
   * @return true if accepted
   */
  boolean isAccepted(LocalSearchMoveScope<Solution_> moveScope);
}
