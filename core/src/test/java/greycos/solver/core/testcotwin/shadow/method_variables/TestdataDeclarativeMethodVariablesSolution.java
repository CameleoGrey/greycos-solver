package greycos.solver.core.testcotwin.shadow.method_variables;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataDeclarativeMethodVariablesSolution extends TestdataObject {
  @PlanningEntityCollectionProperty List<TestdataDeclarativeMethodVariablesEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider
  List<TestdataDeclarativeMethodVariablesBaseValue> values;

  @PlanningScore HardSoftScore score;

  public List<TestdataDeclarativeMethodVariablesEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataDeclarativeMethodVariablesEntity> entities) {
    this.entities = entities;
  }

  public List<TestdataDeclarativeMethodVariablesBaseValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataDeclarativeMethodVariablesBaseValue> values) {
    this.values = values;
  }

  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }
}
