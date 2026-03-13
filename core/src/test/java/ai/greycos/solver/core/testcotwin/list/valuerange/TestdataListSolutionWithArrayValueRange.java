package ai.greycos.solver.core.testcotwin.list.valuerange;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataListSolutionWithArrayValueRange {

  public static SolutionDescriptor<TestdataListSolutionWithArrayValueRange>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataListSolutionWithArrayValueRange.class, TestdataListEntityWithArrayValueRange.class);
  }

  private TestdataValue[] valueArray;
  private TestdataListEntityWithArrayValueRange entity;
  private SimpleScore score;

  @ValueRangeProvider(id = "arrayValueRange")
  @ProblemFactCollectionProperty
  public TestdataValue[] getValueArray() {
    return valueArray;
  }

  public void setValueArray(TestdataValue[] valueArray) {
    this.valueArray = valueArray;
  }

  @PlanningEntityProperty
  public TestdataListEntityWithArrayValueRange getEntity() {
    return entity;
  }

  public void setEntity(TestdataListEntityWithArrayValueRange entity) {
    this.entity = entity;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
