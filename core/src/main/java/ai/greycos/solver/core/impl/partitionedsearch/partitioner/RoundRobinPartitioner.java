package ai.greycos.solver.core.impl.partitionedsearch.partitioner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Simple round-robin partitioner that distributes entities across partitions.
 *
 * <p>Each planning entity is assigned to a partition in round-robin order. This is a simple example
 * implementation suitable for testing.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
@NullMarked
public class RoundRobinPartitioner<Solution_> implements SolutionPartitioner<Solution_> {

  private final int partitionCount;

  /**
   * Creates a round-robin partitioner with the specified number of partitions.
   *
   * @param partitionCount Number of partitions to create
   */
  public RoundRobinPartitioner(int partitionCount) {
    if (partitionCount < 1) {
      throw new IllegalArgumentException(
          "Partition count must be at least 1, but was: " + partitionCount);
    }
    this.partitionCount = partitionCount;
  }

  @Override
  public List<Solution_> splitWorkingSolution(
      ScoreDirector<Solution_> scoreDirector, Integer runnablePartThreadLimit) {

    InnerScoreDirector<Solution_, ?> innerScoreDirector =
        (InnerScoreDirector<Solution_, ?>) scoreDirector;
    SolutionDescriptor<Solution_> solutionDescriptor = innerScoreDirector.getSolutionDescriptor();
    Solution_ workingSolution = innerScoreDirector.getWorkingSolution();

    // Determine actual partition count considering thread limit
    int actualPartCount =
        runnablePartThreadLimit != null
            ? Math.min(partitionCount, runnablePartThreadLimit)
            : partitionCount;

    // Partition entities round-robin
    Map<Integer, List<Object>> entityPartitions = new HashMap<>();
    for (int i = 0; i < actualPartCount; i++) {
      entityPartitions.put(i, new ArrayList<>());
    }

    final int[] counter = {0};
    solutionDescriptor.visitAllEntities(
        workingSolution,
        entity -> {
          int partId = counter[0]++ % actualPartCount;
          entityPartitions.get(partId).add(entity);
        });

    // Create partitioned solutions
    List<Solution_> partitions = new ArrayList<>(actualPartCount);
    for (int i = 0; i < actualPartCount; i++) {
      Solution_ partition = innerScoreDirector.cloneSolution(workingSolution);

      // Remove entities not in this partition
      List<Object> entitiesToKeep = entityPartitions.get(i);
      filterEntitiesForPartition(partition, entitiesToKeep, solutionDescriptor);

      partitions.add(partition);
    }

    return partitions;
  }

  private void filterEntitiesForPartition(
      Solution_ partition,
      List<Object> entitiesToKeep,
      SolutionDescriptor<Solution_> solutionDescriptor) {

    solutionDescriptor.visitAllEntities(
        partition,
        entity -> {
          if (!entitiesToKeep.contains(entity)) {
            // Set all genuine variables to null to "unassign" the entity
            var entityDescriptor = solutionDescriptor.findEntityDescriptorOrFail(entity.getClass());
            for (var variableDescriptor : entityDescriptor.getGenuineVariableDescriptorList()) {
              variableDescriptor.setValue(entity, null);
            }
          }
        });
  }

  /**
   * Gets the configured partition count.
   *
   * @return Number of partitions
   */
  public int getPartitionCount() {
    return partitionCount;
  }
}
