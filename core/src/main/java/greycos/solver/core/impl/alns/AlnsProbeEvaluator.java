package greycos.solver.core.impl.alns;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.alns.AlnsTerminationException;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationSource;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.Nullable;

/**
 * Coordinator-owned adapter for ordered ALNS probes. Workers are created on the first useful batch
 * and retain their incremental working state until the phase ends.
 */
final class AlnsProbeEvaluator<Solution_, Score_ extends Score<Score_>> implements AutoCloseable {

  private static final MoveEvaluationPipeline.Diagnostics EMPTY_DIAGNOSTICS =
      new MoveEvaluationPipeline.Diagnostics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

  private final InnerScoreDirector<Solution_, Score_> parent;
  private final @Nullable Integer workerCount;
  private final int bufferSize;
  private final ThreadFactory threadFactory;
  private final int phaseIndex;
  private final EnvironmentMode environmentMode;
  private final BooleanSupplier terminated;
  private final BooleanSupplier waitTerminated;
  private final int claimChunkSize;
  private @Nullable MoveEvaluationPipeline<Solution_> pipeline;
  private int epochIndex;
  private int nextMoveIndex;
  private long transferredCalculationCount;
  private boolean epochClosed;
  private boolean aborted;
  private boolean closed;

  AlnsProbeEvaluator(
      InnerScoreDirector<Solution_, Score_> parent,
      @Nullable Integer workerCount,
      int bufferSize,
      ThreadFactory threadFactory,
      int phaseIndex,
      EnvironmentMode environmentMode,
      BooleanSupplier terminated) {
    this(
        parent,
        workerCount,
        bufferSize,
        threadFactory,
        phaseIndex,
        environmentMode,
        terminated,
        terminated);
  }

  AlnsProbeEvaluator(
      InnerScoreDirector<Solution_, Score_> parent,
      @Nullable Integer workerCount,
      int bufferSize,
      ThreadFactory threadFactory,
      int phaseIndex,
      EnvironmentMode environmentMode,
      BooleanSupplier terminated,
      BooleanSupplier waitTerminated) {
    if (workerCount != null && workerCount < 1 || bufferSize < 1) {
      throw new IllegalArgumentException(
          "ALNS worker count and candidate capacity must be positive.");
    }
    this.parent = Objects.requireNonNull(parent);
    this.workerCount = workerCount;
    // A one-candidate-per-worker window must still expose work to every worker.
    claimChunkSize =
        Math.min(bufferSize, Integer.getInteger("greycos.solver.alns.probeChunkSize", 4));
    // The phase option, like the existing move-thread buffer option, is per worker.
    this.bufferSize =
        workerCount == null ? bufferSize : Math.multiplyExact(workerCount, bufferSize);
    this.threadFactory = Objects.requireNonNull(threadFactory);
    this.phaseIndex = phaseIndex;
    this.environmentMode = Objects.requireNonNull(environmentMode);
    this.terminated = Objects.requireNonNull(terminated);
    this.waitTerminated = Objects.requireNonNull(waitTerminated);
  }

  boolean isEnabledFor(int candidateCount) {
    return workerCount != null && candidateCount > 1;
  }

  boolean isStarted() {
    return pipeline != null;
  }

  boolean isAborted() {
    return aborted;
  }

  /**
   * Scores a batch against one baseline. The list may compile candidates lazily: only this
   * coordinator reads it. The callback credits one logical calculation and probe in input order;
   * worker completion and speculative results do not independently consume logical budgets.
   */
  List<InnerScore<Score_>> evaluate(
      List<? extends Move<Solution_>> candidates, Consumer<InnerScore<Score_>> onConsumed) {
    var scores = new ArrayList<InnerScore<Score_>>(candidates.size());
    evaluateTo(
        candidates,
        score -> {
          onConsumed.accept(score);
          scores.add(score);
        });
    return scores;
  }

  /** Streams ordered scores directly to a reducer without retaining a second result list. */
  void evaluateTo(
      List<? extends Move<Solution_>> candidates, Consumer<InnerScore<Score_>> onConsumed) {
    requireOpen();
    Objects.requireNonNull(onConsumed);
    if (!isEnabledFor(candidates.size())) {
      throw new IllegalStateException(
          "This ALNS batch must use the coordinator's sequential evaluator.");
    }
    checkTermination();
    startIfNeeded();
    if (epochClosed || candidates.size() > Integer.MAX_VALUE - nextMoveIndex) {
      publishReplay(List.of(), null);
    }
    var activePipeline = Objects.requireNonNull(pipeline);
    int submitted = 0;
    int consumed = 0;
    int firstMoveIndex = nextMoveIndex;
    try {
      while (consumed < candidates.size()) {
        // This corresponds to the check before the next serial probe. In particular, do not
        // add another check after the last result: the operator may draw a random number next.
        checkTermination();
        while (submitted < candidates.size() && submitted - consumed < bufferSize) {
          activePipeline.submit(nextMoveIndex, candidates.get(submitted));
          nextMoveIndex++;
          submitted++;
        }
        var result = activePipeline.take();
        if (result == null) {
          throw new AlnsTerminationException();
        }
        if (result.stepIndex() != epochIndex
            || result.moveIndex() != firstMoveIndex + consumed
            || result.score() == null) {
          throw new IllegalStateException(
              "ALNS probe result belongs to an unexpected worker state.");
        }
        @SuppressWarnings("unchecked")
        var score = (InnerScore<Score_>) result.score();
        onConsumed.accept(score);
        transferredCalculationCount++;
        consumed++;
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      cancelEpoch();
      throw new AlnsTerminationException();
    } catch (RuntimeException | Error failure) {
      cancelEpoch();
      throw failure;
    }
  }

  /** Prepared framework sources compile and rebase candidates on the owning worker. */
  void evaluateSource(
      MoveEvaluationSource<Solution_> source, ObjIntConsumer<InnerScore<Score_>> onConsumed) {
    requireOpen();
    if (!isEnabledFor(source.size()))
      throw new IllegalStateException("Prepared batch must use sequential evaluation.");
    checkTermination();
    startIfNeeded();
    if (epochClosed || source.size() > Integer.MAX_VALUE - nextMoveIndex)
      publishReplay(List.of(), null);
    var activePipeline = Objects.requireNonNull(pipeline);
    int submitted = 0;
    int consumed = 0;
    int firstMoveIndex = nextMoveIndex;
    try {
      while (consumed < source.size()) {
        checkTermination();
        int room = bufferSize - (submitted - consumed);
        int refillSize = Math.min(bufferSize, claimChunkSize);
        if (submitted < source.size()
            && room > 0
            && (room >= refillSize || submitted == consumed || source.size() - submitted <= room)) {
          int count =
              activePipeline.submitRange(
                  nextMoveIndex, source, submitted, Math.min(room, source.size() - submitted));
          submitted += count;
          nextMoveIndex += count;
        }
        @SuppressWarnings("unchecked")
        var score =
            (InnerScore<Score_>) activePipeline.takeScore(epochIndex, firstMoveIndex + consumed);
        if (score == null) throw new AlnsTerminationException();
        onConsumed.accept(score, consumed);
        transferredCalculationCount++;
        consumed++;
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      cancelEpoch();
      throw new AlnsTerminationException();
    } catch (RuntimeException | Error failure) {
      cancelEpoch();
      throw failure;
    }
  }

  /**
   * Replays deltas already applied to the coordinator. Before worker startup these need no journal:
   * the first batch clones the current state. A null score never causes coordinator scoring.
   */
  void replay(List<? extends Move<Solution_>> moves, @Nullable InnerScore<Score_> expectedScore) {
    if (aborted) {
      return; // Coordinator recovery may unwind further savepoints after discarding workers.
    }
    requireOpen();
    if (pipeline != null && (!moves.isEmpty() || epochClosed)) {
      publishReplay(moves, expectedScore);
    }
  }

  private void publishReplay(
      List<? extends Move<Solution_>> moves, @Nullable InnerScore<Score_> expectedScore) {
    try {
      var activePipeline = Objects.requireNonNull(pipeline);
      int nextEpochIndex = Math.incrementExact(epochIndex);
      activePipeline.applyState(nextEpochIndex, AlnsPrimitiveMove.composite(moves), expectedScore);
      epochIndex = nextEpochIndex;
      nextMoveIndex = 0;
      epochClosed = false;
    } catch (RuntimeException | Error failure) {
      // The coordinator has already published this journal segment. A failed handoff cannot
      // safely resume from the previous epoch or replay an unrelated rollback afterwards.
      abortPreserving(failure);
      if (isCoordinatorInterruption(failure)) throw new AlnsTerminationException();
      throw failure;
    }
  }

  private void startIfNeeded() {
    if (pipeline != null) {
      return;
    }
    int count = Objects.requireNonNull(workerCount);
    var executor =
        Executors.newFixedThreadPool(
            count,
            runnable -> {
              var worker = threadFactory.newThread(runnable);
              if (worker == null) {
                throw new IllegalStateException(
                    "ALNS thread factory returned null instead of a worker.");
              }
              return worker;
            });
    try {
      pipeline =
          new MoveEvaluationPipeline<>(
              executor,
              count,
              bufferSize,
              phaseIndex,
              false,
              environmentMode.isFullyAsserted(),
              environmentMode.isIntrusivelyAsserted(),
              environmentMode.isFullyAsserted(),
              environmentMode.isIntrusivelyAsserted(),
              environmentMode.isIntrusivelyAsserted());
      pipeline.setClaimChunkSize(claimChunkSize);
      pipeline.setTerminationCheck(waitTerminated);
      // start() waits until all clones exist, so subsequent coordinator mutation is safe.
      pipeline.start(parent);
    } catch (RuntimeException | Error failure) {
      // A factory may fail before every runner starts. Mark this adapter unusable even if the
      // pipeline has already joined: a never-started runner can never acknowledge later replay.
      abortPreserving(failure);
      if (pipeline == null) {
        try {
          executor.shutdownNow();
        } catch (RuntimeException | Error cleanupFailure) {
          if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
        }
      }
      if (isCoordinatorInterruption(failure)) throw new AlnsTerminationException();
      throw failure;
    }
  }

  private void abortPreserving(Throwable originalFailure) {
    try {
      abort();
    } catch (RuntimeException | Error cleanupFailure) {
      if (cleanupFailure != originalFailure) originalFailure.addSuppressed(cleanupFailure);
    }
  }

  private static boolean isCoordinatorInterruption(Throwable failure) {
    return failure instanceof IllegalStateException
        && failure.getCause() instanceof InterruptedException
        && Thread.currentThread().isInterrupted();
  }

  private void checkTermination() {
    if (Thread.currentThread().isInterrupted() || terminated.getAsBoolean()) {
      throw new AlnsTerminationException();
    }
  }

  private void cancelEpoch() {
    if (pipeline != null) {
      pipeline.cancelStep();
      epochClosed = true;
    }
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("ALNS probe evaluator is closed.");
    }
  }

  /**
   * Final physical worker calculations, including setup, replay, assertions and discarded probes.
   */
  long getPhysicalCalculationCount() {
    return pipeline == null ? 0 : pipeline.getCalculationCount();
  }

  long getTransferredCalculationCount() {
    return transferredCalculationCount;
  }

  /**
   * Add this once to child-thread metrics after close; consumed probes were credited to the parent.
   */
  long getAdditionalCalculationCount() {
    return getPhysicalCalculationCount() - transferredCalculationCount;
  }

  MoveEvaluationPipeline.Diagnostics getDiagnostics() {
    return pipeline == null ? EMPTY_DIAGNOSTICS : pipeline.getDiagnostics();
  }

  void abort() {
    aborted = true;
    closed = true;
    if (pipeline != null) {
      pipeline.abort();
    }
  }

  @Override
  public void close() {
    closed = true;
    if (pipeline != null) {
      pipeline.close();
    }
  }
}
