package ai.greycos.solver.core.testcotwin.shadow.invalid;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.testcotwin.TestdataObject;

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
}
