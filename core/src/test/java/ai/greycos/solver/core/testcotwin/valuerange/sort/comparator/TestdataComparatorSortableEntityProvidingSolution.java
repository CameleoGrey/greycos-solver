package ai.greycos.solver.core.testcotwin.valuerange.sort.comparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningSolution
public class TestdataComparatorSortableEntityProvidingSolution {

  public static SolutionDescriptor<TestdataComparatorSortableEntityProvidingSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataComparatorSortableEntityProvidingSolution.class,
        TestdataComparatorSortableEntityProvidingEntity.class);
  }

  public static TestdataComparatorSortableEntityProvidingSolution generateSolution(
      int valueCount, int entityCount, boolean shuffle) {
    var entityList =
        new ArrayList<>(
            IntStream.range(0, entityCount)
                .mapToObj(
                    i ->
                        new TestdataComparatorSortableEntityProvidingEntity(
                            "Generated Entity " + i, i))
                .toList());
    var valueList =
        IntStream.range(0, valueCount)
            .mapToObj(i -> new TestdataSortableValue("Generated Value " + i, i))
            .toList();
    var random = new Random(0);
    var solution = new TestdataComparatorSortableEntityProvidingSolution();
    for (var entity : entityList) {
      var valueRange = new ArrayList<>(valueList);
      if (shuffle) {
        Collections.shuffle(valueRange, random);
      }
      entity.setValueRange(valueRange);
    }
    if (shuffle) {
      Collections.shuffle(entityList, random);
    }
    solution.setEntityList(entityList);
    return solution;
  }

  private List<TestdataComparatorSortableEntityProvidingEntity> entityList;
  private HardSoftScore score;

  @PlanningEntityCollectionProperty
  public List<TestdataComparatorSortableEntityProvidingEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataComparatorSortableEntityProvidingEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }

  public void removeEntity(TestdataComparatorSortableEntityProvidingEntity entity) {
    this.entityList = entityList.stream().filter(e -> e != entity).toList();
  }
}
