package ai.greycos.solver.spring.boot.it.cotwin;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.SimpleScore;

@PlanningSolution
public class IntegrationTestSolution {
  @PlanningEntityCollectionProperty private List<IntegrationTestEntity> entityList;

  private List<IntegrationTestValue> valueList;

  @PlanningScore private SimpleScore score;

  public IntegrationTestSolution() {}

  public IntegrationTestSolution(
      List<IntegrationTestEntity> entityList, List<IntegrationTestValue> valueList) {
    this.entityList = entityList;
    this.valueList = valueList;
  }

  public List<IntegrationTestEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<IntegrationTestEntity> entityList) {
    this.entityList = entityList;
  }

  public List<IntegrationTestValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<IntegrationTestValue> valueList) {
    this.valueList = valueList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
