package ai.greycos.solver.spring.boot.autoconfigure.gizmo.cotwin;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;

@PlanningSolution
public class TestdataGizmoSpringSolution {

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "valueRange")
  public List<String> valueList;

  @PlanningEntityCollectionProperty public List<TestdataGizmoSpringEntity> entityList;

  @PlanningScore public SimpleScore score;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public List<String> getValueList() {
    return valueList;
  }

  public void setValueList(List<String> valueList) {
    this.valueList = valueList;
  }

  public List<TestdataGizmoSpringEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataGizmoSpringEntity> entityList) {
    this.entityList = entityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
