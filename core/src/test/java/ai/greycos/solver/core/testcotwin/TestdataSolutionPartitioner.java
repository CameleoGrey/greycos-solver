package ai.greycos.solver.core.testcotwin;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

/**
 * Test {@link SolutionPartitioner} implementation that partitions a {@link TestdataSolution} into
 * multiple sub-solutions based on a configurable partition size (default 1).
 *
 * <p>This partitioner distributes entities evenly across partitions. If the number of entities is
 * not evenly divisible by the partition size, the last partition will have fewer entities.
 *
 * <p>The partition size can be configured using a system property "testPartitionSize" or defaults
 * to 1.
 */
public class TestdataSolutionPartitioner implements SolutionPartitioner<TestdataSolution> {

  private static final int DEFAULT_PART_SIZE = 1;
  private static final String PART_SIZE_PROPERTY = "testPartitionSize";

  @Override
  public List<TestdataSolution> splitWorkingSolution(
      ScoreDirector<TestdataSolution> scoreDirector, Integer runnablePartThreadLimit) {
    TestdataSolution workingSolution = scoreDirector.getWorkingSolution();
    List<TestdataEntity> entityList = workingSolution.getEntityList();
    List<TestdataValue> valueList = workingSolution.getValueList();

    // Get partition size from system property or use default
    int partSize =
        Integer.parseInt(System.getProperty(PART_SIZE_PROPERTY, String.valueOf(DEFAULT_PART_SIZE)));

    int partCount = (entityList.size() + partSize - 1) / partSize;
    List<TestdataSolution> partList = new ArrayList<>(partCount);

    for (int i = 0; i < partCount; i++) {
      TestdataSolution partSolution = new TestdataSolution();
      partSolution.setValueList(new ArrayList<>(valueList));

      int startIndex = i * partSize;
      int endIndex = Math.min(startIndex + partSize, entityList.size());
      List<TestdataEntity> partEntityList = new ArrayList<>();
      for (int j = startIndex; j < endIndex; j++) {
        TestdataEntity entity = entityList.get(j);
        TestdataEntity clonedEntity = new TestdataEntity(entity.getCode());
        clonedEntity.setValue(entity.getValue());
        partEntityList.add(clonedEntity);
      }
      partSolution.setEntityList(partEntityList);

      partList.add(partSolution);
    }

    return partList;
  }
}
