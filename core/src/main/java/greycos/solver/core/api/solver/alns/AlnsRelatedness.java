package greycos.solver.core.api.solver.alns;

/** Domain-provided relatedness distance; smaller finite, nonnegative values mean more related. */
@FunctionalInterface
public interface AlnsRelatedness<Solution_> {
  double distance(Solution_ solution, AlnsTarget<Solution_> left, AlnsTarget<Solution_> right);
}
