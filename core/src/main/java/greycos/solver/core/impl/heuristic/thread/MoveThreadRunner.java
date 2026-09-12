package greycos.solver.core.impl.heuristic.thread;

import java.util.concurrent.locks.LockSupport;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.phase.scope.SolverLifecyclePoint;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.preview.api.move.Move;

/** A persistent owner of one incremental working solution and an addressed step mailbox. */
final class MoveThreadRunner<Solution_, Score_ extends Score<Score_>> implements Runnable {

  private final MoveEvaluationPipeline<Solution_> pipeline;
  private final int workerIndex;
  private final InnerScoreDirector<Solution_, Score_> parent;
  volatile MoveEvaluationPipeline.Epoch<Solution_> mailbox;
  volatile int appliedStepIndex = -1;
  volatile Thread thread;
  volatile boolean waiting;
  long calculationCount;
  long evaluated;
  long scored;
  long samples;
  long rebaseNanos;
  long evaluationNanos;
  long replayNanos;
  long idleNanos;

  MoveThreadRunner(
      MoveEvaluationPipeline<Solution_> pipeline,
      int workerIndex,
      InnerScoreDirector<Solution_, Score_> parent,
      MoveEvaluationPipeline.Epoch<Solution_> initialEpoch) {
    this.pipeline = pipeline;
    this.workerIndex = workerIndex;
    this.parent = parent;
    mailbox = initialEpoch;
  }

  @Override
  public void run() {
    thread = Thread.currentThread();
    InnerScoreDirector<Solution_, Score_> director = null;
    try {
      if (pipeline.aborting) {
        return;
      }
      director = parent.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
      InnerScore<Score_> workingScore = director.calculateScore();
      var epoch = mailbox;
      appliedStepIndex = epoch.stepIndex;
      pipeline.acknowledge();
      while (!pipeline.aborting) {
        if (Thread.currentThread().isInterrupted()) {
          if (pipeline.aborting) {
            break;
          }
          throw new IllegalStateException("Move worker interrupted outside cancellation.");
        }
        var next = mailbox;
        if (next != epoch) {
          if (next.stepIndex != epoch.stepIndex + 1) {
            throw new IllegalStateException("Move worker received a nonconsecutive step update.");
          }
          long start = pipeline.diagnosticsEnabled ? System.nanoTime() : 0;
          Move<Solution_> step = next.step.rebase(director.getMoveDirector());
          director.getMoveDirector().execute(step);
          @SuppressWarnings("unchecked")
          var expected = (InnerScore<Score_>) next.stepScore;
          workingScore = expected;
          director.getSolutionDescriptor().setScore(director.getWorkingSolution(), expected.raw());
          if (pipeline.assertStepScoreFromScratch) {
            director.assertPredictedScoreFromScratch(expected, step);
          }
          if (pipeline.assertExpectedStepScore) {
            director.assertExpectedWorkingScore(expected, step);
          }
          if (pipeline.assertShadowVariablesAreNotStaleAfterStep) {
            director.assertShadowVariablesAreNotStale(expected, step);
          }
          if (pipeline.diagnosticsEnabled) {
            replayNanos += System.nanoTime() - start;
          }
          epoch = next;
          // No accesses to the previous epoch may occur after this release acknowledgement.
          appliedStepIndex = epoch.stepIndex;
          pipeline.acknowledge();
        }
        if (pipeline.stopping) {
          // Close may have published a final control after the mailbox read above.
          if (mailbox != epoch) {
            continue;
          }
          break;
        }
        int moveIndex = epoch.claim();
        if (moveIndex >= 0) {
          // Cancellation can race the claim. Never interrupt a partial transaction.
          if (epoch.closed) {
            continue;
          }
          var slot = epoch.slots[moveIndex % epoch.slots.length];
          boolean sample = pipeline.diagnosticsEnabled && (evaluated & 1023) == 0;
          long start = sample ? System.nanoTime() : 0;
          var move = slot.move.rebase(director.getMoveDirector());
          if (sample) {
            rebaseNanos += System.nanoTime() - start;
            start = System.nanoTime();
          }
          InnerScore<Score_> score = null;
          if (!(pipeline.evaluateDoable
              && move instanceof AbstractSelectorBasedMove<Solution_> selector
              && !selector.isMoveDoable(director))) {
            score = director.executeTemporaryMove(move, pipeline.assertMoveScoreFromScratch);
            if (pipeline.assertExpectedUndoMoveScore) {
              director.assertExpectedUndoMoveScore(
                  move,
                  workingScore,
                  SolverLifecyclePoint.of(
                      workerIndex, pipeline.phaseIndex, epoch.stepIndex, moveIndex));
            }
            scored++;
          }
          evaluated++;
          if (sample) {
            evaluationNanos += System.nanoTime() - start;
            samples++;
          }
          slot.score = score;
          slot.completedIndex = moveIndex;
          // Never touch the slot after publishing: the coordinator may immediately reuse it.
          pipeline.resultPublished(epoch, moveIndex);
        } else {
          long start = pipeline.diagnosticsEnabled ? System.nanoTime() : 0;
          for (int spin = 0;
              spin < 1024
                  && !pipeline.stopping
                  && mailbox == epoch
                  && !epoch.closed
                  && !epoch.hasWork();
              spin++) {
            Thread.onSpinWait();
          }
          waiting = true;
          if (!pipeline.stopping && mailbox == epoch && !epoch.hasWork()) {
            LockSupport.park(pipeline);
          }
          waiting = false;
          if (pipeline.diagnosticsEnabled) {
            idleNanos += System.nanoTime() - start;
          }
        }
      }
    } catch (Throwable e) {
      if (!pipeline.aborting || !(e instanceof InterruptedException)) {
        pipeline.fail(workerIndex, e);
      }
    } finally {
      waiting = false;
      if (director != null) {
        try {
          calculationCount = director.getCalculationCount();
        } catch (Throwable e) {
          pipeline.fail(workerIndex, e);
        }
        try {
          director.close();
        } catch (Throwable e) {
          pipeline.fail(workerIndex, e);
        }
      }
      pipeline.acknowledge();
    }
  }
}
