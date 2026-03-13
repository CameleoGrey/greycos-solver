package ai.greycos.solver.core.testcotwin.mixed.singleentity;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataMixedSolution {

  public static SolutionDescriptor<TestdataMixedSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataMixedSolution.class,
        TestdataMixedEntity.class,
        TestdataMixedValue.class,
        TestdataMixedOtherValue.class);
  }

  public static TestdataMixedSolution generateUninitializedSolution(
      int entityListSize, int valueListSize, int otherValueListSize) {
    var solution = new TestdataMixedSolution();
    var valueList = new ArrayList<TestdataMixedValue>(valueListSize);
    var otherValueList = new ArrayList<TestdataMixedOtherValue>(otherValueListSize);
    for (int i = 0; i < valueListSize; i++) {
      valueList.add(new TestdataMixedValue("Generated Value " + i));
    }
    for (int i = 0; i < otherValueListSize; i++) {
      otherValueList.add(
          new TestdataMixedOtherValue("Generated Other Value " + i, valueListSize - i));
    }
    solution.setValueList(valueList);
    solution.setOtherValueList(otherValueList);
    var entityList = new ArrayList<TestdataMixedEntity>(entityListSize);
    for (int i = 0; i < entityListSize; i++) {
      var entity = new TestdataMixedEntity("Entity " + i, i);
      entityList.add(entity);
    }
    solution.setEntityList(entityList);
    return solution;
  }

  @ValueRangeProvider(id = "valueRange")
  @PlanningEntityCollectionProperty
  private List<TestdataMixedValue> valueList;

  @ValueRangeProvider(id = "otherValueRange")
  @PlanningEntityCollectionProperty
  private List<TestdataMixedOtherValue> otherValueList;

  @PlanningEntityCollectionProperty private List<TestdataMixedEntity> entityList;
  @PlanningScore private SimpleScore score;

  public List<TestdataMixedValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataMixedValue> valueList) {
    this.valueList = valueList;
  }

  public List<TestdataMixedOtherValue> getOtherValueList() {
    return otherValueList;
  }

  public void setOtherValueList(List<TestdataMixedOtherValue> otherValueList) {
    this.otherValueList = otherValueList;
  }

  public List<TestdataMixedEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataMixedEntity> entityList) {
    this.entityList = entityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
