package ai.greycos.solver.core.impl.heuristic.thread;

/**
 * Base class for all move thread operations. These operations are used to communicate between the
 * main solver thread and move threads.
 *
 * @param <Solution_> the solution type, the class with the {@link
 *     ai.greycos.solver.core.api.cotwin.solution.PlanningSolution} annotation
 */
public abstract class MoveThreadOperation<Solution_> {

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
