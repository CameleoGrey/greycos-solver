package greycos.solver.core.api.solver.alns;

/**
 * A change expressed through safe mutations. The callback must not retain its view or modify facts.
 */
@FunctionalInterface
public interface AlnsChange<Solution_> {
  void apply(AlnsMutableSolutionView<Solution_> view);
}
