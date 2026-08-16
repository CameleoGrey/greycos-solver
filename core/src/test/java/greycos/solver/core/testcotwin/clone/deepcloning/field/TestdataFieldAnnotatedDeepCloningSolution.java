package greycos.solver.core.testcotwin.clone.deepcloning.field;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.solution.cloner.DeepPlanningClone;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataFieldAnnotatedDeepCloningSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataFieldAnnotatedDeepCloningSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataFieldAnnotatedDeepCloningSolution.class,
        TestdataFieldAnnotatedDeepCloningEntity.class);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  private List<TestdataValue> valueList;

  @PlanningEntityCollectionProperty
  private List<TestdataFieldAnnotatedDeepCloningEntity> entityList;

  @DeepPlanningClone private List<String> generalShadowVariableList;

  @PlanningScore private SimpleScore score;

  public TestdataFieldAnnotatedDeepCloningSolution() {}

  public TestdataFieldAnnotatedDeepCloningSolution(String code) {
    super(code);
  }

  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }

  public List<TestdataFieldAnnotatedDeepCloningEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataFieldAnnotatedDeepCloningEntity> entityList) {
    this.entityList = entityList;
  }

  public List<String> getGeneralShadowVariableList() {
    return generalShadowVariableList;
  }

  public void setGeneralShadowVariableList(List<String> generalShadowVariableList) {
    this.generalShadowVariableList = generalShadowVariableList;
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
