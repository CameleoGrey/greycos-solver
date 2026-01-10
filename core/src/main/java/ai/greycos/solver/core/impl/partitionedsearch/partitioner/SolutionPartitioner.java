package ai.greycos.solver.core.impl.partitionedsearch.partitioner;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Strategy for splitting planning problems into independent partitions.
 *
 * <p>Each entity must appear in exactly one partition. Facts can be shared or cloned.
 * Partitions must be independently solvable.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public interface SolutionPartitioner<Solution_> {

  List<Solution_> splitWorkingSolution(
      ScoreDirector<Solution_> scoreDirector, Integer runnablePartThreadLimit);
}
