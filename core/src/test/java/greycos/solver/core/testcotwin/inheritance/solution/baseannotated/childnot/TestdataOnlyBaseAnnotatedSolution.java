package greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataOnlyBaseAnnotatedSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataOnlyBaseAnnotatedSolution>
      buildBaseSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataOnlyBaseAnnotatedSolution.class, TestdataOnlyBaseAnnotatedBaseEntity.class);
  }

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "valueRange")
  private List<TestdataValue> valueList;

  @PlanningEntityCollectionProperty private List<TestdataOnlyBaseAnnotatedBaseEntity> entityList;
  @PlanningScore private SimpleScore score;
  private ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides;

  public TestdataOnlyBaseAnnotatedSolution() {}

  public TestdataOnlyBaseAnnotatedSolution(String code) {
    super(code);
  }

  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }

  public List<? extends TestdataOnlyBaseAnnotatedBaseEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<? extends TestdataOnlyBaseAnnotatedBaseEntity> entityList) {
    this.entityList = (List<TestdataOnlyBaseAnnotatedBaseEntity>) entityList;
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
