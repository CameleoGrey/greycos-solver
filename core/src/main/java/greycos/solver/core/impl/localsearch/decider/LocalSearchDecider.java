package greycos.solver.core.impl.localsearch.decider;

import java.util.concurrent.CancellationException;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.move.PreparableMove;
import greycos.solver.core.impl.move.PreparedMoveEvaluation;
import greycos.solver.core.impl.move.PreparedMoveFilters;
import greycos.solver.core.impl.neighborhood.MoveRepository;
import greycos.solver.core.impl.phase.scope.SolverLifecyclePoint;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.solver.termination.Termination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class LocalSearchDecider<Solution_> implements LocalSearchPhaseDecider<Solution_> {

  protected final transient Logger logger = LoggerFactory.getLogger(getClass());

  protected final String logIndentation;
  protected final PhaseTermination<Solution_> termination;
  protected final MoveRepository<Solution_> moveRepository;
  protected final Acceptor<Solution_> acceptor;
  protected final LocalSearchForager<Solution_> forager;

  protected boolean assertMoveScoreFromScratch = false;
  protected boolean assertExpectedUndoMoveScore = false;
  protected boolean resetOnPendingMove = false;
  private LocalSearchPhaseScope<Solution_> repositoryPhaseScope;

  public LocalSearchDecider(
      String logIndentation,
      PhaseTermination<Solution_> termination,
      MoveRepository<Solution_> moveRepository,
      Acceptor<Solution_> acceptor,
      LocalSearchForager<Solution_> forager) {
    this.logIndentation = logIndentation;
    this.termination = termination;
    this.moveRepository = moveRepository;
    this.acceptor = acceptor;
    this.forager = forager;
  }

  public Termination<Solution_> getTermination() {
    return termination;
  }

  public MoveRepository<Solution_> getMoveRepository() {
    return moveRepository;
  }

  public Acceptor<Solution_> getAcceptor() {
    return acceptor;
  }

  public LocalSearchForager<Solution_> getForager() {
    return forager;
  }

  /** Worker calculations not yet credited to the local director; used only for reporting. */
  public long getUncreditedCalculationCount() {
    return 0L;
  }

  public void enableAssertions(EnvironmentMode environmentMode) {
    assertMoveScoreFromScratch = environmentMode.isFullyAsserted();
    assertExpectedUndoMoveScore = environmentMode.isIntrusivelyAsserted();
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  public void solvingStarted(SolverScope<Solution_> solverScope) {
    moveRepository.solvingStarted(solverScope);
    acceptor.solvingStarted(solverScope);
    forager.solvingStarted(solverScope);
  }

  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    startRepositoryPhase(phaseScope);
    acceptor.phaseStarted(phaseScope);
    forager.phaseStarted(phaseScope);
  }

  public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
    moveRepository.stepStarted(stepScope);
    acceptor.stepStarted(stepScope);
    forager.stepStarted(stepScope);
  }

  public void decideNextStep(LocalSearchStepScope<Solution_> stepScope) {
    var scoreDirector = stepScope.getScoreDirector();
    boolean previousTemporaryState = scoreDirector.isAllChangesWillBeUndoneBeforeStepEnds();
    scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(true);
    try {
      var pending = stepScope.getPhaseScope().getSolverScope().consumePendingMove();
      if (pending != null) {
        resetOnPendingMove = pending.requiresReset();
        var move = pending.move();
        var score = scoreDirector.executeTemporaryMove(move, assertMoveScoreFromScratch);
        stepScope.getPhaseScope().addMoveEvaluationCount(move, 1L);
        stepScope.setStep(move);
        if (logger.isDebugEnabled()) {
          stepScope.setStepString(move.toString());
        }
        stepScope.setScore(score);
        stepScope.setSelectedMoveCount(1L);
        stepScope.setAcceptedMoveCount(1L);
      } else {
        var moveIndex = 0;
        for (var move : moveRepository) {
          var moveScope = new LocalSearchMoveScope<>(stepScope, moveIndex, move);
          moveIndex++;
          doMove(moveScope);
          if (forager.isQuitEarly()) {
            break;
          }
          stepScope.getPhaseScope().getSolverScope().checkYielding();
          if (termination.isPhaseTerminated(stepScope.getPhaseScope())) {
            break;
          }
        }
        pickMove(stepScope);
      }
    } finally {
      scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(previousTemporaryState);
    }
  }

  protected <Score_ extends Score<Score_>> void doMove(LocalSearchMoveScope<Solution_> moveScope) {
    var scoreDirector = moveScope.<Score_>getScoreDirector();
    var move = moveScope.getMove();
    InnerScore<Score_> score;
    if (move instanceof PreparableMove<Solution_> preparableMove) {
      var stepScope = moveScope.getStepScope();
      var result =
          preparableMove.prepare(
              scoreDirector,
              () -> checkPreparationTermination(stepScope),
              assertMoveScoreFromScratch,
              null);
      if (result.status() != PreparedMoveEvaluation.Status.EVALUATED) {
        return;
      }
      move = PreparedMoveFilters.filter(result.move(), scoreDirector);
      if (move == null) {
        return;
      }
      moveScope = new LocalSearchMoveScope<>(stepScope, moveScope.getMoveIndex(), move);
      score = result.score();
    } else {
      if (move instanceof AbstractSelectorBasedMove<Solution_> selectorBasedMove
          && !selectorBasedMove.isMoveDoable(scoreDirector)) {
        throw new IllegalStateException(
            "Impossible state: Local search move selector (%s) provided a non-doable move (%s)."
                .formatted(moveRepository, move));
      }
      score = scoreDirector.executeTemporaryMove(move, assertMoveScoreFromScratch);
    }
    moveScope.setScore(score);
    moveScope.setAccepted(acceptor.isAccepted(moveScope));
    forager.addMove(moveScope);
    if (assertExpectedUndoMoveScore) {
      scoreDirector.assertExpectedUndoMoveScore(
          moveScope.getMove(),
          moveScope.getStepScope().getPhaseScope().getLastCompletedStepScope().getScore(),
          SolverLifecyclePoint.of(moveScope));
    }
    logger.trace(
        "{}        Move index ({}), score ({}), accepted ({}), move ({}).",
        logIndentation,
        moveScope.getMoveIndex(),
        moveScope.getScore().raw(),
        moveScope.getAccepted(),
        moveScope.getMove());
  }

  protected void checkPreparationTermination(LocalSearchStepScope<Solution_> stepScope) {
    stepScope.getPhaseScope().getSolverScope().checkYielding();
    if (Thread.currentThread().isInterrupted()
        || termination.isPhaseTerminated(stepScope.getPhaseScope())) {
      throw new CancellationException("Local search move preparation terminated.");
    }
  }

  protected void pickMove(LocalSearchStepScope<Solution_> stepScope) {
    var pickedMoveScope = forager.pickMove(stepScope);
    if (pickedMoveScope != null) {
      var step = pickedMoveScope.getMove();
      stepScope.setStep(step);
      if (logger.isDebugEnabled()) {
        stepScope.setStepString(step.toString());
      }
      stepScope.setScore(pickedMoveScope.getScore());
    }
  }

  public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
    moveRepository.stepEnded(stepScope);
    acceptor.stepEnded(stepScope);
    forager.stepEnded(stepScope);
    if (resetOnPendingMove) {
      resetOnPendingMove = false;
      resetLocalSearchState(stepScope);
    }
  }

  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    endRepositoryPhase();
    acceptor.phaseEnded(phaseScope);
    forager.phaseEnded(phaseScope);
  }

  protected void resetLocalSearchState(LocalSearchStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    phaseScope.setLastCompletedStepScope(stepScope);
    endRepositoryPhase();
    acceptor.phaseEnded(phaseScope);
    forager.phaseEnded(phaseScope);
    startRepositoryPhase(phaseScope);
    acceptor.phaseStarted(phaseScope);
    forager.phaseStarted(phaseScope);
  }

  public void solvingEnded(SolverScope<Solution_> solverScope) {
    moveRepository.solvingEnded(solverScope);
    acceptor.solvingEnded(solverScope);
    forager.solvingEnded(solverScope);
  }

  private void startRepositoryPhase(LocalSearchPhaseScope<Solution_> phaseScope) {
    if (repositoryPhaseScope != null) {
      throw new IllegalStateException(
          "The local search move repository already has an active phase.");
    }
    // Track partial startup too: a failing provider may already own an evaluation session.
    repositoryPhaseScope = phaseScope;
    moveRepository.phaseStarted(phaseScope);
  }

  private void endRepositoryPhase() {
    var phaseScope = repositoryPhaseScope;
    if (phaseScope == null) return;
    repositoryPhaseScope = null;
    moveRepository.phaseEnded(phaseScope);
  }

  public void solvingError(SolverScope<Solution_> solverScope, Throwable exception) {
    try {
      endRepositoryPhase();
    } catch (RuntimeException | Error cleanup) {
      if (cleanup != exception) exception.addSuppressed(cleanup);
    }
  }
}
