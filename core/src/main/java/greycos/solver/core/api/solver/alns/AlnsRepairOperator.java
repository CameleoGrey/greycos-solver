package greycos.solver.core.api.solver.alns;

import java.util.List;

import greycos.solver.core.api.score.Score;

/**
 * Repairs the pending bindings through recorded context operations. Every binding must be resolved
 * explicitly, including optional bindings left unassigned. Return false if repair cannot complete;
 * the framework restores the incumbent. Call {@link AlnsContext#checkTermination()} during loops.
 * Instances and mutable state belong to one solve/island and must not be shared across threads.
 */
@FunctionalInterface
public interface AlnsRepairOperator<Solution_, Score_ extends Score<Score_>> {
  boolean repair(AlnsContext<Solution_, Score_> context, List<AlnsTarget<Solution_>> pending);
}
