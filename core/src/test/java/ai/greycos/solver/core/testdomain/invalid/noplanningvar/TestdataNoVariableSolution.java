package ai.greycos.solver.core.testdomain.invalid.noplanningvar;

import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;

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
