package greycos.solver.core.impl.partitionedsearch;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.phase.Phase;

/**
 * Partitioned search phase - splits problem into parallel sub-solvers.
 *
 * <p>Divides planning problem into independent partitions, solves them concurrently with configured
 * phases, and aggregates improvements back into the main solution.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public interface PartitionedSearchPhase<Solution_> extends Phase<Solution_> {}
