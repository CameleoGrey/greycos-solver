package greycos.solver.core.testcotwin.invalid.nosolution;

import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;

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
