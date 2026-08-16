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

  List<Solution_> splitWorkingSolution(
      ScoreDirector<Solution_> scoreDirector, Integer runnablePartThreadLimit);
}
