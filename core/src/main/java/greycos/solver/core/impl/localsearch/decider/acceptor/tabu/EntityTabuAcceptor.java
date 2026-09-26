package greycos.solver.core.impl.localsearch.decider.acceptor.tabu;

import java.util.SequencedCollection;

import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.move.PlanningEntityResolver;

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
    var move = moveScope.getMove();
    // Direct evaluation has already been undone; threaded evaluation executed a rebased copy.
    // Resolve from the coordinator's current state in both cases, never from an earlier execution.
    return move instanceof PlanningEntityResolver<Solution_> resolver
        ? resolver.resolvePlanningEntities(moveScope.getScoreDirector().getMoveDirector())
        : move.getPlanningEntities();
  }

  @Override
  protected SequencedCollection<Object> findNewTabu(LocalSearchStepScope<Solution_> stepScope) {
    return stepScope.getStep().getPlanningEntities();
  }
}
