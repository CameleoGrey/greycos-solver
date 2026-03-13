package ai.greycos.solver.core.testcotwin.record;

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

@PlanningSolution
public class TestdataRecordSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataRecordSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataRecordSolution.class, TestdataRecordEntity.class);
  }

  public static TestdataRecordSolution generateSolution() {
    return generateSolution(5, 7);
  }

  public static TestdataRecordSolution generateSolution(int valueListSize, int entityListSize) {
    TestdataRecordSolution solution = new TestdataRecordSolution("Generated Solution 0");
    List<TestdataRecordValue> valueList = new ArrayList<>(valueListSize);
    for (int i = 0; i < valueListSize; i++) {
      TestdataRecordValue value = new TestdataRecordValue("Generated Value " + i);
      valueList.add(value);
    }
    solution.setValueList(valueList);
    List<TestdataRecordEntity> entityList = new ArrayList<>(entityListSize);
    for (int i = 0; i < entityListSize; i++) {
      TestdataRecordValue value = valueList.get(i % valueListSize);
      TestdataRecordEntity entity = new TestdataRecordEntity("Generated Entity " + i, value);
      entityList.add(entity);
    }
    solution.setEntityList(entityList);
    return solution;
  }

  private List<TestdataRecordValue> valueList;
  private List<TestdataRecordEntity> entityList;

  private SimpleScore score;

  public TestdataRecordSolution() {}

  public TestdataRecordSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataRecordValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataRecordValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataRecordEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataRecordEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
