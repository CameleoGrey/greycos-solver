package ai.greycos.solver.core.testdomain.clone.deepcloning.field;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.cloner.DeepPlanningClone;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

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
