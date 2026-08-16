package greycos.solver.core.impl.neighborhood;

import java.util.Iterator;
import java.util.Objects;

import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.SessionContext;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MoveSelectorBasedMoveRepository<Solution_>
    implements MoveRepository<Solution_>, PhaseLifecycleListener<Solution_> {

  private final MoveSelector<Solution_> moveSelector;

  public MoveSelectorBasedMoveRepository(MoveSelector<Solution_> moveSelector) {
    this.moveSelector = Objects.requireNonNull(moveSelector);
  }

  @Override
  public boolean isNeverEnding() {
    return moveSelector.isNeverEnding();
  }

  @Override
  public void initialize(SessionContext<Solution_> context) {
    // No need to do anything.
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    moveSelector.solvingStarted(solverScope);
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    moveSelector.phaseStarted(phaseScope);
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> stepScope) {
    moveSelector.stepStarted(stepScope);
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    moveSelector.stepEnded(stepScope);
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    moveSelector.phaseEnded(phaseScope);
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    moveSelector.solvingEnded(solverScope);
  }

  @Override
  public Iterator<Move<Solution_>> iterator() {
    return moveSelector.iterator();
  }
}
