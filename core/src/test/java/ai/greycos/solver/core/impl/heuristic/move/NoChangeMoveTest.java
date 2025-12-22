package ai.greycos.solver.core.impl.heuristic.move;

import static ai.greycos.solver.core.testutil.PlannerTestUtils.mockRebasingScoreDirector;
import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.testdomain.TestdataSolution;

import org.junit.jupiter.api.Test;

class NoChangeMoveTest {

  @Test
  void isMoveDoable() {
    assertThat(NoChangeMove.getInstance().isMoveDoable(null)).isFalse();
  }

  @Test
  void rebase() {
    var destinationScoreDirector =
        mockRebasingScoreDirector(TestdataSolution.buildSolutionDescriptor(), new Object[][] {});
    var move = NoChangeMove.<TestdataSolution>getInstance();
    var rebasedMove = move.rebase(destinationScoreDirector);
    assertThat(rebasedMove).isSameAs(move);
  }
}
