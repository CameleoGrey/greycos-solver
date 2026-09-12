package greycos.solver.core.api.solver.alns;

/**
 * Controlled, recorded mutations valid only within the invoking callback. Never retain this view or
 * mutate working objects directly. Each completed operation leaves shadow variables up to date.
 */
public interface AlnsMutableSolutionView<Solution_> {
  /**
   * Assigns a legal value/placement and resolves that pending binding, including optional
   * unassignment.
   */
  void assign(AlnsAssignment<Solution_> assignment);

  /** Removes one movable binding, temporarily allowing mandatory decisions to be unassigned. */
  void destroy(AlnsTarget<Solution_> target);
}
