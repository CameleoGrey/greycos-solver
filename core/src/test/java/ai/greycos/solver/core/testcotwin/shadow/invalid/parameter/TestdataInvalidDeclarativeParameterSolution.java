package ai.greycos.solver.core.testcotwin.shadow.invalid.parameter;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataInvalidDeclarativeParameterSolution extends TestdataObject {
  @PlanningEntityCollectionProperty List<TestdataInvalidDeclarativeParameterEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider
  List<TestdataInvalidDeclarativeParameterValue> values;

  @PlanningScore SimpleScore score;

  public TestdataInvalidDeclarativeParameterSolution() {}

  public TestdataInvalidDeclarativeParameterSolution(String code) {
    super(code);
  }

  public List<TestdataInvalidDeclarativeParameterEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataInvalidDeclarativeParameterEntity> entities) {
    this.entities = entities;
  }

  public List<TestdataInvalidDeclarativeParameterValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataInvalidDeclarativeParameterValue> values) {
    this.values = values;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
