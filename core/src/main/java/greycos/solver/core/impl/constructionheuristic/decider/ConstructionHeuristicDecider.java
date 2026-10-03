package greycos.solver.core.impl.constructionheuristic.decider;

import java.util.Iterator;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import greycos.solver.core.impl.constructionheuristic.nearby.ProgressiveConstructionIterator;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedCompositeMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import greycos.solver.core.impl.phase.scope.SolverLifecyclePoint;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class ConstructionHeuristicDecider<Solution_> {

  protected final transient Logger logger = LoggerFactory.getLogger(getClass());

  protected final String logIndentation;
  protected final PhaseTermination<Solution_> termination;
  protected final ConstructionHeuristicForager<Solution_> forager;

  protected boolean assertMoveScoreFromScratch = false;
  protected boolean assertExpectedUndoMoveScore = false;

  public ConstructionHeuristicDecider(
      String logIndentation,
      PhaseTermination<Solution_> termination,
      ConstructionHeuristicForager<Solution_> forager) {
    this.logIndentation = logIndentation;
    this.termination = termination;
    this.forager = forager;
  }

  public boolean isLoggingEnabled() {
    return true;
  }

  public ConstructionHeuristicForager<Solution_> getForager() {
    return forager;
  }

  public void enableAssertions(EnvironmentMode environmentMode) {
    this.assertMoveScoreFromScratch = environmentMode.isFullyAsserted();
    this.assertExpectedUndoMoveScore = environmentMode.isIntrusivelyAsserted();
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  public void solvingStarted(SolverScope<Solution_> solverScope) {
    forager.solvingStarted(solverScope);
  }

  public void phaseStarted(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
    forager.phaseStarted(phaseScope);
  }

  public void stepStarted(ConstructionHeuristicStepScope<Solution_> stepScope) {
    forager.stepStarted(stepScope);
  }

  public void stepEnded(ConstructionHeuristicStepScope<Solution_> stepScope) {
    forager.stepEnded(stepScope);
  }

  public void phaseEnded(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
    forager.phaseEnded(phaseScope);
  }

  public void solvingEnded(SolverScope<Solution_> solverScope) {
    forager.solvingEnded(solverScope);
  }

  public void solvingError(SolverScope<Solution_> solverScope, Throwable exception) {
    // Overridable by a subclass.
  }

  public void decideNextStep(
      ConstructionHeuristicStepScope<Solution_> stepScope, Iterator<Move<Solution_>> moveIterator) {
    var progressiveIterator =
        moveIterator instanceof ProgressiveConstructionIterator<Solution_> progressive
            ? progressive
            : null;
    var moveIndex = 0;
    var terminatedPrematurely = false;
    try {
      while (true) {
        if (Thread.currentThread().isInterrupted()) {
          terminatedPrematurely = true;
          break;
        }
        if (!moveIterator.hasNext()) {
          // Seeking the next candidate may itself observe cancellation while skipping empty ranges.
          if (Thread.currentThread().isInterrupted()
              || termination.isPhaseTerminated(stepScope.getPhaseScope())) {
            terminatedPrematurely = true;
            break;
          }
          if (progressiveIterator != null && progressiveIterator.advanceBatch()) {
            continue;
          }
          break;
        }
        var move = moveIterator.next();
        var allowedNonDoableMove = isAllowedNonDoableMove(move);
        if (!allowedNonDoableMove) {
          if (move instanceof AbstractSelectorBasedMove<Solution_> selectorBasedMove
              && !selectorBasedMove.isMoveDoable(stepScope.getScoreDirector())) {
            // Construction Heuristic should not do non-doable moves, but in some cases, it has to.
            // Specifically:
            //      1/ NoChangeMove for list variable; means "try to not assign that value".
            //      2/ ChangeMove for basic variable; move from null to null means "try to not
            // assign
            // that value".
            // Every other non-doable move must not be executed, as it may cause all sorts of
            // issues.
            // Example: ListChangeMove from a[0] to a[1] when the list of 'a' only has 1 element.
            //      This move is correctly non-doable,
            //      but it may be generated by the placer, and must therefore be ignored.
            // A random selector may keep producing rejected candidates indefinitely. Poll here
            // too, so time limits and external cancellation do not depend on scoring a move.
            stepScope.getPhaseScope().getSolverScope().checkYielding();
            if (Thread.currentThread().isInterrupted()
                || termination.isPhaseTerminated(stepScope.getPhaseScope())) {
              terminatedPrematurely = true;
              break;
            }
            continue;
          }
        }
        var moveScope = new ConstructionHeuristicMoveScope<>(stepScope, moveIndex, move);
        moveIndex++;
        doMove(moveScope);
        if (progressiveIterator != null) {
          progressiveIterator.recordScore(moveScope);
        }
        if (Thread.currentThread().isInterrupted()) {
          terminatedPrematurely = true;
          break;
        }
        if (forager.isQuitEarly()) {
          break;
        }
        stepScope.getPhaseScope().getSolverScope().checkYielding();
        if (termination.isPhaseTerminated(stepScope.getPhaseScope())) {
          terminatedPrematurely = true;
          break;
        }
      }
    } finally {
      if (progressiveIterator != null) {
        stepScope.setNearbyWideningCount(progressiveIterator.getWideningCount());
        progressiveIterator.close();
      }
    }
    // Only pick a move when CH has finished all moves within the step, or when pick early was
    // enabled.
    // If CH terminated prematurely, it means a move could have been picked which makes the solution
    // worse,
    // while there were moves still to be evaluated that could have been better.
    // This typically happens for list variable with allowsUnassignedValues=true,
    // where most moves make the score worse and the only move that doesn't is the move which
    // assigns null.
    // This move typically comes last, and therefore if the phase terminates early, it will not be
    // attempted.
    if (!terminatedPrematurely) {
      pickMove(stepScope);
    }
  }

  protected static <Solution_> boolean isAllowedNonDoableMove(Move<Solution_> move) {
    if (move instanceof SelectorBasedCompositeMove<Solution_> compositeMove) {
      for (var child : compositeMove.getMoves()) {
        if (!isAllowedNonDoableMove(child)) {
          return false;
        }
      }
      // A Cartesian product of optional assignments must include its all-null alternative.
      return true;
    }
    return move instanceof SelectorBasedNoChangeMove<Solution_>
        || move instanceof SelectorBasedChangeMove<Solution_>;
  }

  protected void pickMove(ConstructionHeuristicStepScope<Solution_> stepScope) {
    var pickedMoveScope = forager.pickMove(stepScope);
    if (pickedMoveScope != null) {
      var step = pickedMoveScope.getMove();
      stepScope.setStep(step);
      if (isLoggingEnabled() && logger.isDebugEnabled()) {
        stepScope.setStepString(step.toString());
      }
      stepScope.setScore(pickedMoveScope.getScore());
    }
  }

  protected void doMove(ConstructionHeuristicMoveScope<Solution_> moveScope) {
    var scoreDirector = moveScope.getScoreDirector();
    var score = scoreDirector.executeTemporaryMove(moveScope.getMove(), assertMoveScoreFromScratch);
    moveScope.setScore(score);
    forager.addMove(moveScope);
    if (assertExpectedUndoMoveScore) {
      scoreDirector.assertExpectedUndoMoveScore(
          moveScope.getMove(),
          moveScope.getStepScope().getPhaseScope().getLastCompletedStepScope().getScore(),
          SolverLifecyclePoint.of(moveScope));
    }
    if (isLoggingEnabled()) {
      logger.trace(
          "{}        Move index ({}), score ({}), move ({}).",
          logIndentation,
          moveScope.getMoveIndex(),
          moveScope.getScore().raw(),
          moveScope.getMove());
    }
  }
}
