package greycos.solver.core.impl.localsearch.decider;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.neighborhood.MoveRepository;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;

/**
 * Multithreaded implementation of LocalSearchDecider that evaluates moves in parallel using
 * multiple worker threads. This decider coordinates move evaluation across threads while
 * maintaining the correct order of operations and proper synchronization.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class MultiThreadedLocalSearchDecider<Solution_> extends LocalSearchDecider<Solution_> {

  protected final ThreadFactory threadFactory;
  protected final int moveThreadCount;
  protected final int selectedMoveBufferSize;

  protected boolean assertStepScoreFromScratch = false;
  protected boolean assertExpectedStepScore = false;
  protected boolean assertShadowVariablesAreNotStaleAfterStep = false;

  protected ExecutorService executor;
  protected MoveEvaluationPipeline<Solution_> moveEvaluationPipeline;
  private MoveEvaluationPipeline.Diagnostics moveEvaluationDiagnostics;

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
        true,
        assertMoveScoreFromScratch,
        assertExpectedUndoMoveScore,
        assertStepScoreFromScratch,
        assertExpectedStepScore,
        assertShadowVariablesAreNotStaleAfterStep);
  }

  @Override
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    moveEvaluationPipeline.close();
    phaseScope.addChildThreadsScoreCalculationCount(moveEvaluationPipeline.getCalculationCount());
    moveEvaluationDiagnostics = moveEvaluationPipeline.getDiagnostics();
    logger.debug("{}Move evaluation diagnostics: {}", logIndentation, moveEvaluationDiagnostics);
    moveEvaluationPipeline = null;
  }

  public MoveEvaluationPipeline.Diagnostics getMoveEvaluationDiagnostics() {
    return moveEvaluationDiagnostics;
  }

  @Override
  public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
    super.solvingError(solverScope, exception);
    if (moveEvaluationPipeline != null) {
      moveEvaluationPipeline.abort();
    }
  }

  protected ExecutorService createThreadPoolExecutor() {
    return Executors.newFixedThreadPool(moveThreadCount, threadFactory);
  }

  @Override
  public void decideNextStep(LocalSearchStepScope<Solution_> stepScope) {
    int stepIndex = stepScope.getStepIndex();
    moveEvaluationPipeline.startNextStep(stepIndex);

    var pending = stepScope.getPhaseScope().getSolverScope().consumePendingMove();
    if (pending != null) {
      moveEvaluationPipeline.cancelStep();
      resetOnPendingMove = pending.requiresReset();
      var move = pending.move();
      var score =
          stepScope.getScoreDirector().executeTemporaryMove(move, assertMoveScoreFromScratch);
      stepScope.setStep(move);
      if (logger.isDebugEnabled()) {
        stepScope.setStepString(move.toString());
      }
      stepScope.setScore(score);
      stepScope.setSelectedMoveCount(1L);
      stepScope.setAcceptedMoveCount(1L);
    } else {
      int selectMoveIndex = 0;
      int movesInPlay = 0;
      Iterator<Move<Solution_>> moveIterator = moveRepository.iterator();
      boolean stoppedForagingEarly = false;

      do {
        boolean hasNextMove = moveIterator.hasNext();
        if (movesInPlay > 0 && (selectMoveIndex >= selectedMoveBufferSize || !hasNextMove)) {
          var forageResult = forageResult(stepScope, stepIndex);
          movesInPlay--;
          if (forageResult != ForageResult.CONTINUE) {
            stoppedForagingEarly = true;
            break;
          }
        }
        if (hasNextMove) {
          var move = moveIterator.next();
          moveEvaluationPipeline.submit(selectMoveIndex, move);
          selectMoveIndex++;
          movesInPlay++;
        }
      } while (movesInPlay > 0);
      if (stoppedForagingEarly && movesInPlay > 0) {
        moveEvaluationPipeline.cancelStep();
      }
      // Pick the best move from the results
      pickMove(stepScope);
    }

    // If we have a step, apply it to all threads
    if (stepScope.getStep() != null) {
      InnerScoreDirector<Solution_, ?> scoreDirector = stepScope.getScoreDirector();
      if (scoreDirector.requiresFlushing() && stepIndex % 100 == 99) {
        // Flush delayed score director state periodically to avoid unbounded buildup.
        scoreDirector.calculateScore();
      }
      moveEvaluationPipeline.applyStep(stepIndex + 1, stepScope.getStep(), stepScope.getScore());
    }
  }

  private ForageResult forageResult(LocalSearchStepScope<Solution_> stepScope, int stepIndex) {
    MoveEvaluationPipeline.Result<Solution_> result;
    try {
      result = moveEvaluationPipeline.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ForageResult.STOP;
    }

    if (result == null) {
      return ForageResult.STOP_FORAGING;
    }

    if (stepIndex != result.stepIndex()) {
      throw new IllegalStateException(
          "Impossible situation: the solverThread's stepIndex ("
              + stepIndex
              + ") differs from the result's stepIndex ("
              + result.stepIndex()
              + ").");
    }
    int foragingMoveIndex = result.moveIndex();
    Move<Solution_> foragingMove = result.move();
    if (foragingMove == null) {
      throw new IllegalStateException(
          "Impossible situation: no original move for move index (" + foragingMoveIndex + ").");
    }

    LocalSearchMoveScope<Solution_> moveScope =
        new LocalSearchMoveScope<>(stepScope, foragingMoveIndex, foragingMove);

    if (!result.isMoveDoable()) {
      logger.trace(
          "{}        Move index ({}) not doable, ignoring move ({}).",
          logIndentation,
          foragingMoveIndex,
          foragingMove);
    } else {
      moveScope.setScore(result.score());
      moveScope.getScoreDirector().incrementCalculationCount();
      boolean accepted = acceptor.isAccepted(moveScope);
      moveScope.setAccepted(accepted);
      logger.trace(
          "{}        Move index ({}), score ({}), accepted ({}), move ({}).",
          logIndentation,
          foragingMoveIndex,
          moveScope.getScore().raw(),
          moveScope.getAccepted(),
          foragingMove);
      forager.addMove(moveScope);
      if (forager.isQuitEarly()) {
        return ForageResult.STOP_FORAGING;
      }
    }

    stepScope.getPhaseScope().getSolverScope().checkYielding();
    if (termination.isPhaseTerminated(stepScope.getPhaseScope())) {
      return ForageResult.STOP_FORAGING;
    }
    return ForageResult.CONTINUE;
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

  private enum ForageResult {
    CONTINUE,
    STOP_FORAGING,
    STOP
  }
}
