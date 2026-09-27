package greycos.solver.core.impl.partitionedsearch.partitioner;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.score.director.ScoreDirector;

/**
 * Strategy for splitting planning problems into independent partitions.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public interface SolutionPartitioner<Solution_> {

  /**
   * Return independent subproblems with the same planning IDs as the parent. Each movable entity
   * and each assignable list value must have exactly one partition owner. List values already
   * assigned in the parent must stay with their entity's partition; initially unassigned values may
   * be divided freely. Values with genuine variables must have the same owner as their list
   * assignment.
   *
   * <p>Preserve parent pinning and pinned list prefixes. A partition may include consistent pinned
   * copies of boundary entities; their pinned list values may appear in several ranges. Other list
   * ranges must be disjoint and cover every assignable parent value. Basic value ranges may
   * overlap, but all non-null assignments must belong to both the partition and parent entity's
   * range. Required basic variables need nonempty ranges. Leave a basic variable unassigned if its
   * previous value is removed from its partition range.
   *
   * <p>The solver clones each partition before changing it, so returned inputs may refer to parent
   * entities. The solution cloner must isolate mutable entities, list containers and shadow state.
   * Immutable facts may be shared. Constraints coupling mutable decisions in different partitions
   * remain the partitioner's responsibility.
   *
   * @param scoreDirector the parent score director; do not modify its working solution
   * @param runnablePartThreadLimit maximum concurrently runnable children, or null for unlimited;
   *     the partitioner may return a different number of partitions
   * @return non-null list of non-null partition solutions
   */
  List<Solution_> splitWorkingSolution(
      ScoreDirector<Solution_> scoreDirector, Integer runnablePartThreadLimit);
}
