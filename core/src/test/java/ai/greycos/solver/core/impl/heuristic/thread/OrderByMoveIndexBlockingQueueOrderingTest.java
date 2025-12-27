package ai.greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.heuristic.move.AbstractMove;
import ai.greycos.solver.core.impl.heuristic.move.Move;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for OrderByMoveIndexBlockingQueue ordering behavior. Verifies that results
 * are returned in moveIndex order regardless of arrival order.
 */
class OrderByMoveIndexBlockingQueueOrderingTest {

  private OrderByMoveIndexBlockingQueue<TestSolution> queue;

  @BeforeEach
  void setUp() {
    queue = new OrderByMoveIndexBlockingQueue<>(10);
  }

  @Test
  void testResultsReturnedInMoveIndexOrder() throws InterruptedException {
    queue.startNextStep(0);

    // Add moves in reverse order (5, 4, 3, 2, 1, 0)
    Move<TestSolution> move0 = new TestMove<>("move0");
    Move<TestSolution> move1 = new TestMove<>("move1");
    Move<TestSolution> move2 = new TestMove<>("move2");
    Move<TestSolution> move3 = new TestMove<>("move3");
    Move<TestSolution> move4 = new TestMove<>("move4");
    Move<TestSolution> move5 = new TestMove<>("move5");

    Score<?> score0 = SimpleScore.of(0);
    Score<?> score1 = SimpleScore.of(1);
    Score<?> score2 = SimpleScore.of(2);
    Score<?> score3 = SimpleScore.of(3);
    Score<?> score4 = SimpleScore.of(4);
    Score<?> score5 = SimpleScore.of(5);

    // Add in reverse order
    queue.addMove(0, 0, 5, move5, score5);
    queue.addMove(0, 0, 4, move4, score4);
    queue.addMove(0, 0, 3, move3, score3);
    queue.addMove(0, 0, 2, move2, score2);
    queue.addMove(0, 0, 1, move1, score1);
    queue.addMove(0, 0, 0, move0, score0);

    // Take results - they should come out in moveIndex order (0, 1, 2, 3, 4, 5)
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result0 = queue.take();
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result1 = queue.take();
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result2 = queue.take();
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result3 = queue.take();
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result4 = queue.take();
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result5 = queue.take();

    assertThat(result0.getMoveIndex()).isEqualTo(0);
    assertThat(result0.getMove()).isEqualTo(move0);
    assertThat(result0.getScore()).isEqualTo(score0);

    assertThat(result1.getMoveIndex()).isEqualTo(1);
    assertThat(result1.getMove()).isEqualTo(move1);
    assertThat(result1.getScore()).isEqualTo(score1);

    assertThat(result2.getMoveIndex()).isEqualTo(2);
    assertThat(result2.getMove()).isEqualTo(move2);
    assertThat(result2.getScore()).isEqualTo(score2);

    assertThat(result3.getMoveIndex()).isEqualTo(3);
    assertThat(result3.getMove()).isEqualTo(move3);
    assertThat(result3.getScore()).isEqualTo(score3);

    assertThat(result4.getMoveIndex()).isEqualTo(4);
    assertThat(result4.getMove()).isEqualTo(move4);
    assertThat(result4.getScore()).isEqualTo(score4);

    assertThat(result5.getMoveIndex()).isEqualTo(5);
    assertThat(result5.getMove()).isEqualTo(move5);
    assertThat(result5.getScore()).isEqualTo(score5);
  }

  @Test
  void testBacklogMechanismWithGaps() throws InterruptedException {
    queue.startNextStep(0);

    Move<TestSolution> move0 = new TestMove<>("move0");
    Move<TestSolution> move1 = new TestMove<>("move1");
    Move<TestSolution> move2 = new TestMove<>("move2");

    Score<?> score0 = SimpleScore.of(0);
    Score<?> score1 = SimpleScore.of(1);
    Score<?> score2 = SimpleScore.of(2);

    // Add moves out of order with gaps: add 2, then 0, then 1
    queue.addMove(0, 0, 2, move2, score2);
    queue.addMove(0, 0, 0, move0, score0);
    queue.addMove(0, 0, 1, move1, score1);

    // Take results - should still come out in order
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result0 = queue.take();
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result1 = queue.take();
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result2 = queue.take();

    assertThat(result0.getMoveIndex()).isEqualTo(0);
    assertThat(result1.getMoveIndex()).isEqualTo(1);
    assertThat(result2.getMoveIndex()).isEqualTo(2);
  }

  @Test
  void testStepIndexFiltering() throws InterruptedException {
    queue.startNextStep(0);

    Move<TestSolution> move0 = new TestMove<>("move0");
    Score<?> score0 = SimpleScore.of(0);

    // Add move for step 0
    queue.addMove(0, 0, 0, move0, score0);

    // Start next step
    queue.startNextStep(1);

    Move<TestSolution> move1 = new TestMove<>("move1");
    Score<?> score1 = SimpleScore.of(1);

    // Add move for step 1
    queue.addMove(0, 1, 0, move1, score1);

    // Try to add stale move for step 0 - should be filtered out
    Move<TestSolution> staleMove = new TestMove<>("stale");
    Score<?> staleScore = SimpleScore.of(-1);
    queue.addMove(0, 0, 1, staleMove, staleScore);

    // Take should only return the step 1 move
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result = queue.take();

    assertThat(result.getStepIndex()).isEqualTo(1);
    assertThat(result.getMove()).isEqualTo(move1);
    assertThat(result.getScore()).isEqualTo(score1);
  }

  @Test
  void testExceptionPropagationInTake() {
    queue.startNextStep(0);

    RuntimeException exception = new RuntimeException("Test exception");
    queue.addExceptionThrown(0, exception);

    // take() should throw IllegalStateException with the original exception as cause
    assertThatThrownBy(() -> queue.take())
        .isInstanceOf(IllegalStateException.class)
        .hasCause(exception)
        .hasMessageContaining("Move thread (0) threw exception");
  }

  @Test
  void testExceptionPropagationInStartNextStep() {
    queue.startNextStep(0);

    RuntimeException exception = new RuntimeException("Test exception");
    queue.addExceptionThrown(0, exception);

    // startNextStep should throw IllegalStateException with the original exception
    assertThatThrownBy(() -> queue.startNextStep(1))
        .isInstanceOf(IllegalStateException.class)
        .hasCause(exception)
        .hasMessageContaining("Move thread (0) threw exception");
  }

  @Test
  void testUndoableMoveOrdering() throws InterruptedException {
    queue.startNextStep(0);

    Move<TestSolution> move0 = new TestMove<>("move0");
    Move<TestSolution> move1 = new TestMove<>("move1");

    // Add undoable move
    queue.addUndoableMove(0, 0, 1, move1);

    // Add doable move
    Score<?> score0 = SimpleScore.of(0);
    queue.addMove(0, 0, 0, move0, score0);

    // Results should come out in moveIndex order
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result0 = queue.take();
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result1 = queue.take();

    assertThat(result0.getMoveIndex()).isEqualTo(0);
    assertThat(result0.isMoveDoable()).isTrue();

    assertThat(result1.getMoveIndex()).isEqualTo(1);
    assertThat(result1.isMoveDoable()).isFalse();
  }

  @Test
  void testMixedMovesOrdering() throws InterruptedException {
    queue.startNextStep(0);

    Move<TestSolution> move0 = new TestMove<>("move0");
    Move<TestSolution> move1 = new TestMove<>("move1");
    Move<TestSolution> move2 = new TestMove<>("move2");

    Score<?> score0 = SimpleScore.of(0);
    Score<?> score2 = SimpleScore.of(2);

    // Add in mixed order: doable(0), undoable(2), doable(1)
    queue.addMove(0, 0, 0, move0, score0);
    queue.addUndoableMove(0, 0, 2, move2);
    queue.addMove(0, 0, 1, move1, score0);

    // Results should come out in moveIndex order
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result0 = queue.take();
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result1 = queue.take();
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result2 = queue.take();

    assertThat(result0.getMoveIndex()).isEqualTo(0);
    assertThat(result0.isMoveDoable()).isTrue();

    assertThat(result1.getMoveIndex()).isEqualTo(1);
    assertThat(result1.isMoveDoable()).isTrue();

    assertThat(result2.getMoveIndex()).isEqualTo(2);
    assertThat(result2.isMoveDoable()).isFalse();
  }

  @Test
  void testBacklogClearedOnNewStep() throws InterruptedException {
    queue.startNextStep(0);

    Move<TestSolution> move0 = new TestMove<>("move0");
    Score<?> score0 = SimpleScore.of(0);

    // Add move for step 0
    queue.addMove(0, 0, 0, move0, score0);

    // Start next step - should clear backlog
    queue.startNextStep(1);

    Move<TestSolution> move1 = new TestMove<>("move1");
    Score<?> score1 = SimpleScore.of(1);

    // Add move for step 1
    queue.addMove(0, 1, 0, move1, score1);

    // Take should return step 1 move
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result = queue.take();

    assertThat(result.getStepIndex()).isEqualTo(1);
    assertThat(result.getMove()).isEqualTo(move1);
  }

  @Test
  void testMultipleSteps() throws InterruptedException {
    // Step 0
    queue.startNextStep(0);

    Move<TestSolution> move0 = new TestMove<>("move0");
    Score<?> score0 = SimpleScore.of(0);
    queue.addMove(0, 0, 0, move0, score0);

    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result0 = queue.take();
    assertThat(result0.getStepIndex()).isEqualTo(0);
    assertThat(result0.getMoveIndex()).isEqualTo(0);

    // Step 1
    queue.startNextStep(1);

    Move<TestSolution> move1 = new TestMove<>("move1");
    Score<?> score1 = SimpleScore.of(1);
    queue.addMove(0, 1, 0, move1, score1);

    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result1 = queue.take();
    assertThat(result1.getStepIndex()).isEqualTo(1);
    assertThat(result1.getMoveIndex()).isEqualTo(0);

    // Step 2
    queue.startNextStep(2);

    Move<TestSolution> move2 = new TestMove<>("move2");
    Score<?> score2 = SimpleScore.of(2);
    queue.addMove(0, 2, 0, move2, score2);

    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result2 = queue.take();
    assertThat(result2.getStepIndex()).isEqualTo(2);
    assertThat(result2.getMoveIndex()).isEqualTo(0);
  }

  @Test
  void testLargeGapInMoveIndices() throws InterruptedException {
    queue.startNextStep(0);

    Move<TestSolution> move0 = new TestMove<>("move0");
    Move<TestSolution> move100 = new TestMove<>("move100");

    Score<?> score0 = SimpleScore.of(0);
    Score<?> score100 = SimpleScore.of(100);

    // Add move 100 first
    queue.addMove(0, 0, 100, move100, score100);

    // Add move 0
    queue.addMove(0, 0, 0, move0, score0);

    // Take should return move 0 first
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result0 = queue.take();
    assertThat(result0.getMoveIndex()).isEqualTo(0);
    assertThat(result0.getMove()).isEqualTo(move0);

    // Move 100 should be in backlog, take it next
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result100 = queue.take();
    assertThat(result100.getMoveIndex()).isEqualTo(100);
    assertThat(result100.getMove()).isEqualTo(move100);
  }

  // Helper classes
  private static class TestSolution {
    // Mock solution class
  }

  private static class TestMove<Solution_> extends AbstractMove<Solution_> {
    private final String name;

    TestMove(String name) {
      this.name = name;
    }

    @Override
    public boolean isMoveDoable(
        ai.greycos.solver.core.api.score.director.ScoreDirector<Solution_> scoreDirector) {
      return true;
    }

    @Override
    protected void doMoveOnGenuineVariables(
        ai.greycos.solver.core.api.score.director.ScoreDirector<Solution_> scoreDirector) {
      // Mock implementation
    }

    @Override
    public String getSimpleMoveTypeDescription() {
      return name;
    }

    @Override
    public Move<Solution_> rebase(
        ai.greycos.solver.core.api.score.director.ScoreDirector<Solution_>
            destinationScoreDirector) {
      return this; // For testing, just return this
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
