package ai.greycos.solver.spring.boot.autoconfigure.declarative.domain;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;

@PlanningSolution
public class TestdataSpringSupplierVariableSolution {

  private List<String> valueList;
  private List<TestdataSpringSupplierVariableEntity> entityList;

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
  public List<TestdataSpringSupplierVariableEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataSpringSupplierVariableEntity> entityList) {
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
