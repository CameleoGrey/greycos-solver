package greycos.solver.core.testutil;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class PlannerTestUtilsTest {

  @Test
  void mockRebasingScoreDirectorExposesAWorkingMoveDirector() {
    var external = new TestdataEntity("external");
    var working = new TestdataEntity("working");

    InnerScoreDirector<TestdataSolution, SimpleScore> scoreDirector =
        PlannerTestUtils.mockRebasingScoreDirector(
            TestdataSolution.buildSolutionDescriptor(), new Object[][] {{external, working}});

    assertThat(scoreDirector.lookUpWorkingObject(external)).isSameAs(working);
    assertThat(scoreDirector.getMoveDirector()).isNotNull();
    assertThat(scoreDirector.getMoveDirector().lookUpWorkingObject(external)).isSameAs(working);
    assertThat(scoreDirector.getMoveDirector().getScoreDirector().lookUpWorkingObject(external))
        .isSameAs(working);
  }
}
