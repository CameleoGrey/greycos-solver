/**
 * Partitioned search implementation for GreyCOS Solver.
 *
 * <p>Partitioned search enables parallel solving of problem sub-cotwins by splitting a planning
 * problem into independent partitions and solving them concurrently.
 *
 * <p>Key components:
 *
 * <ul>
 *   <li>{@link PartitionedSearchPhase} - Main phase implementation
 *   <li>{@link SolutionPartitioner} - Partitioning strategy interface
 *   <li>{@link PartitionSolver} - Sub-solver for partitions
 *   <li>{@link PartitionQueue} - Thread-safe communication queue
 *   <li>{@link PartitionChangeMove} - Encapsulates partition improvements
 * </ul>
 */
package ai.greycos.solver.core.impl.partitionedsearch;

import ai.greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import ai.greycos.solver.core.impl.partitionedsearch.queue.PartitionQueue;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;
