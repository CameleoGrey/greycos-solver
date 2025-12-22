package ai.greycos.solver.core.testdomain.invalid.nosolution;

import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;

public class TestdataNoSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataNoSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(TestdataNoSolution.class);
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
