package ai.greycos.solver.core.impl.constructionheuristic.decider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import ai.greycos.solver.core.impl.heuristic.move.MoveAdapters;
import ai.greycos.solver.core.impl.heuristic.thread.ApplyStepOperation;
import ai.greycos.solver.core.impl.heuristic.thread.DestroyOperation;
import ai.greycos.solver.core.impl.heuristic.thread.MoveEvaluationOperation;
import ai.greycos.solver.core.impl.heuristic.thread.MoveThreadOperation;
import ai.greycos.solver.core.impl.heuristic.thread.MoveThreadRunner;
import ai.greycos.solver.core.impl.heuristic.thread.OrderByMoveIndexBlockingQueue;
import ai.greycos.solver.core.impl.heuristic.thread.SetupOperation;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.solver.thread.ThreadUtils;
import ai.greycos.solver.core.preview.api.move.Move;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multithreaded implementation of ConstructionHeuristicDecider that evaluates moves in parallel
 * using multiple worker threads. This decider coordinates move evaluation across threads while
 * maintaining the correct order of operations and proper synchronization.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class MultiThreadedConstructionHeuristicDecider<Solution_>
    extends ConstructionHeuristicDecider<Solution_> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MultiThreadedConstructionHeuristicDecider.class);

  protected final ThreadFactory threadFactory;
  protected final int moveThreadCount;
  protected final int selectedMoveBufferSize;

  protected boolean assertStepScoreFromScratch = false;
  protected boolean assertExpectedStepScore = false;
  protected boolean assertShadowVariablesAreNotStaleAfterStep = false;

  protected BlockingQueue<MoveThreadOperation<Solution_>> operationQueue;
  protected OrderByMoveIndexBlockingQueue<Solution_> resultQueue;
  protected CyclicBarrier moveThreadBarrier;
  protected ExecutorService executor;
  protected List<MoveThreadRunner<Solution_, ?>> moveThreadRunnerList;

  protected volatile boolean fallbackToSingleThreaded = false;
  protected volatile int consecutiveFailures = 0;
  protected static final int MAX_CONSECUTIVE_FAILURES = 3;

  public MultiThreadedConstructionHeuristicDecider(
      String logIndentation,
      PhaseTermination<Solution_> termination,
      ConstructionHeuristicForager<Solution_> forager,
      ThreadFactory threadFactory,
      int moveThreadCount,
      int selectedMoveBufferSize) {
    super(logIndentation, termination, forager);
    this.threadFactory = threadFactory;
    this.moveThreadCount = moveThreadCount;
    this.selectedMoveBufferSize = selectedMoveBufferSize;
  }

  @Override
  public void phaseStarted(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);

    fallbackToSingleThreaded = false;
    consecutiveFailures = 0;

    operationQueue =
        new ArrayBlockingQueue<>(selectedMoveBufferSize + moveThreadCount + moveThreadCount);
    resultQueue = new OrderByMoveIndexBlockingQueue<>(selectedMoveBufferSize + moveThreadCount);
    moveThreadBarrier = new CyclicBarrier(moveThreadCount);

    var scoreDirector = phaseScope.getScoreDirector();
    executor = createThreadPoolExecutor();
    moveThreadRunnerList = new ArrayList<>(moveThreadCount);

    try {
      for (int moveThreadIndex = 0; moveThreadIndex < moveThreadCount; moveThreadIndex++) {
        MoveThreadRunner<Solution_, ?> moveThreadRunner =
            new MoveThreadRunner<>(
                logIndentation,
                moveThreadIndex,
                true,
                operationQueue,
                resultQueue,
                moveThreadBarrier,
                assertMoveScoreFromScratch,
                assertExpectedUndoMoveScore,
                assertStepScoreFromScratch,
                assertExpectedStepScore,
                assertShadowVariablesAreNotStaleAfterStep);
        moveThreadRunnerList.add(moveThreadRunner);
        executor.submit(moveThreadRunner);
        operationQueue.add(new SetupOperation<>(scoreDirector));
      }
    } catch (RuntimeException | Error e) {
      LOGGER.error(
          "{}            Failed to initialize move threads, falling back to single-threaded mode: {}",
          logIndentation,
          e.getMessage(),
          e);
      shutdownMoveThreads();
      fallbackToSingleThreaded = true;

      if (moveThreadBarrier != null) {
        try {
          moveThreadBarrier.reset();
        } catch (Exception barrierException) {
          // Ignore barrier exceptions during cleanup
        }
        moveThreadBarrier = null;
      }
      operationQueue = null;
      resultQueue = null;
      moveThreadRunnerList = null;

      throw e;
    }
  }

  @Override
  public void phaseEnded(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);

    DestroyOperation<Solution_> destroyOperation = new DestroyOperation<>();
    for (int i = 0; i < moveThreadCount; i++) {
      operationQueue.add(destroyOperation);
    }

    shutdownMoveThreads();

    long childThreadsScoreCalculationCount = 0;
    for (MoveThreadRunner<Solution_, ?> moveThreadRunner : moveThreadRunnerList) {
      childThreadsScoreCalculationCount += moveThreadRunner.getCalculationCount();
    }
    phaseScope.addChildThreadsScoreCalculationCount(childThreadsScoreCalculationCount);

    operationQueue = null;
    resultQueue = null;
    moveThreadRunnerList = null;
  }

  @Override
  public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
    super.solvingError(solverScope, exception);
    shutdownMoveThreads();
  }

  protected ExecutorService createThreadPoolExecutor() {
    ExecutorService threadPoolExecutor =
        Executors.newFixedThreadPool(moveThreadCount, threadFactory);
    return threadPoolExecutor;
  }

  @Override
  public void decideNextStep(
      ConstructionHeuristicStepScope<Solution_> stepScope, Iterator<Move<Solution_>> moveIterator) {
    if (fallbackToSingleThreaded) {
      LOGGER.debug("{}            Falling back to single-threaded mode", logIndentation);
      super.decideNextStep(stepScope, moveIterator);
      return;
    }

    int stepIndex = stepScope.getStepIndex();
    resultQueue.startNextStep(stepIndex);

    int selectMoveIndex = 0;
    int movesInPlay = 0;
    var selectedMoveList = new ArrayList<Move<Solution_>>(selectedMoveBufferSize);

    do {
      boolean hasNextMove = moveIterator.hasNext();
      if (movesInPlay > 0 && (selectMoveIndex >= selectedMoveBufferSize || !hasNextMove)) {
        if (forageResult(stepScope, stepIndex, selectedMoveList)) {
          break;
        }
        movesInPlay--;
      }
      if (hasNextMove) {
        var move = moveIterator.next();
        selectedMoveList.add(move);
        var legacyMove = MoveAdapters.toLegacyMove(move);
        operationQueue.add(new MoveEvaluationOperation<>(stepIndex, selectMoveIndex, legacyMove));
        selectMoveIndex++;
        movesInPlay++;
      }
    } while (movesInPlay > 0);

    operationQueue.clear();

    pickMove(stepScope);

    if (stepScope.getStep() != null) {
      var scoreDirector = stepScope.getScoreDirector();
      var legacyStep = MoveAdapters.toLegacyMove(stepScope.getStep());
      var stepOperation =
          new ApplyStepOperation<>(stepIndex + 1, legacyStep, stepScope.getScore().raw());

      for (int i = 0; i < moveThreadCount; i++) {
        operationQueue.add(stepOperation);
      }
    }
  }

  private boolean forageResult(
      ConstructionHeuristicStepScope<Solution_> stepScope,
      int stepIndex,
      List<Move<Solution_>> selectedMoveList) {
    OrderByMoveIndexBlockingQueue.MoveResult<Solution_> result;
    try {
      result = resultQueue.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return true;
    }

    if (result.getThrowable() != null) {
      consecutiveFailures++;
      LOGGER.error(
          "{}            Move thread ({}) threw exception: {}",
          logIndentation,
          result.getMoveThreadIndex(),
          result.getThrowable());

      if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
        LOGGER.warn(
            "{}            Too many consecutive failures ({}), "
                + "falling back to single-threaded mode",
            logIndentation,
            consecutiveFailures);
        fallbackToSingleThreaded = true;
        return true;
      }
      return false;
    }

    consecutiveFailures = 0;

    // Step index must match exactly
    if (stepIndex != result.getStepIndex()) {
      throw new IllegalStateException(
          "Impossible situation: solverThread's stepIndex ("
              + stepIndex
              + ") differs from the result's stepIndex ("
              + result.getStepIndex()
              + ").");
    }

    int foragingMoveIndex = result.getMoveIndex();
    Move<Solution_> foragingMove = null;
    if (selectedMoveList != null && foragingMoveIndex < selectedMoveList.size()) {
      foragingMove = selectedMoveList.get(foragingMoveIndex);
      selectedMoveList.set(foragingMoveIndex, null);
    }
    if (foragingMove == null) {
      foragingMove = result.getMove().rebase(stepScope.getScoreDirector().getMoveDirector());
    }

    ConstructionHeuristicMoveScope<Solution_> moveScope =
        new ConstructionHeuristicMoveScope<>(stepScope, foragingMoveIndex, foragingMove);

    if (!result.isMoveDoable()) {
      LOGGER.trace(
          "{}        Move index ({}) not doable, ignoring move ({}).",
          logIndentation,
          foragingMoveIndex,
          foragingMove);
    } else {
      @SuppressWarnings("unchecked")
      var score = (ai.greycos.solver.core.api.score.Score<?>) result.getScore();
      moveScope.setScore(InnerScore.fullyAssigned((ai.greycos.solver.core.api.score.Score) score));
      moveScope.getScoreDirector().incrementCalculationCount();
      forager.addMove(moveScope);
      if (forager.isQuitEarly()) {
        return true;
      }
    }

    stepScope.getPhaseScope().getSolverScope().checkYielding();
    return termination.isPhaseTerminated(stepScope.getPhaseScope());
  }

  private void shutdownMoveThreads() {
    if (executor != null && !executor.isShutdown()) {
      ThreadUtils.shutdownAwaitOrKill(
          executor, logIndentation, "Multi-threaded Construction Heuristic");
    }
  }

  @Override
  public void enableAssertions(EnvironmentMode environmentMode) {
    super.enableAssertions(environmentMode);
    assertStepScoreFromScratch = environmentMode.isFullyAsserted();
    if (environmentMode.isIntrusivelyAsserted()) {
      assertExpectedStepScore = true;
      assertShadowVariablesAreNotStaleAfterStep = true;
    } else {
      assertExpectedStepScore = false;
      assertShadowVariablesAreNotStaleAfterStep = false;
    }
  }

  public int getMoveThreadCount() {
    return moveThreadCount;
  }

  public int getSelectedMoveBufferSize() {
    return selectedMoveBufferSize;
  }

  public boolean isAssertStepScoreFromScratch() {
    return assertStepScoreFromScratch;
  }

  public boolean isAssertExpectedStepScore() {
    return assertExpectedStepScore;
  }

  public boolean isAssertShadowVariablesAreNotStaleAfterStep() {
    return assertShadowVariablesAreNotStaleAfterStep;
  }
}
