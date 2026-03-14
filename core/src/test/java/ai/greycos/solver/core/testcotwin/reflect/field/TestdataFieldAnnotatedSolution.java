package ai.greycos.solver.core.testcotwin.reflect.field;

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
public class TestdataFieldAnnotatedSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataFieldAnnotatedSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataFieldAnnotatedSolution.class, TestdataFieldAnnotatedEntity.class);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  private List<TestdataValue> valueList;

  @PlanningEntityCollectionProperty private List<TestdataFieldAnnotatedEntity> entityList;

  @PlanningScore private SimpleScore score;

  public TestdataFieldAnnotatedSolution() {}

  public TestdataFieldAnnotatedSolution(String code) {
    super(code);
  }

  public TestdataFieldAnnotatedSolution(
      String code, List<TestdataValue> valueList, List<TestdataFieldAnnotatedEntity> entityList) {
    super(code);
    this.valueList = valueList;
    this.entityList = entityList;
  }

  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }

  public List<TestdataFieldAnnotatedEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataFieldAnnotatedEntity> entityList) {
    this.entityList = entityList;
  }

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
