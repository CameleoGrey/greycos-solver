package ai.greycos.solver.core.testcotwin.collection;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataArrayBasedSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataArrayBasedSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataArrayBasedSolution.class, TestdataArrayBasedEntity.class);
  }

  private TestdataValue[] values;
  private TestdataArrayBasedEntity[] entities;

  private SimpleScore score;

  public TestdataArrayBasedSolution() {}

  public TestdataArrayBasedSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public TestdataValue[] getValues() {
    return values;
  }

  public void setValues(TestdataValue[] values) {
    this.values = values;
  }

  @PlanningEntityCollectionProperty
  public TestdataArrayBasedEntity[] getEntities() {
    return entities;
  }

  public void setEntities(TestdataArrayBasedEntity[] entities) {
    this.entities = entities;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
