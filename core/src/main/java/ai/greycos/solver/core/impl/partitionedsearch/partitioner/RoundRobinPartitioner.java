package ai.greycos.solver.core.impl.partitionedsearch.partitioner;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Simple round-robin partitioner for testing (not production-ready).
 *
 * <p>Distributes entities round-robin across partitions; clones facts to all partitions. For
 * production, implement domain-specific partitioner considering entity relationships.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
@NullMarked
public class RoundRobinPartitioner<Solution_> implements SolutionPartitioner<Solution_> {

  private final int partCount;

  public RoundRobinPartitioner(int partCount) {
    if (partCount < 1) {
      throw new IllegalArgumentException(
          "Partition count must be at least 1, but was: " + partCount);
    }
    this.partCount = partCount;
  }

  @Override
  public List<Solution_> splitWorkingSolution(
      ScoreDirector<Solution_> scoreDirector, Integer runnablePartThreadLimit) {

    InnerScoreDirector<Solution_, ?> innerScoreDirector =
        (InnerScoreDirector<Solution_, ?>) scoreDirector;
    SolutionDescriptor<Solution_> solutionDescriptor = innerScoreDirector.getSolutionDescriptor();
    Solution_ workingSolution = innerScoreDirector.getWorkingSolution();

    int actualPartCount =
        runnablePartThreadLimit != null ? Math.min(partCount, runnablePartThreadLimit) : partCount;

    final List<Object> allEntities = new ArrayList<>();
    solutionDescriptor.visitAllEntities(workingSolution, entity -> allEntities.add(entity));

    List<Solution_> partList = new ArrayList<>(actualPartCount);
    for (int i = 0; i < actualPartCount; i++) {
      Solution_ partSolution = innerScoreDirector.cloneSolution(workingSolution);
      partList.add(partSolution);
    }

    int partIndex = 0;
    for (Object entity : allEntities) {
      Solution_ part = partList.get(partIndex);
      partIndex = (partIndex + 1) % actualPartCount;
    }

    return partList;
  }

  public int getPartCount() {
    return partCount;
  }
}
