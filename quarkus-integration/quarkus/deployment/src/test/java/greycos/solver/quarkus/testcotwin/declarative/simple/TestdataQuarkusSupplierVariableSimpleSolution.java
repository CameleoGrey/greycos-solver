package greycos.solver.quarkus.testcotwin.declarative.simple;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;

@PlanningSolution
public class TestdataQuarkusSupplierVariableSimpleSolution {

  private List<String> valueList;
  private List<TestdataQuarkusSupplierVariableSimpleEntity> entityList;

  private SimpleScore score;

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<String> getValueList() {
    return valueList;
  }

  public void setValueList(List<String> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataQuarkusSupplierVariableSimpleEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataQuarkusSupplierVariableSimpleEntity> entityList) {
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
