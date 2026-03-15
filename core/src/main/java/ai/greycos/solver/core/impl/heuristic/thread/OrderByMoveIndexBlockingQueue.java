package ai.greycos.solver.core.impl.heuristic.thread;

import java.util.Arrays;
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
 *     ai.greycos.solver.core.api.cotwin.solution.PlanningSolution} annotation
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
  private final MoveResult<Solution_>[] backlogBySlot;
  private final int[] backlogMoveIndexBySlot;

  private volatile RuntimeException pendingThreadFailure = null;
  private volatile int filterStepIndex = Integer.MIN_VALUE;
  private int nextMoveIndex = Integer.MIN_VALUE;

  @SuppressWarnings("unchecked")
  public OrderByMoveIndexBlockingQueue(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("Queue capacity (" + capacity + ") must be > 0.");
    }
    this.innerQueue = new ArrayBlockingQueue<>(capacity);
    this.backlogBySlot = (MoveResult<Solution_>[]) new MoveResult<?>[capacity];
    this.backlogMoveIndexBySlot = new int[capacity];
    Arrays.fill(backlogMoveIndexBySlot, Integer.MIN_VALUE);
  }

  public void startNextStep(int stepIndex) {
    if (filterStepIndex >= stepIndex) {
      throw new IllegalStateException(
          "Impossible situation: stepIndex ("
              + stepIndex
              + ") is not greater than previous stepIndex ("
              + filterStepIndex
              + ").");
    }
    filterStepIndex = stepIndex;
    nextMoveIndex = 0;
    clearBacklog();

    MoveResult<Solution_> drainedResult;
    while ((drainedResult = innerQueue.poll()) != null) {
      if (drainedResult.hasThrownException()) {
        setPendingThreadFailureIfAbsent(
            createMoveThreadException(
                drainedResult.getMoveThreadIndex(), drainedResult.getThrowable()));
      }
    }

    RuntimeException threadFailure = pendingThreadFailure;
    if (threadFailure != null) {
      throw threadFailure;
    }
  }

  public void addMove(
      int moveThreadIndex, int stepIndex, int moveIndex, Move<Solution_> move, Score<?> score) {
    if (stepIndex != filterStepIndex) {
      return;
    }
    MoveResult<Solution_> result =
        new MoveResult<>(moveThreadIndex, stepIndex, moveIndex, move, score);
    if (!innerQueue.offer(result)) {
      throw new IllegalStateException(
          "Impossible situation: result queue is full while adding move index ("
              + moveIndex
              + ").");
    }
  }

  public void addUndoableMove(
      int moveThreadIndex, int stepIndex, int moveIndex, Move<Solution_> move) {
    if (stepIndex != filterStepIndex) {
      return;
    }
    MoveResult<Solution_> result = new MoveResult<>(moveThreadIndex, stepIndex, moveIndex, move);
    if (!innerQueue.offer(result)) {
      throw new IllegalStateException(
          "Impossible situation: result queue is full while adding undoable move index ("
              + moveIndex
              + ").");
    }
  }

  public void addExceptionThrown(int moveThreadIndex, Throwable throwable) {
    setPendingThreadFailureIfAbsent(createMoveThreadException(moveThreadIndex, throwable));
    if (!innerQueue.offer(new MoveResult<>(moveThreadIndex, throwable))) {
      LOGGER.warn(
          "Move thread ({}) failure could not enqueue exception marker because queue is full.",
          moveThreadIndex);
    }
  }

  public MoveResult<Solution_> take() throws InterruptedException {
    final int moveIndex = nextMoveIndex++;
    MoveResult<Solution_> cached = removeBacklog(moveIndex);
    if (cached != null) {
      return cached;
    }

    RuntimeException threadFailure = pendingThreadFailure;
    if (threadFailure != null) {
      throw threadFailure;
    }

    while (true) {
      MoveResult<Solution_> result = innerQueue.take();

      if (result.hasThrownException()) {
        RuntimeException pendingException = pendingThreadFailure;
        if (pendingException != null) {
          throw pendingException;
        }
        throw createMoveThreadException(result.getMoveThreadIndex(), result.getThrowable());
      }

      if (result.getStepIndex() != filterStepIndex) {
        continue;
      }

      if (result.getMoveIndex() == moveIndex) {
        return result;
      }

      putBacklog(result);

      threadFailure = pendingThreadFailure;
      if (threadFailure != null) {
        throw threadFailure;
      }
    }
  }

  public boolean isEmpty() {
    return innerQueue.isEmpty();
  }

  public int size() {
    return innerQueue.size();
  }

  private void clearBacklog() {
    Arrays.fill(backlogBySlot, null);
    Arrays.fill(backlogMoveIndexBySlot, Integer.MIN_VALUE);
  }

  private void putBacklog(MoveResult<Solution_> result) {
    int slot = result.getMoveIndex() % backlogBySlot.length;
    int existingMoveIndex = backlogMoveIndexBySlot[slot];
    if (existingMoveIndex != Integer.MIN_VALUE && existingMoveIndex != result.getMoveIndex()) {
      throw new IllegalStateException(
          "Impossible situation: backlog slot collision for move index ("
              + result.getMoveIndex()
              + ").");
    }
    backlogBySlot[slot] = result;
    backlogMoveIndexBySlot[slot] = result.getMoveIndex();
  }

  private MoveResult<Solution_> removeBacklog(int moveIndex) {
    int slot = moveIndex % backlogBySlot.length;
    if (backlogMoveIndexBySlot[slot] != moveIndex) {
      return null;
    }
    MoveResult<Solution_> result = backlogBySlot[slot];
    backlogBySlot[slot] = null;
    backlogMoveIndexBySlot[slot] = Integer.MIN_VALUE;
    return result;
  }

  private RuntimeException createMoveThreadException(int moveThreadIndex, Throwable throwable) {
    return new IllegalStateException(
        "Move thread (" + moveThreadIndex + ") threw exception.", throwable);
  }

  private void setPendingThreadFailureIfAbsent(RuntimeException runtimeException) {
    if (pendingThreadFailure == null) {
      pendingThreadFailure = runtimeException;
    }
  }
}
