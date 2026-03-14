package ai.greycos.solver.core.testutil;

import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

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
