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

  /**
   * Receives the final acceptance decision before the forager considers this evaluated move. {@link
   * LocalSearchMoveScope#getAccepted()} contains the decision of the entire acceptor, including all
   * children of a composite. Acceptance does not imply that the move will be executed; the chosen
   * transition is reported by {@code stepEnded()}.
   *
   * @param moveScope the evaluated move with its final accepted flag set
   */
  default void moveEvaluated(LocalSearchMoveScope<Solution_> moveScope) {
    // Most acceptors only need the selected step.
  }

  /**
   * Whether move evaluation must retain the planning values needed by this acceptor.
   *
   * @return true if planning values are required for acceptance or subsequent step bookkeeping
   */
  default boolean requiresPlanningValues() {
    return false;
  }
}
