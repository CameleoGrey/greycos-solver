package ai.greycos.solver.quarkus.jackson.it.cotwin;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;

@PlanningSolution
public class ITestdataPlanningSolution {

  @ValueRangeProvider(id = "valueRange")
  private List<String> valueList;

  @PlanningEntityCollectionProperty private List<ITestdataPlanningEntity> entityList;

  @PlanningScore private SimpleScore score;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public List<String> getValueList() {
    return valueList;
  }

  public void setValueList(List<String> valueList) {
    this.valueList = valueList;
  }

  public List<ITestdataPlanningEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<ITestdataPlanningEntity> entityList) {
    this.entityList = entityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
