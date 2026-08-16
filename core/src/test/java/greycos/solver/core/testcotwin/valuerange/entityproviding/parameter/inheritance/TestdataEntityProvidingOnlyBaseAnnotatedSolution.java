package greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.inheritance;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

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
