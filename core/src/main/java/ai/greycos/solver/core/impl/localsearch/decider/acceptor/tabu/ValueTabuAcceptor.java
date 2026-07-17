package ai.greycos.solver.core.impl.localsearch.decider.acceptor.tabu;

import java.util.SequencedCollection;

import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ValueTabuAcceptor<Solution_> extends AbstractTabuAcceptor<Solution_> {

  public ValueTabuAcceptor(String logIndentation) {
    super(logIndentation);
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  protected SequencedCollection<@Nullable Object> findTabu(
      LocalSearchMoveScope<Solution_> moveScope) {
    return moveScope.getMove().getPlanningValues();
  }

  @Override
  protected SequencedCollection<@Nullable Object> findNewTabu(
      LocalSearchStepScope<Solution_> stepScope) {
    return stepScope.getStep().getPlanningValues();
  }
}
