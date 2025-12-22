package ai.greycos.solver.core.testdomain.inheritance.solution.baseannotated.childnot;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

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
