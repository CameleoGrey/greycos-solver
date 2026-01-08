/**
 * Partitioned search implementation for GreyCOS Solver.
 *
 * <p>Partitioned search enables parallel solving of problem sub-domains by splitting a planning
 * problem into independent partitions and solving them concurrently.
 *
 * <p>Key components:
 *
 * <ul>
 *   <li>{@link ai.greycos.solver.core.impl.partitionedsearch.PartitionedSearchPhase} - Main phase
 *       implementation
 *   <li>{@link ai.greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner} -
 *       Partitioning strategy interface
 *   <li>{@link ai.greycos.solver.core.impl.partitionedsearch.PartitionSolver} - Sub-solver for
 *       partitions
 *   <li>{@link ai.greycos.solver.core.impl.partitionedsearch.queue.PartitionQueue} - Thread-safe
 *       communication queue
 *   <li>{@link ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove} -
 *       Encapsulates partition improvements
 * </ul>
 */
package ai.greycos.solver.core.impl.partitionedsearch;
