package greycos.solver.core.testcotwin.unassignedvar.sort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningSolution
public class TestdataAllowsUnassignedSortableSolution {

  public static SolutionDescriptor<TestdataAllowsUnassignedSortableSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataAllowsUnassignedSortableSolution.class,
        TestdataAllowsUnassignedSortableEntity.class,
        TestdataSortableValue.class);
  }

  public static TestdataAllowsUnassignedSortableSolution generateSolution(
      int valueCount, int entityCount, boolean shuffle) {
    var entityList =
        new ArrayList<>(
            IntStream.range(0, entityCount)
                .mapToObj(i -> new TestdataAllowsUnassignedSortableEntity("Generated Entity " + i))
                .toList());
    var valueList =
        new ArrayList<>(
            IntStream.range(0, valueCount)
                .mapToObj(i -> new TestdataSortableValue("Generated Value " + i, i))
                .toList());
    if (shuffle) {
      var random = new Random(0);
      Collections.shuffle(entityList, random);
      Collections.shuffle(valueList, random);
    }
    TestdataAllowsUnassignedSortableSolution solution =
        new TestdataAllowsUnassignedSortableSolution();
    solution.setValueList(valueList);
    solution.setEntityList(entityList);
    return solution;
  }

  private List<TestdataSortableValue> valueList;
  private List<TestdataAllowsUnassignedSortableEntity> entityList;
  private HardSoftScore score;

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataSortableValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataSortableValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataAllowsUnassignedSortableEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataAllowsUnassignedSortableEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }

  public void removeEntity(TestdataAllowsUnassignedSortableEntity entity) {
    this.entityList = entityList.stream().filter(e -> e != entity).toList();
  }
}
