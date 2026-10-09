package greycos.solver.core.impl.constructionheuristic.placer;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

/** Constructs one complete proposal; completing its step exhausts the placement queue. */
public final class RandomAssignmentEntityPlacer<Solution_>
    extends PhaseLifecycleListenerAdapter<Solution_> implements EntityPlacer<Solution_> {

  private AbstractPhaseScope<Solution_> phaseScope;
  private boolean completed;

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    this.phaseScope = phaseScope;
    completed = false;
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    completed = true;
  }

  @Override
  public Iterator<Placement<Solution_>> iterator() {
    var scope =
        Objects.requireNonNull(
            phaseScope,
            "RANDOM_ASSIGNMENT phaseScope is null. Start the phase before creating a placement iterator.");
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        // Keep an interrupted, uncommitted placement pending so CH reports early termination.
        return !completed;
      }

      @Override
      public Placement<Solution_> next() {
        if (!hasNext()) {
          throw new NoSuchElementException(
              "RANDOM_ASSIGNMENT placement is already completed (true).");
        }
        return new Placement<>(
            new UpcomingSelectionIterator<>() {
              private boolean sampled;

              @Override
              protected Move<Solution_> createUpcomingSelection() {
                if (sampled) {
                  return noUpcomingSelection();
                }
                sampled = true;
                var move =
                    RandomAssignmentMove.sample(
                        scope.getScoreDirector(),
                        scope.getWorkingRandom().moveIteratorUsage(),
                        () -> {
                          scope.getSolverScope().checkYielding();
                          return Thread.currentThread().isInterrupted()
                              || scope.getTermination().isPhaseTerminated(scope);
                        });
                return move == null ? noUpcomingSelection() : move;
              }
            });
      }
    };
  }

  @Override
  public EntityPlacer<Solution_> copy() {
    return new RandomAssignmentEntityPlacer<>();
  }

  @Override
  public EntityPlacer<Solution_> rebuildWithFilter(SelectionFilter<Solution_, Object> filter) {
    throw new UnsupportedOperationException(
        "RANDOM_ASSIGNMENT constructs a whole solution and does not support repair with filter (%s). Use a construction heuristic that supports filtered repair."
            .formatted(filter));
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    clear();
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    clear();
  }

  @Override
  public void solvingError(SolverScope<Solution_> solverScope, Throwable exception) {
    clear();
  }

  private void clear() {
    phaseScope = null;
    completed = false;
  }
}
