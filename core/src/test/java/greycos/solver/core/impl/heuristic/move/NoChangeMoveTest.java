package greycos.solver.core.impl.heuristic.move;

import static greycos.solver.core.testutil.PlannerTestUtils.mockRebasingScoreDirector;
import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.testcotwin.TestdataSolution;

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
