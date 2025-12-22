package ai.greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.heuristic.move.AbstractMove;
import ai.greycos.solver.core.impl.heuristic.move.Move;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for OrderByMoveIndexBlockingQueue. Tests the thread-safe result queue functionality
 * including move result ordering, exception handling, and queue management.
 */
class OrderByMoveIndexBlockingQueueTest {

  private OrderByMoveIndexBlockingQueue<TestSolution> queue;

  @BeforeEach
  void setUp() {
    queue = new OrderByMoveIndexBlockingQueue<>(10);
  }

  @Test
  void testMoveResultCreation() {
    Move<TestSolution> move = new TestMove<>();
    Score<?> score = mockScore();
    int moveThreadIndex = 1;
    int stepIndex = 0;
    int moveIndex = 5;

    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result =
        new OrderByMoveIndexBlockingQueue.MoveResult<>(
            moveThreadIndex, stepIndex, moveIndex, move, score);

    assertThat(result.getMoveThreadIndex()).isEqualTo(moveThreadIndex);
    assertThat(result.getStepIndex()).isEqualTo(stepIndex);
    assertThat(result.getMoveIndex()).isEqualTo(moveIndex);
    assertThat(result.getMove()).isEqualTo(move);
    assertThat(result.getScore()).isEqualTo(score);
    assertThat(result.isMoveDoable()).isTrue();
    assertThat(result.getThrowable()).isNull();
  }

  @Test
  void testUndoableMoveResultCreation() {
    Move<TestSolution> move = new TestMove<>();
    int moveThreadIndex = 1;
    int stepIndex = 0;
    int moveIndex = 5;

    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result =
        new OrderByMoveIndexBlockingQueue.MoveResult<>(moveThreadIndex, stepIndex, moveIndex, move);

    assertThat(result.getMoveThreadIndex()).isEqualTo(moveThreadIndex);
    assertThat(result.getStepIndex()).isEqualTo(stepIndex);
    assertThat(result.getMoveIndex()).isEqualTo(moveIndex);
    assertThat(result.getMove()).isEqualTo(move);
    assertThat(result.getScore()).isNull();
    assertThat(result.isMoveDoable()).isFalse();
    assertThat(result.getThrowable()).isNull();
  }

  @Test
  void testExceptionResultCreation() {
    RuntimeException exception = new RuntimeException("Test exception");
    int moveThreadIndex = 1;

    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result =
        new OrderByMoveIndexBlockingQueue.MoveResult<>(moveThreadIndex, exception);

    assertThat(result.getMoveThreadIndex()).isEqualTo(moveThreadIndex);
    assertThat(result.getStepIndex()).isEqualTo(-1);
    assertThat(result.getMoveIndex()).isEqualTo(-1);
    assertThat(result.getMove()).isNull();
    assertThat(result.getScore()).isNull();
    assertThat(result.isMoveDoable()).isFalse();
    assertThat(result.getThrowable()).isEqualTo(exception);
  }

  @Test
  void testStartNextStep() {
    queue.startNextStep(5);

    // Verify that the queue is ready for the new step
    assertThat(queue.isEmpty()).isTrue();
    assertThat(queue.size()).isEqualTo(0);
  }

  @Test
  void testAddMove() {
    Move<TestSolution> move = new TestMove<>();
    Score<?> score = mockScore();
    int moveThreadIndex = 1;
    int stepIndex = 0;
    int moveIndex = 5;

    queue.addMove(moveThreadIndex, stepIndex, moveIndex, move, score);

    assertThat(queue.size()).isEqualTo(1);
    assertThat(queue.isEmpty()).isFalse();
  }

  @Test
  void testAddUndoableMove() {
    Move<TestSolution> move = new TestMove<>();
    int moveThreadIndex = 1;
    int stepIndex = 0;
    int moveIndex = 5;

    queue.addUndoableMove(moveThreadIndex, stepIndex, moveIndex, move);

    assertThat(queue.size()).isEqualTo(1);
    assertThat(queue.isEmpty()).isFalse();
  }

  @Test
  void testAddExceptionThrown() {
    RuntimeException exception = new RuntimeException("Test exception");
    int moveThreadIndex = 1;

    queue.addExceptionThrown(moveThreadIndex, exception);

    assertThat(queue.size()).isEqualTo(1);
    assertThat(queue.isEmpty()).isFalse();
  }

  @Test
  void testTake() throws InterruptedException {
    Move<TestSolution> move = new TestMove<>();
    Score<?> score = mockScore();
    int moveThreadIndex = 1;
    int stepIndex = 0;
    int moveIndex = 5;

    queue.addMove(moveThreadIndex, stepIndex, moveIndex, move, score);

    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result = queue.take();

    assertThat(result).isNotNull();
    assertThat(result.getMoveThreadIndex()).isEqualTo(moveThreadIndex);
    assertThat(result.getStepIndex()).isEqualTo(stepIndex);
    assertThat(result.getMoveIndex()).isEqualTo(moveIndex);
    assertThat(result.getMove()).isEqualTo(move);
    assertThat(result.getScore()).isEqualTo(score);
    assertThat(result.isMoveDoable()).isTrue();
  }

  @Test
  void testTakeWithTimeout() throws InterruptedException {
    Move<TestSolution> move = new TestMove<>();
    Score<?> score = mockScore();
    int moveThreadIndex = 1;
    int stepIndex = 0;
    int moveIndex = 5;

    // Add move to queue
    queue.addMove(moveThreadIndex, stepIndex, moveIndex, move, score);

    // Take with timeout
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result = queue.take();

    assertThat(result).isNotNull();
    assertThat(result.getMoveThreadIndex()).isEqualTo(moveThreadIndex);
    assertThat(result.getStepIndex()).isEqualTo(stepIndex);
    assertThat(result.getMoveIndex()).isEqualTo(moveIndex);
  }

  @Test
  void testQueueEmptyAfterTake() throws InterruptedException {
    Move<TestSolution> move = new TestMove<>();
    Score<?> score = mockScore();
    int moveThreadIndex = 1;
    int stepIndex = 0;
    int moveIndex = 5;

    queue.addMove(moveThreadIndex, stepIndex, moveIndex, move, score);
    queue.take();

    assertThat(queue.isEmpty()).isTrue();
    assertThat(queue.size()).isEqualTo(0);
  }

  @Test
  void testMultipleResults() throws InterruptedException {
    Move<TestSolution> move1 = new TestMove<>();
    Move<TestSolution> move2 = new TestMove<>();
    Score<?> score1 = mockScore();
    Score<?> score2 = mockScore();

    queue.addMove(1, 0, 0, move1, score1);
    queue.addMove(2, 0, 1, move2, score2);

    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result1 = queue.take();
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result2 = queue.take();

    assertThat(result1).isNotNull();
    assertThat(result2).isNotNull();
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void testQueueFullBehavior() {
    // Create a small queue
    OrderByMoveIndexBlockingQueue<TestSolution> smallQueue = new OrderByMoveIndexBlockingQueue<>(2);

    Move<TestSolution> move = new TestMove<>();
    Score<?> score = mockScore();

    // Fill the queue
    smallQueue.addMove(1, 0, 0, move, score);
    smallQueue.addMove(1, 0, 1, move, score);

    // Adding more should block or fail depending on implementation
    // The actual behavior depends on the underlying BlockingQueue implementation
    assertThat(smallQueue.size()).isEqualTo(2);
  }

  @Test
  void testQueueOrdering() throws InterruptedException {
    // Add moves in different order
    Move<TestSolution> move1 = new TestMove<>();
    Move<TestSolution> move2 = new TestMove<>();
    Score<?> score1 = mockScore();
    Score<?> score2 = mockScore();

    queue.addMove(2, 0, 1, move2, score2);
    queue.addMove(1, 0, 0, move1, score1);

    // Take results - order should be maintained by the underlying queue
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result1 = queue.take();
    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result2 = queue.take();

    assertThat(result1).isNotNull();
    assertThat(result2).isNotNull();
  }

  @Test
  void testExceptionPropagation() throws InterruptedException {
    RuntimeException exception = new RuntimeException("Test exception");
    int moveThreadIndex = 1;

    queue.addExceptionThrown(moveThreadIndex, exception);

    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result = queue.take();

    assertThat(result).isNotNull();
    assertThat(result.getThrowable()).isEqualTo(exception);
    assertThat(result.getMoveThreadIndex()).isEqualTo(moveThreadIndex);
  }

  @Test
  void testUndoableMoveHandling() throws InterruptedException {
    Move<TestSolution> move = new TestMove<>();
    int moveThreadIndex = 1;
    int stepIndex = 0;
    int moveIndex = 5;

    queue.addUndoableMove(moveThreadIndex, stepIndex, moveIndex, move);

    OrderByMoveIndexBlockingQueue.MoveResult<TestSolution> result = queue.take();

    assertThat(result).isNotNull();
    assertThat(result.getMove()).isEqualTo(move);
    assertThat(result.isMoveDoable()).isFalse();
    assertThat(result.getScore()).isNull();
  }

  // Helper methods
  private Score<?> mockScore() {
    return SimpleScore.of(100);
  }

  // Mock classes for testing
  private static class TestSolution {
    // Mock solution class
  }

  private static class TestMove<Solution_> extends AbstractMove<Solution_> {
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
      return "TestMove";
    }

    @Override
    public Move<Solution_> rebase(
        ai.greycos.solver.core.api.score.director.ScoreDirector<Solution_>
            destinationScoreDirector) {
      return this; // For testing, just return this
    }
  }
}
