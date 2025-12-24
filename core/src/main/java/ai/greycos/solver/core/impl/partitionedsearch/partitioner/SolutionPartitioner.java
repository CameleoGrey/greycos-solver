package ai.greycos.solver.core.impl.partitionedsearch.partitioner;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Defines the strategy for splitting a planning problem into independent partitions.
 *
 * <p>Each planning entity must appear in exactly one partition. Problem facts can be shared or
 * cloned across partitions. Partitions must be independently solvable.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public interface SolutionPartitioner<Solution_> {

  /**
   * Splits a working solution into multiple partitions.
   *
   * <p>Each partition must be a complete, valid solution with:
   *
   * <ul>
   *   <li>Entities partitioned (each entity in exactly one partition)
   *   <li>Facts shared or cloned appropriately
   *   <li>Shadow variables recomputed after partitioning
   * </ul>
   *
   * @param scoreDirector The score director with the working solution
   * @param runnablePartThreadLimit Thread limit (null = unlimited)
   * @return List of partitioned solutions
   */
  List<Solution_> splitWorkingSolution(
      ScoreDirector<Solution_> scoreDirector, Integer runnablePartThreadLimit);
}
