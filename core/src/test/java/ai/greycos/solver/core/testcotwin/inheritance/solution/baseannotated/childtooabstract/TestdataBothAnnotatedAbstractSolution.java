package ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childtooabstract;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;

@PlanningSolution
public abstract class TestdataBothAnnotatedAbstractSolution {

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "valueRange")
  private List<String> valueList;

  @PlanningEntityCollectionProperty
  private List<? extends TestdataBothAnnotatedAbstractBaseEntity> entityList;

  @PlanningScore private SimpleScore score;
  private ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides;

  public List<String> getValueList() {
    return valueList;
  }

  public void setValueList(List<String> valueList) {
    this.valueList = valueList;
  }

  public List<? extends TestdataBothAnnotatedAbstractBaseEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<? extends TestdataBothAnnotatedAbstractBaseEntity> entityList) {
    this.entityList = entityList;
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
