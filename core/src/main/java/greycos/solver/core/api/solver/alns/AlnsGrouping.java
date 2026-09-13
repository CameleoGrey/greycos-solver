package greycos.solver.core.api.solver.alns;

/**
 * Domain-provided grouping for group removal. Equal keys group eligible targets within the same
 * planning variable; targets belonging to different variables are never combined. Keys must be
 * nonnull and their equality and hash code must remain stable during the selection callback.
 *
 * <p>The solution and target are read-only. Instances and mutable state belong to one solve/island;
 * working objects must not be retained beyond the callback or shared with another thread.
 */
@FunctionalInterface
public interface AlnsGrouping<Solution_> {
  Object groupKey(Solution_ solution, AlnsTarget<Solution_> target);
}
