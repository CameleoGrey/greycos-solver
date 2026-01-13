package ai.greycos.solver.quarkus.it.cotwin;

import java.util.List;
import java.util.Map;

import ai.greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;

@PlanningSolution
public class TestdataStringLengthShadowSolution {

  @PlanningEntityCollectionProperty
  private List<TestdataStringLengthShadowEntityInterface> entityList;

  private List<String> valueList;

  ConstraintWeightOverrides<HardSoftScore> constraintWeightOverrides =
      ConstraintWeightOverrides.of(
          Map.of("Don't assign 2 entities the same value.", HardSoftScore.ofHard(1)));

  @PlanningScore private HardSoftScore score;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public List<String> getValueList() {
    return valueList;
  }

  public void setValueList(List<String> valueList) {
    this.valueList = valueList;
  }

  public List<TestdataStringLengthShadowEntityInterface> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataStringLengthShadowEntityInterface> entityList) {
    this.entityList = entityList;
  }

  public ConstraintWeightOverrides<HardSoftScore> getConstraintWeightOverrides() {
    return constraintWeightOverrides;
  }

  public void setConstraintWeightOverrides(
      ConstraintWeightOverrides<HardSoftScore> constraintWeightOverrides) {
    this.constraintWeightOverrides = constraintWeightOverrides;
  }

  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }
}
