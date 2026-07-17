package ai.greycos.solver.core.impl.localsearch.decider.acceptor.tabu;

import java.util.SequencedCollection;

import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class EntityTabuAcceptor<Solution_> extends AbstractTabuAcceptor<Solution_> {

  public EntityTabuAcceptor(String logIndentation) {
    super(logIndentation);
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  protected SequencedCollection<Object> findTabu(LocalSearchMoveScope<Solution_> moveScope) {
    return moveScope.getMove().getPlanningEntities();
  }

  @Override
  protected SequencedCollection<Object> findNewTabu(LocalSearchStepScope<Solution_> stepScope) {
    return stepScope.getStep().getPlanningEntities();
  }
}
