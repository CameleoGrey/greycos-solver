package ai.greycos.solver.core.testdomain.valuerange.entityproviding.parameter.inheritance;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningSolution
public class TestdataEntityProvidingOnlyBaseAnnotatedSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataEntityProvidingOnlyBaseAnnotatedSolution>
      buildBaseSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataEntityProvidingOnlyBaseAnnotatedSolution.class,
        TestdataEntityProvidingOnlyBaseAnnotatedBaseEntity.class);
  }

  private List<TestdataValue> valueList;

  @PlanningEntityCollectionProperty
  private List<TestdataEntityProvidingOnlyBaseAnnotatedBaseEntity> entityList;

  @PlanningScore private SimpleScore score;
  private ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides;

  public TestdataEntityProvidingOnlyBaseAnnotatedSolution() {}

  public TestdataEntityProvidingOnlyBaseAnnotatedSolution(String code) {
    super(code);
  }

  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }

  public List<? extends TestdataEntityProvidingOnlyBaseAnnotatedBaseEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(
      List<? extends TestdataEntityProvidingOnlyBaseAnnotatedBaseEntity> entityList) {
    this.entityList = (List<TestdataEntityProvidingOnlyBaseAnnotatedBaseEntity>) entityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }

  public ConstraintWeightOverrides<SimpleScore> getConstraintWeightOverrides() {
    return constraintWeightOverrides;
  }

  public void setConstraintWeightOverrides(
      ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides) {
    this.constraintWeightOverrides = constraintWeightOverrides;
  }
}
