package greycos.solver.core.impl.localsearch.decider.acceptor;

import static org.mockito.Mockito.mock;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.preview.api.move.Move;

public abstract class AbstractAcceptorTest {

  protected <Solution_> LocalSearchMoveScope<Solution_> buildMoveScope(
      LocalSearchStepScope<Solution_> stepScope, int score) {
    Move<Solution_> move = mock(Move.class);
    var moveScope = new LocalSearchMoveScope<>(stepScope, 0, move);
    moveScope.setInitializedScore(SimpleScore.of(score));
    return moveScope;
  }
}
