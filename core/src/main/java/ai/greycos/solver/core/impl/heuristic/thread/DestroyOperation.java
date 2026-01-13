package ai.greycos.solver.core.impl.heuristic.thread;

/**
 * Operation to signal the destruction of a move thread. This operation is used to cleanly shut down
 * move threads.
 *
 * @param <Solution_> the solution type, the class with the {@link
 *     ai.greycos.solver.core.api.cotwin.solution.PlanningSolution} annotation
 */
public class DestroyOperation<Solution_> extends MoveThreadOperation<Solution_> {}
