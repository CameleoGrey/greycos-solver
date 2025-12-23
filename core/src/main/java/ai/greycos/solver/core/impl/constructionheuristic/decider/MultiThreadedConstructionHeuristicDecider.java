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

    // Initialize thread-safe queues and barriers
    operationQueue =
        new ArrayBlockingQueue<>(selectedMoveBufferSize + moveThreadCount + moveThreadCount);
    resultQueue = new OrderByMoveIndexBlockingQueue<>(selectedMoveBufferSize + moveThreadCount);
    moveThreadBarrier = new CyclicBarrier(moveThreadCount);

    // Create and start move threads
    var scoreDirector = phaseScope.getScoreDirector();
    executor = createThreadPoolExecutor();
    moveThreadRunnerList = new ArrayList<>(moveThreadCount);

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

      // Send setup operation to initialize the thread
      operationQueue.add(new SetupOperation<>(scoreDirector));
    }
  }

  @Override
  public void phaseEnded(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);

    // Signal all threads to shutdown
    DestroyOperation<Solution_> destroyOperation = new DestroyOperation<>();
    for (int i = 0; i < moveThreadCount; i++) {
      operationQueue.add(destroyOperation);
    }

    shutdownMoveThreads();

    // Collect calculation counts from all threads
    long childThreadsScoreCalculationCount = 0;
    for (MoveThreadRunner<Solution_, ?> moveThreadRunner : moveThreadRunnerList) {
      childThreadsScoreCalculationCount += moveThreadRunner.getCalculationCount();
    }
    phaseScope.addChildThreadsScoreCalculationCount(childThreadsScoreCalculationCount);

    // Clean up resources
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
    int stepIndex = stepScope.getStepIndex();
    resultQueue.startNextStep(stepIndex);

    int selectMoveIndex = 0;
    int movesInPlay = 0;

    do {
      boolean hasNextMove = moveIterator.hasNext();
      if (movesInPlay > 0 && (selectMoveIndex >= selectedMoveBufferSize || !hasNextMove)) {
        if (forageResult(stepScope, stepIndex)) {
          break;
        }
        movesInPlay--;
      }
      if (hasNextMove) {
        var move = moveIterator.next();
        var legacyMove = MoveAdapters.toLegacyMove(move);
        operationQueue.add(new MoveEvaluationOperation<>(stepIndex, selectMoveIndex, legacyMove));
        selectMoveIndex++;
        movesInPlay++;
      }
    } while (movesInPlay > 0);

    // Clear any remaining operations in the queue
    operationQueue.clear();

    // Pick the best move from the results
    pickMove(stepScope);

    // If we have a step, apply it to all threads
    if (stepScope.getStep() != null) {
      var scoreDirector = stepScope.getScoreDirector();
      var legacyStep = MoveAdapters.toLegacyMove(stepScope.getStep());
      var stepOperation =
          new ApplyStepOperation<>(stepIndex + 1, legacyStep, stepScope.getScore().raw());

      // Send the step operation to all move threads
      for (int i = 0; i < moveThreadCount; i++) {
        operationQueue.add(stepOperation);
      }
    }
  }

  private boolean forageResult(ConstructionHeuristicStepScope<Solution_> stepScope, int stepIndex) {
    OrderByMoveIndexBlockingQueue.MoveResult<Solution_> result;
    try {
      result = resultQueue.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return true;
    }

    if (stepIndex != result.getStepIndex()) {
      throw new IllegalStateException(
          "Impossible situation: the solverThread's stepIndex ("
              + stepIndex
              + ") differs from the result's stepIndex ("
              + result.getStepIndex()
              + ").");
    }

    var foragingMove =
        result
            .getMove()
            .rebase((ai.greycos.solver.core.preview.api.move.Rebaser) stepScope.getScoreDirector());
    int foragingMoveIndex = result.getMoveIndex();

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

  // Override assertion setters to support child thread assertions
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

  // Getters for testing
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
