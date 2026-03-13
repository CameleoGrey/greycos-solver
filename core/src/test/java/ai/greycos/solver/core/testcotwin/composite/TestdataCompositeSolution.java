package ai.greycos.solver.core.testcotwin.composite;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataCompositeSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataCompositeSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataCompositeSolution.class, TestdataCompositeEntity.class);
  }

  public static TestdataCompositeSolution generateSolution(int valueListSize, int entityListSize) {
    TestdataCompositeSolution solution = new TestdataCompositeSolution("Generated Solution 0");
    List<TestdataValue> valueList = new ArrayList<>(valueListSize);
    for (int i = 0; i < valueListSize; i++) {
      TestdataValue value = new TestdataValue("Generated Value " + i);
      valueList.add(value);
    }
    List<TestdataValue> otherValueList = new ArrayList<>(valueListSize);
    for (int i = 0; i < valueListSize; i++) {
      TestdataValue value = new TestdataValue("Generated Value " + (valueListSize + i));
      otherValueList.add(value);
    }
    solution.setValueList(valueList);
    solution.setOtherValueList(otherValueList);
    List<TestdataCompositeEntity> entityList = new ArrayList<>(entityListSize);
    for (int i = 0; i < entityListSize; i++) {
      TestdataCompositeEntity entity = new TestdataCompositeEntity("Generated Entity " + i);
      entityList.add(entity);
    }
    solution.setEntityList(entityList);
    return solution;
  }

  private List<TestdataValue> valueList;
  private List<TestdataValue> otherValueList;
  private List<TestdataCompositeEntity> entityList;

  private SimpleScore score;

  public TestdataCompositeSolution() {
    // Required for cloning
  }

  public TestdataCompositeSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange1")
  @ProblemFactCollectionProperty
  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }

  @ValueRangeProvider(id = "valueRange2")
  @ProblemFactCollectionProperty
  public List<TestdataValue> getOtherValueList() {
    return otherValueList;
  }

  public void setOtherValueList(List<TestdataValue> otherValueList) {
    this.otherValueList = otherValueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataCompositeEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataCompositeEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
