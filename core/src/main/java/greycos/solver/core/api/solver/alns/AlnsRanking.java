package greycos.solver.core.api.solver.alns;

/** Domain-provided removal ranking; larger finite values are considered first. */
@FunctionalInterface
public interface AlnsRanking<Solution_> {
  double rank(Solution_ solution, AlnsTarget<Solution_> target);
}
