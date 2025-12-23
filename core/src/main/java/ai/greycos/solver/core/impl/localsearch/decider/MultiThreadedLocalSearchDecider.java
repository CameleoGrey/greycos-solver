package ai.greycos.solver.core.impl.localsearch.decider;

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
import ai.greycos.solver.core.impl.heuristic.move.MoveAdapters;
import ai.greycos.solver.core.impl.heuristic.thread.ApplyStepOperation;
import ai.greycos.solver.core.impl.heuristic.thread.DestroyOperation;
import ai.greycos.solver.core.impl.heuristic.thread.MoveEvaluationOperation;
import ai.greycos.solver.core.impl.heuristic.thread.MoveThreadOperation;
import ai.greycos.solver.core.impl.heuristic.thread.MoveThreadRunner;
import ai.greycos.solver.core.impl.heuristic.thread.OrderByMoveIndexBlockingQueue;
import ai.greycos.solver.core.impl.heuristic.thread.SetupOperation;
import ai.greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.neighborhood.MoveRepository;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.solver.thread.ThreadUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multithreaded implementation of LocalSearchDecider that evaluates moves in parallel using
 * multiple worker threads. This decider coordinates move evaluation across threads while
 * maintaining the correct order of operations and proper synchronization.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class MultiThreadedLocalSearchDecider<Solution_> extends LocalSearchDecider<Solution_> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MultiThreadedLocalSearchDecider.class);

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

  public MultiThreadedLocalSearchDecider(
      String logIndentation,
      PhaseTermination<Solution_> termination,
      MoveRepository<Solution_> moveRepository,
      Acceptor<Solution_> acceptor,
      LocalSearchForager<Solution_> forager,
      ThreadFactory threadFactory,
      int moveThreadCount,
      int selectedMoveBufferSize) {
    super(logIndentation, termination, moveRepository, acceptor, forager);
    this.threadFactory = threadFactory;
    this.moveThreadCount = moveThreadCount;
    this.selectedMoveBufferSize = selectedMoveBufferSize;
  }

  @Override
  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);

    // Initialize thread-safe queues and barriers
    operationQueue =
        new ArrayBlockingQueue<>(selectedMoveBufferSize + moveThreadCount + moveThreadCount);
    resultQueue = new OrderByMoveIndexBlockingQueue<>(selectedMoveBufferSize + moveThreadCount);
    moveThreadBarrier = new CyclicBarrier(moveThreadCount);

    // Create and start move threads
    InnerScoreDirector<Solution_, ?> scoreDirector = phaseScope.getScoreDirector();
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
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
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
  public void decideNextStep(LocalSearchStepScope<Solution_> stepScope) {
    int stepIndex = stepScope.getStepIndex();
    resultQueue.startNextStep(stepIndex);

    int selectMoveIndex = 0;
    int movesInPlay = 0;
    Iterator<?> moveIterator = moveRepository.iterator();

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
        @SuppressWarnings("unchecked")
        var legacyMove =
            MoveAdapters.toLegacyMove(
                (ai.greycos.solver.core.preview.api.move.Move<Solution_>) move);
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
      InnerScoreDirector<Solution_, ?> scoreDirector = stepScope.getScoreDirector();
      var legacyStep = MoveAdapters.toLegacyMove(stepScope.getStep());
      var stepOperation =
          new ApplyStepOperation<>(stepIndex + 1, legacyStep, stepScope.getScore().raw());

      // Send the step operation to all move threads
      for (int i = 0; i < moveThreadCount; i++) {
        operationQueue.add(stepOperation);
      }
    }
  }

  private boolean forageResult(LocalSearchStepScope<Solution_> stepScope, int stepIndex) {
    OrderByMoveIndexBlockingQueue.MoveResult<Solution_> result;
    try {
      result = resultQueue.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return true;
    }

    // Allow stepIndex to be -1 (initial state) or match the expected stepIndex
    if (stepIndex != -1 && stepIndex != result.getStepIndex()) {
      throw new IllegalStateException(
          "Impossible situation: the solverThread's stepIndex ("
              + stepIndex
              + ") differs from the result's stepIndex ("
              + result.getStepIndex()
              + ").");
    }

    var foragingMove = result.getMove().rebase(stepScope.getScoreDirector().getMoveDirector());
    int foragingMoveIndex = result.getMoveIndex();

    LocalSearchMoveScope<Solution_> moveScope =
        new LocalSearchMoveScope<>(stepScope, foragingMoveIndex, foragingMove);

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
      boolean accepted = acceptor.isAccepted(moveScope);
      moveScope.setAccepted(accepted);
      LOGGER.trace(
          "{}        Move index ({}), score ({}), accepted ({}), move ({}).",
          logIndentation,
          foragingMoveIndex,
          moveScope.getScore().raw(),
          moveScope.getAccepted(),
          foragingMove);
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
      ThreadUtils.shutdownAwaitOrKill(executor, logIndentation, "Multi-threaded Local Search");
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
