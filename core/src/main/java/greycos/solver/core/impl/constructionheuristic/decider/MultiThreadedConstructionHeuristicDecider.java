package greycos.solver.core.impl.constructionheuristic.decider;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.solver.thread.ThreadUtils;
import greycos.solver.core.preview.api.move.Move;

/**
 * Multithreaded implementation of ConstructionHeuristicDecider that evaluates moves in parallel
 * using multiple worker threads. This decider coordinates move evaluation across threads while
 * maintaining the correct order of operations and proper synchronization.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class MultiThreadedConstructionHeuristicDecider<Solution_>
    extends ConstructionHeuristicDecider<Solution_> {

  protected final ThreadFactory threadFactory;
  protected final int moveThreadCount;
  protected final int selectedMoveBufferSize;

  protected boolean assertStepScoreFromScratch = false;
  protected boolean assertExpectedStepScore = false;
  protected boolean assertShadowVariablesAreNotStaleAfterStep = false;

  protected ExecutorService executor;
  protected MoveEvaluationPipeline<Solution_> moveEvaluationPipeline;
  private MoveEvaluationPipeline.Diagnostics moveEvaluationDiagnostics;
  private long transferredCalculationCount;

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
    transferredCalculationCount = 0;
    executor = createThreadPoolExecutor();
    moveEvaluationPipeline = createMoveEvaluationPipeline(phaseScope.getPhaseIndex());
    moveEvaluationPipeline.setTerminationCheck(() -> termination.isPhaseTerminated(phaseScope));
    moveEvaluationPipeline.start(phaseScope.getScoreDirector());
  }

  protected MoveEvaluationPipeline<Solution_> createMoveEvaluationPipeline(int phaseIndex) {
    return new MoveEvaluationPipeline<>(
        executor,
        moveThreadCount,
        selectedMoveBufferSize,
        phaseIndex,
        false,
        assertMoveScoreFromScratch,
        assertExpectedUndoMoveScore,
        assertStepScoreFromScratch,
        assertExpectedStepScore,
        assertShadowVariablesAreNotStaleAfterStep);
  }

  @Override
  public void phaseEnded(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    moveEvaluationPipeline.close();
    // Consumed scores already count toward coordinator termination; add only the remaining work.
    phaseScope.addChildThreadsScoreCalculationCount(
        moveEvaluationPipeline.getCalculationCount() - transferredCalculationCount);
    moveEvaluationDiagnostics = moveEvaluationPipeline.getDiagnostics();
    logger.debug("{}Move evaluation diagnostics: {}", logIndentation, moveEvaluationDiagnostics);
    moveEvaluationPipeline = null;
  }

  public MoveEvaluationPipeline.Diagnostics getMoveEvaluationDiagnostics() {
    return moveEvaluationDiagnostics;
  }

  @Override
  public void solvingError(SolverScope<Solution_> solverScope, Throwable exception) {
    super.solvingError(solverScope, exception);
    if (moveEvaluationPipeline != null) {
      moveEvaluationPipeline.abort();
    }
  }

  protected ExecutorService createThreadPoolExecutor() {
    return Executors.newFixedThreadPool(
        moveThreadCount,
        ThreadUtils.requireNonNullThreads(threadFactory, "Construction Heuristic"));
  }

  @Override
  public void decideNextStep(
      ConstructionHeuristicStepScope<Solution_> stepScope, Iterator<Move<Solution_>> moveIterator) {
    int stepIndex = stepScope.getStepIndex();
    moveEvaluationPipeline.startNextStep(stepIndex);

    int selectMoveIndex = 0;
    int nextForagingMoveIndex = 0;
    int movesInPlay = 0;
    boolean terminatedPrematurely = false;

    while (moveIterator.hasNext() || movesInPlay > 0) {
      boolean hasNextMove = moveIterator.hasNext();
      if (movesInPlay > 0 && (selectMoveIndex >= selectedMoveBufferSize || !hasNextMove)) {
        var forageResult = forageResult(stepScope, stepIndex, nextForagingMoveIndex);
        if (forageResult != ForageResult.CONTINUE) {
          terminatedPrematurely = forageResult == ForageResult.TERMINATED;
          break;
        }
        nextForagingMoveIndex++;
        movesInPlay--;
      }
      if (hasNextMove) {
        var move = moveIterator.next();
        if (!isAllowedNonDoableMove(move)
            && move instanceof AbstractSelectorBasedMove<Solution_> selectorBasedMove
            && !selectorBasedMove.isMoveDoable(stepScope.getScoreDirector())) {
          continue;
        }
        moveEvaluationPipeline.submit(selectMoveIndex, move);
        selectMoveIndex++;
        movesInPlay++;
      }
    }

    moveEvaluationPipeline.cancelStep();

    // A partial CH step can worsen an optional assignment before its no-change move is considered.
    // Only complete evaluation or an intentional forager early pick may commit a move.
    if (!terminatedPrematurely) {
      pickMove(stepScope);
    }

    if (stepScope.getStep() != null) {
      var scoreDirector = stepScope.getScoreDirector();
      if (scoreDirector.requiresFlushing() && stepIndex % 100 == 99) {
        // Flush delayed score director state periodically to avoid unbounded buildup.
        scoreDirector.calculateScore();
      }
      moveEvaluationPipeline.applyStep(stepIndex + 1, stepScope.getStep(), stepScope.getScore());
    }
  }

  private ForageResult forageResult(
      ConstructionHeuristicStepScope<Solution_> stepScope, int stepIndex, int expectedMoveIndex) {
    MoveEvaluationPipeline.Result<Solution_> result;
    try {
      result = moveEvaluationPipeline.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ForageResult.TERMINATED;
    }

    if (result == null) {
      return ForageResult.TERMINATED;
    }

    if (stepIndex != result.stepIndex()) {
      throw new IllegalStateException(
          "Impossible situation: solverThread's stepIndex ("
              + stepIndex
              + ") differs from the result's stepIndex ("
              + result.stepIndex()
              + ").");
    }
    if (expectedMoveIndex != result.moveIndex()) {
      throw new IllegalStateException(
          "Impossible situation: expected moveIndex ("
              + expectedMoveIndex
              + ") differs from result moveIndex ("
              + result.moveIndex()
              + ").");
    }

    int foragingMoveIndex = result.moveIndex();
    Move<Solution_> foragingMove = result.move();
    if (foragingMove == null) {
      throw new IllegalStateException(
          "Impossible situation: no in-flight move for move index (" + foragingMoveIndex + ").");
    }

    ConstructionHeuristicMoveScope<Solution_> moveScope =
        new ConstructionHeuristicMoveScope<>(stepScope, foragingMoveIndex, foragingMove);

    if (!result.isMoveDoable()) {
      throw new IllegalStateException(
          "Impossible situation: Construction Heuristics move is not doable.");
    } else {
      moveScope.setScore(result.score());
      moveScope.getScoreDirector().incrementCalculationCount();
      transferredCalculationCount++;
      forager.addMove(moveScope);
      if (forager.isQuitEarly()) {
        return ForageResult.PICK_EARLY;
      }
    }

    stepScope.getPhaseScope().getSolverScope().checkYielding();
    return termination.isPhaseTerminated(stepScope.getPhaseScope())
        ? ForageResult.TERMINATED
        : ForageResult.CONTINUE;
  }

  private enum ForageResult {
    CONTINUE,
    PICK_EARLY,
    TERMINATED
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
