package greycos.solver.core.testcotwin.valuerange.sort.factorystrength;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningSolution
public class TestdataStrengthFactorySortableEntityProvidingSolution {

  public static SolutionDescriptor<TestdataStrengthFactorySortableEntityProvidingSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataStrengthFactorySortableEntityProvidingSolution.class,
        TestdataStrengthFactorySortableEntityProvidingEntity.class);
  }

  public static TestdataStrengthFactorySortableEntityProvidingSolution generateSolution(
      int valueCount, int entityCount, boolean shuffle) {
    var entityList =
        new ArrayList<>(
            IntStream.range(0, entityCount)
                .mapToObj(
                    i ->
                        new TestdataStrengthFactorySortableEntityProvidingEntity(
                            "Generated Entity " + i, i))
                .toList());
    var valueList =
        IntStream.range(0, valueCount)
            .mapToObj(i -> new TestdataSortableValue("Generated Value " + i, i))
            .toList();
    var solution = new TestdataStrengthFactorySortableEntityProvidingSolution();
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

  private List<TestdataStrengthFactorySortableEntityProvidingEntity> entityList;
  private HardSoftScore score;

  @PlanningEntityCollectionProperty
  public List<TestdataStrengthFactorySortableEntityProvidingEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataStrengthFactorySortableEntityProvidingEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }

  public void removeEntity(TestdataStrengthFactorySortableEntityProvidingEntity entity) {
    this.entityList = entityList.stream().filter(e -> e != entity).toList();
  }
}
