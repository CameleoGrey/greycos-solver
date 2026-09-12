package greycos.solver.core.api.solver.alns;

import java.util.List;

import greycos.solver.core.api.score.Score;

/**
 * Selects at most the requested number of unique eligible destruction targets. The framework
 * applies the destruction in canonical order. Selection may use scratch evaluations but must return
 * with the incumbent unchanged. Instances and any mutable operator state belong to one
 * solve/island.
 */
@FunctionalInterface
public interface AlnsDestroyOperator<Solution_, Score_ extends Score<Score_>> {
  List<AlnsTarget<Solution_>> select(AlnsContext<Solution_, Score_> context, int size);
}
