package ai.greycos.solver.core.impl.heuristic.thread;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.heuristic.move.Move;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe queue for move results that ensures moves are processed in the correct order. This
 * queue handles result aggregation from multiple move threads and provides exception propagation.
 *
 * @param <Solution_> the solution type, the class with the {@link
 *     ai.greycos.solver.core.api.domain.solution.PlanningSolution} annotation
 */
public class OrderByMoveIndexBlockingQueue<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderByMoveIndexBlockingQueue.class);

  public static class MoveResult<Solution_> {
    private final int moveThreadIndex;
    private final int stepIndex;
    private final int moveIndex;
    private final Move<Solution_> move;
    private final boolean moveDoable;
    private final Score<?> score;
    private final Throwable throwable;

    public MoveResult(
        int moveThreadIndex, int stepIndex, int moveIndex, Move<Solution_> move, Score<?> score) {
      this.moveThreadIndex = moveThreadIndex;
      this.stepIndex = stepIndex;
      this.moveIndex = moveIndex;
      this.move = move;
      this.moveDoable = true;
      this.score = score;
      this.throwable = null;
    }

    public MoveResult(int moveThreadIndex, int stepIndex, int moveIndex, Move<Solution_> move) {
      this.moveThreadIndex = moveThreadIndex;
      this.stepIndex = stepIndex;
      this.moveIndex = moveIndex;
      this.move = move;
      this.moveDoable = false;
      this.score = null;
      this.throwable = null;
    }

    public MoveResult(int moveThreadIndex, Throwable throwable) {
      this.moveThreadIndex = moveThreadIndex;
      this.stepIndex = Integer.MIN_VALUE;
      this.moveIndex = Integer.MIN_VALUE;
      this.move = null;
      this.moveDoable = false;
      this.score = null;
      this.throwable = throwable;
    }

    public int getMoveThreadIndex() {
      return moveThreadIndex;
    }

    public int getStepIndex() {
      return stepIndex;
    }

    public int getMoveIndex() {
      return moveIndex;
    }

    public Move<Solution_> getMove() {
      return move;
    }

    public boolean isMoveDoable() {
      return moveDoable;
    }

    public Score<?> getScore() {
      return score;
    }

    public Throwable getThrowable() {
      return throwable;
    }

    public boolean hasThrownException() {
      return throwable != null;
    }
  }

  private final BlockingQueue<MoveResult<Solution_>> innerQueue;
  private final Map<Integer, MoveResult<Solution_>> backlog;

  private int filterStepIndex = Integer.MIN_VALUE;
  private int nextMoveIndex = Integer.MIN_VALUE;

  public OrderByMoveIndexBlockingQueue(int capacity) {
    this.innerQueue = new ArrayBlockingQueue<>(capacity);
    this.backlog = new HashMap<>(capacity);
  }

  public void startNextStep(int stepIndex) {
    synchronized (this) {
      if (filterStepIndex >= stepIndex) {
        throw new IllegalStateException(
            "Impossible situation: stepIndex ("
                + stepIndex
                + ") is not greater than previous stepIndex ("
                + filterStepIndex
                + ").");
      }
      filterStepIndex = stepIndex;

      MoveResult<Solution_> exceptionResult =
          innerQueue.stream().filter(MoveResult::hasThrownException).findFirst().orElse(null);
      if (exceptionResult != null) {
        throw new IllegalStateException(
            "Move thread (" + exceptionResult.getMoveThreadIndex() + ") threw exception.",
            exceptionResult.getThrowable());
      }

      innerQueue.clear();
    }
    nextMoveIndex = 0;
    backlog.clear();
  }

  public void addMove(
      int moveThreadIndex, int stepIndex, int moveIndex, Move<Solution_> move, Score<?> score) {
    MoveResult<Solution_> result =
        new MoveResult<>(moveThreadIndex, stepIndex, moveIndex, move, score);
    synchronized (this) {
      if (result.getStepIndex() != filterStepIndex) {
        return;
      }
      innerQueue.add(result);
    }
  }

  public void addUndoableMove(
      int moveThreadIndex, int stepIndex, int moveIndex, Move<Solution_> move) {
    MoveResult<Solution_> result = new MoveResult<>(moveThreadIndex, stepIndex, moveIndex, move);
    synchronized (this) {
      if (result.getStepIndex() != filterStepIndex) {
        return;
      }
      innerQueue.add(result);
    }
  }

  public void addExceptionThrown(int moveThreadIndex, Throwable throwable) {
    MoveResult<Solution_> result = new MoveResult<>(moveThreadIndex, throwable);
    synchronized (this) {
      innerQueue.add(result);
    }
  }

  public MoveResult<Solution_> take() throws InterruptedException {
    final int moveIndex = nextMoveIndex++;

    MoveResult<Solution_> cached = backlog.remove(moveIndex);
    if (cached != null) {
      return cached;
    }

    while (true) {
      MoveResult<Solution_> result = innerQueue.take();

      if (result.hasThrownException()) {
        throw new IllegalStateException(
            "Move thread (" + result.getMoveThreadIndex() + ") threw exception.",
            result.getThrowable());
      }

      if (result.getStepIndex() != filterStepIndex) {
        continue;
      }

      if (result.getMoveIndex() == moveIndex) {
        return result;
      }

      backlog.put(result.getMoveIndex(), result);
    }
  }

  public boolean isEmpty() {
    return innerQueue.isEmpty();
  }

  public int size() {
    return innerQueue.size();
  }
}
