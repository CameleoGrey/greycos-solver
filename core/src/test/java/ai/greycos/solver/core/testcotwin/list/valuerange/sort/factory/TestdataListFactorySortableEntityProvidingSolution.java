package ai.greycos.solver.core.testcotwin.list.valuerange.sort.factory;

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
public class TestdataListFactorySortableEntityProvidingSolution {

  public static SolutionDescriptor<TestdataListFactorySortableEntityProvidingSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataListFactorySortableEntityProvidingSolution.class,
        TestdataListFactorySortableEntityProvidingEntity.class,
        TestdataSortableValue.class);
  }

  public static TestdataListFactorySortableEntityProvidingSolution generateSolution(
      int valueCount, int entityCount, boolean shuffle) {
    var entityList =
        new ArrayList<>(
            IntStream.range(0, entityCount)
                .mapToObj(
                    i ->
                        new TestdataListFactorySortableEntityProvidingEntity(
                            "Generated Entity " + i, i))
                .toList());
    var valueList =
        IntStream.range(0, valueCount)
            .mapToObj(i -> new TestdataSortableValue("Generated Value " + i, i))
            .toList();
    var solution = new TestdataListFactorySortableEntityProvidingSolution();
    var random = new Random(0);
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

  private List<TestdataListFactorySortableEntityProvidingEntity> entityList;
  private HardSoftScore score;

  @PlanningEntityCollectionProperty
  public List<TestdataListFactorySortableEntityProvidingEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataListFactorySortableEntityProvidingEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }

  public void removeEntity(TestdataListFactorySortableEntityProvidingEntity entity) {
    this.entityList = entityList.stream().filter(e -> e != entity).toList();
  }
}
