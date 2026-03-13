package ai.greycos.solver.core.testcotwin.shadow.method_variables;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.testcotwin.TestdataObject;

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
