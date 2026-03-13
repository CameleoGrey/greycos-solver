package ai.greycos.solver.core.testcotwin.invalid.noplanningvar;

import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataNoVariableSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataNoVariableSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataNoVariableSolution.class, TestdataNoVariableEntity.class);
  }

  private SimpleScore score;

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
