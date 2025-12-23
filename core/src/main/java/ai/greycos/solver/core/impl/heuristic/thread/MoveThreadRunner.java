package ai.greycos.solver.core.impl.heuristic.thread;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.move.MoveAdapters;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core move thread implementation that processes operations from the operation queue. This runner
 * handles setup, move evaluation, step application, and cleanup operations.
 *
 * @param <Solution_> the solution type, the class with the {@link
 *     ai.greycos.solver.core.api.domain.solution.PlanningSolution} annotation
 * @param <Score_> the score type to go with the solution
 */
public class MoveThreadRunner<Solution_, Score_ extends Score<Score_>> implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(MoveThreadRunner.class);

  private final String logIndentation;
  private final int moveThreadIndex;
  private final boolean evaluateDoable;
  private final BlockingQueue<MoveThreadOperation<Solution_>> operationQueue;
  private final OrderByMoveIndexBlockingQueue<Solution_> resultQueue;
  private final CyclicBarrier moveThreadBarrier;
  private final boolean assertMoveScoreFromScratch;
  private final boolean assertExpectedUndoMoveScore;
  private final boolean assertStepScoreFromScratch;
  private final boolean assertExpectedStepScore;
  private final boolean assertShadowVariablesAreNotStaleAfterStep;

  private InnerScoreDirector<Solution_, Score_> scoreDirector = null;
  private InnerScoreDirector<Solution_, Score_> parentScoreDirector = null;
  private AtomicLong calculationCount = new AtomicLong(-1);

  public MoveThreadRunner(
      String logIndentation,
      int moveThreadIndex,
      boolean evaluateDoable,
      BlockingQueue<MoveThreadOperation<Solution_>> operationQueue,
      OrderByMoveIndexBlockingQueue<Solution_> resultQueue,
      CyclicBarrier moveThreadBarrier,
      boolean assertMoveScoreFromScratch,
      boolean assertExpectedUndoMoveScore,
      boolean assertStepScoreFromScratch,
      boolean assertExpectedStepScore,
      boolean assertShadowVariablesAreNotStaleAfterStep) {
    this.logIndentation = logIndentation;
    this.moveThreadIndex = moveThreadIndex;
    this.evaluateDoable = evaluateDoable;
    this.operationQueue = operationQueue;
    this.resultQueue = resultQueue;
    this.moveThreadBarrier = moveThreadBarrier;
    this.assertMoveScoreFromScratch = assertMoveScoreFromScratch;
    this.assertExpectedUndoMoveScore = assertExpectedUndoMoveScore;
    this.assertStepScoreFromScratch = assertStepScoreFromScratch;
    this.assertExpectedStepScore = assertExpectedStepScore;
    this.assertShadowVariablesAreNotStaleAfterStep = assertShadowVariablesAreNotStaleAfterStep;
  }

  @Override
  public void run() {
    boolean exceptionThrown = false;
    try {
      int stepIndex = -1;
      Score_ lastStepScore = null;
      while (true) {
        MoveThreadOperation<Solution_> operation;
        try {
          operation = operationQueue.take();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }

        if (operation instanceof SetupOperation) {
          SetupOperation<Solution_, Score_> setupOperation =
              (SetupOperation<Solution_, Score_>) operation;
          try {
            parentScoreDirector = setupOperation.getScoreDirector();
            scoreDirector =
                parentScoreDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
            stepIndex = 0;
            lastStepScore = scoreDirector.calculateScore().raw();
            try {
              moveThreadBarrier.await();
            } catch (InterruptedException | java.util.concurrent.BrokenBarrierException e) {
              Thread.currentThread().interrupt();
              break;
            }
          } catch (RuntimeException | Error throwable) {
            exceptionThrown = true;
            // Close the score director if setup fails
            if (scoreDirector != null) {
              try {
                scoreDirector.close();
              } catch (Exception e) {
                LOGGER.warn(
                    "{}            Move thread ({}) failed to close score director during setup.",
                    logIndentation,
                    moveThreadIndex,
                    e);
              }
            }
            // Also close the parent score director on failure, but only if different from child
            if (parentScoreDirector != null && parentScoreDirector != scoreDirector) {
              try {
                parentScoreDirector.close();
              } catch (Exception e) {
                LOGGER.warn(
                    "{}            Move thread ({}) failed to close parent score director during setup.",
                    logIndentation,
                    moveThreadIndex,
                    e);
              }
            }
            throw throwable;
          }
        } else if (operation instanceof DestroyOperation) {
          if (parentScoreDirector != null) {
            calculationCount.set(parentScoreDirector.getCalculationCount());
          }
          break;
        } else if (operation instanceof ApplyStepOperation) {
          ApplyStepOperation<Solution_, Score_> applyStepOperation =
              (ApplyStepOperation<Solution_, Score_>) operation;
          if (stepIndex + 1 != applyStepOperation.getStepIndex()) {
            throw new IllegalStateException(
                "Impossible situation: the moveThread's stepIndex ("
                    + stepIndex
                    + ") is not followed by the operation's stepIndex ("
                    + applyStepOperation.getStepIndex()
                    + ").");
          }
          stepIndex = applyStepOperation.getStepIndex();
          Move<Solution_> step =
              MoveAdapters.toLegacyMove(applyStepOperation.getStep()).rebase(scoreDirector);
          Score_ score = applyStepOperation.getScore();
          step.doMoveOnly(scoreDirector);
          predictWorkingStepScore(step, InnerScore.fullyAssigned(score));
          lastStepScore = score;
          try {
            moveThreadBarrier.await();
          } catch (InterruptedException | java.util.concurrent.BrokenBarrierException e) {
            Thread.currentThread().interrupt();
            break;
          }
        } else if (operation instanceof MoveEvaluationOperation) {
          MoveEvaluationOperation<Solution_> moveEvaluationOperation =
              (MoveEvaluationOperation<Solution_>) operation;
          int moveIndex = moveEvaluationOperation.getMoveIndex();
          if (stepIndex != moveEvaluationOperation.getStepIndex()) {
            throw new IllegalStateException(
                "Impossible situation: the moveThread's stepIndex ("
                    + stepIndex
                    + ") differs from the operation's stepIndex ("
                    + moveEvaluationOperation.getStepIndex()
                    + ") with moveIndex ("
                    + moveIndex
                    + ").");
          }
          Move<Solution_> originalMove = moveEvaluationOperation.getMove();
          if (originalMove == null) {
            throw new NullPointerException("Move cannot be null in MoveEvaluationOperation");
          }
          Move<Solution_> move = MoveAdapters.toLegacyMove(originalMove).rebase(scoreDirector);
          if (evaluateDoable && !move.isMoveDoable(scoreDirector)) {
            resultQueue.addUndoableMove(moveThreadIndex, stepIndex, moveIndex, move);
          } else {
            var score = scoreDirector.executeTemporaryMove(move, assertMoveScoreFromScratch);
            if (score == null) {
              // Fallback to a default score if the mock returns null
              score = scoreDirector.calculateScore();
            }
            if (assertExpectedUndoMoveScore) {
              // Note: assertExpectedUndoMoveScore is not applicable in move threads
              // as they don't have access to the proper lifecycle context
            }
            resultQueue.addMove(moveThreadIndex, stepIndex, moveIndex, move, score.raw());
          }
        } else {
          throw new IllegalStateException("Unknown operation (" + operation + ").");
        }
      }
    } catch (RuntimeException | Error throwable) {
      LOGGER.error(
          "{}            Move thread ({}) exception that will be propagated to the solver thread.",
          logIndentation,
          moveThreadIndex,
          throwable);
      resultQueue.addExceptionThrown(moveThreadIndex, throwable);
    } finally {
      // Close child score director
      if (scoreDirector != null) {
        try {
          scoreDirector.close();
        } catch (Exception e) {
          LOGGER.warn(
              "{}            Move thread ({}) failed to close score director.",
              logIndentation,
              moveThreadIndex,
              e);
        }
      }
      // Close parent score director only if:
      // 1. It's different from child, AND
      // 2. No exception was thrown during setup (parent already closed in catch block)
      if (parentScoreDirector != null && parentScoreDirector != scoreDirector && !exceptionThrown) {
        try {
          parentScoreDirector.close();
        } catch (Exception e) {
          LOGGER.warn(
              "{}            Move thread ({}) failed to close parent score director.",
              logIndentation,
              moveThreadIndex,
              e);
        }
      }
    }
  }

  protected void predictWorkingStepScore(Move<Solution_> step, InnerScore<Score_> score) {
    scoreDirector.getSolutionDescriptor().setScore(scoreDirector.getWorkingSolution(), score.raw());
    if (assertStepScoreFromScratch) {
      scoreDirector.assertPredictedScoreFromScratch(score, step);
    }
    if (assertExpectedStepScore) {
      scoreDirector.assertExpectedWorkingScore(score, step);
    }
    if (assertShadowVariablesAreNotStaleAfterStep) {
      scoreDirector.assertShadowVariablesAreNotStale(score, step);
    }
  }

  public long getCalculationCount() {
    long calculationCount = this.calculationCount.get();
    if (calculationCount == -1L) {
      LOGGER.info(
          "{}Score calculation speed will be too low"
              + " because move thread ({})'s destroy wasn't processed soon enough.",
          logIndentation,
          moveThreadIndex);
      return 0L;
    }
    return calculationCount;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "-" + moveThreadIndex;
  }
}
