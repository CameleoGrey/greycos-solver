package greycos.solver.core.testcotwin.valuerange.parameter.invalid;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataInvalidParameterSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataInvalidParameterSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataInvalidParameterSolution.class, TestdataInvalidParameterEntity.class);
  }

  private List<TestdataInvalidParameterEntity> entityList;
  private List<TestdataValue> valueList;

  private SimpleScore score;

  public TestdataInvalidParameterSolution() {
    // Required for cloning
  }

  public TestdataInvalidParameterSolution(String code) {
    super(code);
  }

  @PlanningEntityCollectionProperty
  public List<TestdataInvalidParameterEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataInvalidParameterEntity> entityList) {
    this.entityList = entityList;
  }

  @ValueRangeProvider(id = "valueRange")
  public List<TestdataValue> getValueList(TestdataInvalidParameterSolution solution) {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
