package greycos.solver.core.impl.heuristic.thread;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.LockSupport;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.move.PreparableMove;
import greycos.solver.core.impl.move.PreparedMoveEvaluation;
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
  volatile long quiescenceAcknowledged;
  volatile Thread thread;
  volatile boolean waiting;
  volatile long calculationCount;
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
    MoveEvaluationPipeline.CandidateMetadataCollector<Solution_> metadataCollector = null;
    var evaluationContexts = new IdentityHashMap<Object, PreparableMove<Solution_>>();
    try {
      if (pipeline.aborting) {
        return;
      }
      director = parent.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
      InnerScore<Score_> workingScore = director.calculateScore();
      metadataCollector = pipeline.createMetadataCollector(director);
      var epoch = mailbox;
      MoveEvaluationSource<Solution_> source = null;
      MoveEvaluationSource<Solution_> rebasedSource = null;
      calculationCount = director.getCalculationCount();
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
          Move<Solution_> step =
              next.step == null ? null : next.step.rebase(director.getMoveDirector());
          // A replay delta may be an ALNS intermediate state with inconsistent shadows.
          // Preserve its structural score so the coordinator can reject invalid candidates.
          if (step != null) {
            director.getMoveDirector().executeAllowingStructurallyFlawedSolutions(step);
          }
          @SuppressWarnings("unchecked")
          var expected = (InnerScore<Score_>) next.stepScore;
          if (step != null) {
            workingScore = expected == null ? director.calculateScore() : expected;
          }
          director
              .getSolutionDescriptor()
              .setScore(director.getWorkingSolution(), workingScore.raw());
          if (step != null && pipeline.assertStepScoreFromScratch) {
            director.assertPredictedScoreFromScratch(workingScore, step);
          }
          if (step != null && pipeline.assertExpectedStepScore) {
            director.assertExpectedWorkingScore(workingScore, step);
          }
          if (step != null && pipeline.assertShadowVariablesAreNotStaleAfterStep) {
            director.assertShadowVariablesAreNotStale(workingScore, step);
          }
          if (pipeline.diagnosticsEnabled) {
            replayNanos += System.nanoTime() - start;
          }
          if (next.episodeId != epoch.episodeId && metadataCollector != null) {
            var previousCollector = metadataCollector;
            metadataCollector = null;
            previousCollector.close();
          }
          if (metadataCollector == null)
            metadataCollector = pipeline.createMetadataCollector(director);
          epoch = next;
          source = null;
          rebasedSource = null;
          calculationCount = director.getCalculationCount();
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
        var quiescence = pipeline.quiescenceRequest();
        if (quiescence != null
            && quiescence.epoch() == epoch.stepIndex
            && quiescence.sequence() > quiescenceAcknowledged) {
          source = null;
          rebasedSource = null;
          // Provider lifecycle may dispose these sessions once the coordinator sees this ack.
          // Remove before invoking cleanup so exceptional teardown cannot close a session twice.
          var contexts = new ArrayList<>(evaluationContexts.values());
          evaluationContexts.clear();
          Throwable cleanupFailure = null;
          for (var context : contexts) {
            try {
              context.closeEvaluationContext(director);
            } catch (RuntimeException | Error failure) {
              if (cleanupFailure == null) cleanupFailure = failure;
              else if (cleanupFailure != failure) cleanupFailure.addSuppressed(failure);
            }
          }
          if (metadataCollector != null) {
            var previousCollector = metadataCollector;
            metadataCollector = null;
            try {
              previousCollector.close();
            } catch (RuntimeException | Error failure) {
              if (cleanupFailure == null) cleanupFailure = failure;
              else if (cleanupFailure != failure) cleanupFailure.addSuppressed(failure);
            }
          }
          if (cleanupFailure instanceof Error error) throw error;
          if (cleanupFailure != null) throw (RuntimeException) cleanupFailure;
          calculationCount = director.getCalculationCount();
          quiescenceAcknowledged = quiescence.sequence();
          pipeline.acknowledge();
        }
        long claim = epoch.claim(pipeline.claimChunkSize());
        if (claim >= 0) {
          int end = (int) claim;
          for (int moveIndex = (int) (claim >>> 32); moveIndex < end; moveIndex++) {
            // Cancellation can race a chunk claim. Finish only the current balanced transaction.
            if (epoch.closed || pipeline.aborting || Thread.currentThread().isInterrupted()) {
              break;
            }
            var slot = epoch.slots[moveIndex % epoch.slots.length];
            boolean sample = pipeline.diagnosticsEnabled && (evaluated & 1023) == 0;
            long start = sample ? System.nanoTime() : 0;
            Move<Solution_> move;
            if (slot.source == null) {
              move = slot.move.rebase(director.getMoveDirector());
            } else {
              if (source != slot.source) {
                source = slot.source;
                rebasedSource = source.rebase(director.getMoveDirector());
              }
              move = rebasedSource.move(slot.sourceIndex);
            }
            if (sample) {
              rebaseNanos += System.nanoTime() - start;
              start = System.nanoTime();
            }
            InnerScore<Score_> score = null;
            long calculationStart = director.getCalculationCount();
            var status = PreparedMoveEvaluation.Status.EMPTY;
            if (metadataCollector != null) {
              metadataCollector.beforeEvaluation(slot.context);
            }
            if (move instanceof PreparableMove<Solution_> preparableMove) {
              evaluationContexts.putIfAbsent(preparableMove.cleanupKey(), preparableMove);
              var evaluatingEpoch = epoch;
              var collector = metadataCollector;
              var context = slot.context;
              var result =
                  preparableMove.prepare(
                      director,
                      () -> {
                        if (evaluatingEpoch.closed
                            || pipeline.aborting
                            || Thread.currentThread().isInterrupted()) {
                          throw new CancellationException("Move worker preparation cancelled.");
                        }
                      },
                      pipeline.assertMoveScoreFromScratch,
                      collector == null
                          ? null
                          : (view, preparedMove) ->
                              slot.metadata = collector.collect(view, preparedMove, context));
              status = result.status();
              score = result.score();
              slot.preparedMove = result.move();
              if (status == PreparedMoveEvaluation.Status.EVALUATED) {
                move = result.move();
              }
            } else if (!(pipeline.evaluateDoable
                && move instanceof AbstractSelectorBasedMove<Solution_> selector
                && !selector.isMoveDoable(director))) {
              if (metadataCollector == null) {
                score = director.executeTemporaryMove(move, pipeline.assertMoveScoreFromScratch);
              } else {
                var collector = metadataCollector;
                var context = slot.context;
                var evaluatedMove = move;
                score =
                    director.executeTemporaryMove(
                        move,
                        view -> slot.metadata = collector.collect(view, evaluatedMove, context),
                        pipeline.assertMoveScoreFromScratch);
              }
              status = PreparedMoveEvaluation.Status.EVALUATED;
            }
            if (status == PreparedMoveEvaluation.Status.EVALUATED) {
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
            slot.status = status;
            calculationCount = director.getCalculationCount();
            slot.calculationCount = calculationCount - calculationStart;
            slot.completedIndex = moveIndex;
            // Never touch the slot after publishing: the coordinator may immediately reuse it.
            pipeline.resultPublished(epoch, moveIndex);
          }
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
        for (var context : evaluationContexts.values()) {
          try {
            context.closeEvaluationContext(director);
          } catch (Throwable e) {
            pipeline.failCleanup(workerIndex, e);
          }
        }
      }
      if (metadataCollector != null) {
        try {
          metadataCollector.close();
        } catch (Throwable e) {
          pipeline.failCleanup(workerIndex, e);
        }
      }
      if (director != null) {
        try {
          calculationCount = director.getCalculationCount();
        } catch (Throwable e) {
          pipeline.fail(workerIndex, e);
        }
        try {
          director.close();
        } catch (Throwable e) {
          pipeline.failCleanup(workerIndex, e);
        }
      }
      pipeline.acknowledge();
    }
  }
}
