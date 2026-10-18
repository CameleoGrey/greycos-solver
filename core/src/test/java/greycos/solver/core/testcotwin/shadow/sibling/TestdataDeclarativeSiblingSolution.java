package greycos.solver.core.testcotwin.shadow.sibling;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataDeclarativeSiblingSolution extends TestdataObject {
  @PlanningEntityCollectionProperty List<TestdataDeclarativeSiblingEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider
  List<AbstractTestdataDeclarativeSiblingValue> values;

  @PlanningScore SimpleScore score;

  public List<TestdataDeclarativeSiblingEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataDeclarativeSiblingEntity> entities) {
    this.entities = entities;
  }

  public List<AbstractTestdataDeclarativeSiblingValue> getValues() {
    return values;
  }

  public void setValues(List<AbstractTestdataDeclarativeSiblingValue> values) {
    this.values = values;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
