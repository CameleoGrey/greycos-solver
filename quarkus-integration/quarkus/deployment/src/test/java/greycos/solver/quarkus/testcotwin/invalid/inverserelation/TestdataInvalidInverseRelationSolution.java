package greycos.solver.quarkus.testcotwin.invalid.inverserelation;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;

@PlanningSolution
public class TestdataInvalidInverseRelationSolution {

  private List<TestdataInvalidInverseRelationValue> valueList;
  private List<TestdataInvalidInverseRelationEntity> entityList;

  private SimpleScore score;

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataInvalidInverseRelationValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataInvalidInverseRelationValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataInvalidInverseRelationEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataInvalidInverseRelationEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
