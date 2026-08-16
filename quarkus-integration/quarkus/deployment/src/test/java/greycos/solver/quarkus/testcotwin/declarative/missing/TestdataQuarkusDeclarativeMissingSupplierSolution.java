package greycos.solver.quarkus.testcotwin.declarative.missing;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.HardSoftScore;

@PlanningSolution
public class TestdataQuarkusDeclarativeMissingSupplierSolution {

  @PlanningEntityCollectionProperty List<TestdataQuarkusDeclarativeMissingSupplierEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider
  List<TestdataQuarkusDeclarativeMissingSupplierValue> values;

  @PlanningScore HardSoftScore score;

  public TestdataQuarkusDeclarativeMissingSupplierSolution() {}

  public TestdataQuarkusDeclarativeMissingSupplierSolution(
      List<TestdataQuarkusDeclarativeMissingSupplierEntity> entities,
      List<TestdataQuarkusDeclarativeMissingSupplierValue> values) {
    this.values = values;
    this.entities = entities;
  }

  public List<TestdataQuarkusDeclarativeMissingSupplierValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataQuarkusDeclarativeMissingSupplierValue> values) {
    this.values = values;
  }

  public List<TestdataQuarkusDeclarativeMissingSupplierEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataQuarkusDeclarativeMissingSupplierEntity> entities) {
    this.entities = entities;
  }

  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }

  @Override
  public String toString() {
    return "TestdataDeclarativeMissingSupplierSolution{"
        + "entities="
        + entities
        + ", values="
        + values
        + ", score="
        + score
        + '}';
  }
}
