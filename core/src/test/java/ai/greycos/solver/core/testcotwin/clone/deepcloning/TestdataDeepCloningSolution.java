package ai.greycos.solver.core.testcotwin.clone.deepcloning;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.cloner.DeepPlanningClone;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataDeepCloningSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataDeepCloningSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataDeepCloningSolution.class, TestdataDeepCloningEntity.class);
  }

  private List<TestdataValue> valueList;
  private List<TestdataDeepCloningEntity> entityList;
  private List<String> generalShadowVariableList;

  private SimpleScore score;

  public TestdataDeepCloningSolution() {}

  public TestdataDeepCloningSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataDeepCloningEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataDeepCloningEntity> entityList) {
    this.entityList = entityList;
  }

  @DeepPlanningClone
  public List<String> getGeneralShadowVariableList() {
    return generalShadowVariableList;
  }

  public void setGeneralShadowVariableList(List<String> generalShadowVariableList) {
    this.generalShadowVariableList = generalShadowVariableList;
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
