package greycos.solver.core.impl.cotwin.score.descriptor;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.definition.ScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class ScoreDescriptorTest {

  @Test
  void scoreDefinition() {
    SolutionDescriptor<TestdataSolution> solutionDescriptor =
        TestdataSolution.buildSolutionDescriptor();
    ScoreDefinition<?> scoreDefinition = solutionDescriptor.getScoreDefinition();
    assertThat(scoreDefinition).isInstanceOf(SimpleScoreDefinition.class);
    assertThat(scoreDefinition.getScoreClass()).isEqualTo(SimpleScore.class);
  }

  @Test
  void scoreAccess() {
    SolutionDescriptor<TestdataSolution> solutionDescriptor =
        TestdataSolution.buildSolutionDescriptor();
    TestdataSolution solution = new TestdataSolution();

    assertThat((SimpleScore) solutionDescriptor.getScore(solution)).isNull();

    SimpleScore score = SimpleScore.of(-2);
    solutionDescriptor.setScore(solution, score);
    assertThat((SimpleScore) solutionDescriptor.getScore(solution)).isSameAs(score);
  }
}
