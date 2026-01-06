package ai.greycos.solver.core.impl.localsearch.decider;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.heuristic.move.MoveAdapters;
import ai.greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.neighborhood.MoveRepository;
import ai.greycos.solver.core.impl.phase.scope.SolverLifecyclePoint;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.solver.termination.Termination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class LocalSearchDecider<Solution_> {

  protected final transient Logger logger = LoggerFactory.getLogger(getClass());

  protected final String logIndentation;
  protected final PhaseTermination<Solution_> termination;
  protected final MoveRepository<Solution_> moveRepository;
  protected final Acceptor<Solution_> acceptor;
  protected final LocalSearchForager<Solution_> forager;

  protected boolean assertMoveScoreFromScratch = false;
  protected boolean assertExpectedUndoMoveScore = false;
  protected boolean resetOnPendingMove = false;

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
    moveRepository.phaseStarted(phaseScope);
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
    scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(true);
    var pending = stepScope.getPhaseScope().getSolverScope().consumePendingMove();
    if (pending != null) {
      resetOnPendingMove = pending.requiresReset();
      var move = pending.move();
      var score = scoreDirector.executeTemporaryMove(move, assertMoveScoreFromScratch);
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
    scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(false);
  }

  protected <Score_ extends Score<Score_>> void doMove(LocalSearchMoveScope<Solution_> moveScope) {
    var scoreDirector = moveScope.<Score_>getScoreDirector();
    var moveDirector = moveScope.getStepScope().<Score_>getMoveDirector();
    var move = moveScope.getMove();
    if (!MoveAdapters.isDoable(moveDirector, move)) {
      throw new IllegalStateException(
          "Impossible state: Local search move selector (%s) provided a non-doable move (%s)."
              .formatted(moveRepository, move));
    }
    var score = scoreDirector.executeTemporaryMove(moveScope.getMove(), assertMoveScoreFromScratch);
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
    moveRepository.phaseEnded(phaseScope);
    acceptor.phaseEnded(phaseScope);
    forager.phaseEnded(phaseScope);
  }

  protected void resetLocalSearchState(LocalSearchStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    phaseScope.setLastCompletedStepScope(stepScope);
    moveRepository.phaseEnded(phaseScope);
    acceptor.phaseEnded(phaseScope);
    forager.phaseEnded(phaseScope);
    moveRepository.phaseStarted(phaseScope);
    acceptor.phaseStarted(phaseScope);
    forager.phaseStarted(phaseScope);
  }

  public void solvingEnded(SolverScope<Solution_> solverScope) {
    moveRepository.solvingEnded(solverScope);
    acceptor.solvingEnded(solverScope);
    forager.solvingEnded(solverScope);
  }

  public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
    // Overridable by a subclass.
  }
}
