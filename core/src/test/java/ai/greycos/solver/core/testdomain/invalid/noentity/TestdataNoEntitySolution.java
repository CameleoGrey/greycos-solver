package ai.greycos.solver.core.testdomain.invalid.noentity;

import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;

@PlanningSolution
public class TestdataNoEntitySolution extends TestdataObject {

  public static SolutionDescriptor<TestdataNoEntitySolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(TestdataNoEntitySolution.class);
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
