package greycos.solver.core.api.solver.event;

import java.util.EventListener;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.change.ProblemChange;

import org.jspecify.annotations.NonNull;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@FunctionalInterface
public interface SolverEventListener<Solution_> extends EventListener {

  /**
   * Called when a new best {@link PlanningSolution} is published. The solution may be
   * uninitialized, for example when construction terminates early. Check {@link
   * BestSolutionChangedEvent#isNewBestSolutionInitialized()} and {@link Score#isFeasible()}
   * separately before using a result that must be initialized and feasible.
   *
   * <p>Called from the solver thread. <b>Should return fast, because it steals time from the {@link
   * Solver}.</b>
   *
   * <p>In real-time planning If {@link Solver#addProblemChange(ProblemChange)} has been called once
   * or more, all {@link ProblemChange}s in the queue will be processed and this method is called
   * only once. In that case, the former best {@link PlanningSolution} is considered stale, so it
   * doesn't matter whether the new {@link Score} is better than that or not.
   */
  void bestSolutionChanged(@NonNull BestSolutionChangedEvent<Solution_> event);
}
