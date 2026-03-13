package ai.greycos.solver.quarkus.it.devui.cotwin;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.HardSoftScore;

@PlanningSolution
public class TestdataStringLengthShadowSolution {

  @ValueRangeProvider(id = "valueRange")
  private List<String> valueList;

  @PlanningEntityCollectionProperty private List<TestdataStringLengthShadowEntity> entityList;

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

  public List<TestdataStringLengthShadowEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataStringLengthShadowEntity> entityList) {
    this.entityList = entityList;
  }

  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }
}
