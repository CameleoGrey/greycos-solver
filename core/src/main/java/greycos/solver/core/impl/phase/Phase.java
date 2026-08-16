package greycos.solver.core.impl.phase;

import java.util.function.IntFunction;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * A phase of a {@link Solver}.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @see AbstractPhase
 */
public interface Phase<Solution_> extends PhaseLifecycleListener<Solution_> {

  /**
   * Add a {@link PhaseLifecycleListener} that is only notified of the {@link
   * PhaseLifecycleListener#phaseStarted(AbstractPhaseScope) phase} and the {@link
   * PhaseLifecycleListener#stepStarted(AbstractStepScope) step} starting/ending events from this
   * phase (and the {@link PhaseLifecycleListener#solvingStarted(SolverScope) solving} events too of
   * course).
   *
   * <p>To get notified for all phases, use {@link
   * DefaultSolver#addPhaseLifecycleListener(PhaseLifecycleListener)} instead.
   *
   * @param phaseLifecycleListener never null
   */
  void addPhaseLifecycleListener(PhaseLifecycleListener<Solution_> phaseLifecycleListener);

  /**
   * @param phaseLifecycleListener never null
   * @see #addPhaseLifecycleListener(PhaseLifecycleListener)
   */
  void removePhaseLifecycleListener(PhaseLifecycleListener<Solution_> phaseLifecycleListener);

  void solve(SolverScope<Solution_> solverScope);

  IntFunction<EventProducerId> getEventProducerIdSupplier();
}
