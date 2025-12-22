package ai.greycos.solver.core.testdomain.shadow.invalid;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.testdomain.TestdataObject;

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
