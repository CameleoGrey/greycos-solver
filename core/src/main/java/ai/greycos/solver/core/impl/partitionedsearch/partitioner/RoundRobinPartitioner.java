package ai.greycos.solver.core.impl.partitionedsearch.partitioner;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Simple round-robin partitioner that distributes entities across partitions.
 *
 * <p>This is a basic example implementation for testing. For production use, implement a
 * partitioner specific to your domain that considers entity relationships and constraints.
 *
 * <p>Each planning entity is assigned to exactly one partition in round-robin order. Problem facts
 * are cloned to all partitions.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
@NullMarked
public class RoundRobinPartitioner<Solution_> implements SolutionPartitioner<Solution_> {

  private final int partCount;

  /**
   * Creates a round-robin partitioner with the specified number of partitions.
   *
   * @param partCount Number of partitions to create
   */
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

    // Adjust partition count based on thread limit
    int actualPartCount =
        runnablePartThreadLimit != null ? Math.min(partCount, runnablePartThreadLimit) : partCount;

    // Collect all entities
    final List<Object> allEntities = new ArrayList<>();
    solutionDescriptor.visitAllEntities(workingSolution, entity -> allEntities.add(entity));

    // Create partitions
    List<Solution_> partList = new ArrayList<>(actualPartCount);
    for (int i = 0; i < actualPartCount; i++) {
      Solution_ partSolution = innerScoreDirector.cloneSolution(workingSolution);
      partList.add(partSolution);
    }

    // Distribute entities round-robin, preserving their variable assignments
    int partIndex = 0;
    for (Object entity : allEntities) {
      Solution_ part = partList.get(partIndex);
      // Entity is already in the partition from the clone
      // with its variable assignments preserved
      partIndex = (partIndex + 1) % actualPartCount;
    }

    return partList;
  }

  /**
   * Gets the configured partition count.
   *
   * @return Number of partitions
   */
  public int getPartCount() {
    return partCount;
  }
}
