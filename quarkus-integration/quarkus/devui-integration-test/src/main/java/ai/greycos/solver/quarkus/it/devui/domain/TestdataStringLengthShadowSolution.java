package ai.greycos.solver.quarkus.it.devui.domain;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;

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
