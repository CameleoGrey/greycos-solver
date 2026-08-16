package greycos.solver.core.testcotwin.shadow.invalid;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataInvalidDeclarativeSolution extends TestdataObject {
  @PlanningEntityCollectionProperty List<TestdataInvalidDeclarativeEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider
  List<TestdataInvalidDeclarativeValue> values;

  @PlanningScore SimpleScore score;

  public TestdataInvalidDeclarativeSolution() {}

  public TestdataInvalidDeclarativeSolution(String code) {
    super(code);
  }

  public List<TestdataInvalidDeclarativeEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataInvalidDeclarativeEntity> entities) {
    this.entities = entities;
  }

  public List<TestdataInvalidDeclarativeValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataInvalidDeclarativeValue> values) {
    this.values = values;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
