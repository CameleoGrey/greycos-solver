package ai.greycos.solver.core.impl.solver.scope;

import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.preview.api.move.Move;
import ai.greycos.solver.core.preview.api.move.MutableSolutionView;

import org.junit.jupiter.api.Test;

class SolverScopeTest {

  @Test
  void pendingMoveIfBetterReplacesWithHigherScore() {
    SolverScope<Object> solverScope = new SolverScope<>();
    Move<Object> weakerMove = new TestMove("weaker");
    Move<Object> strongerMove = new TestMove("stronger");

    solverScope.setPendingMoveIfBetter(
        weakerMove, InnerScore.fullyAssigned(SimpleScore.of(10)), true);
    solverScope.setPendingMoveIfBetter(
        strongerMove, InnerScore.fullyAssigned(SimpleScore.of(20)), true);

    var pendingMove = solverScope.consumePendingMove();
    assertThat(pendingMove).isNotNull();
    assertThat(pendingMove.move()).isSameAs(strongerMove);
    assertThat(pendingMove.requiresReset()).isTrue();
  }

  @Test
  void pendingMoveIfBetterKeepsCurrentWhenCandidateIsWorseByInitialization() {
    SolverScope<Object> solverScope = new SolverScope<>();
    Move<Object> fullyAssignedMove = new TestMove("fullyAssigned");
    Move<Object> unassignedCandidateMove = new TestMove("unassignedCandidate");

    solverScope.setPendingMoveIfBetter(
        fullyAssignedMove, InnerScore.fullyAssigned(SimpleScore.of(0)), true);
    solverScope.setPendingMoveIfBetter(
        unassignedCandidateMove, InnerScore.withUnassignedCount(SimpleScore.of(1000), 1), true);

    var pendingMove = solverScope.consumePendingMove();
    assertThat(pendingMove).isNotNull();
    assertThat(pendingMove.move()).isSameAs(fullyAssignedMove);
  }

  @Test
  void pendingMoveIfBetterReplacesUnscoredPendingMove() {
    SolverScope<Object> solverScope = new SolverScope<>();
    Move<Object> unscoredMove = new TestMove("unscored");
    Move<Object> scoredMove = new TestMove("scored");

    solverScope.setPendingMove(unscoredMove, true);
    solverScope.setPendingMoveIfBetter(scoredMove, InnerScore.fullyAssigned(SimpleScore.ONE), true);

    var pendingMove = solverScope.consumePendingMove();
    assertThat(pendingMove).isNotNull();
    assertThat(pendingMove.move()).isSameAs(scoredMove);
  }

  private record TestMove(String id) implements Move<Object> {
    @Override
    public void execute(MutableSolutionView<Object> solutionView) {}
  }
}
