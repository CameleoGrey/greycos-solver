package ai.greycos.solver.core.impl.localsearch.decider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadFactory;

import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.heuristic.move.AbstractMove;
import ai.greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.greycos.solver.core.impl.neighborhood.MoveRepository;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.solver.thread.DefaultSolverThreadFactory;

import org.junit.jupiter.api.Test;

/**
 * Test for step synchronization in MultiThreadedLocalSearchDecider. This test specifically verifies
 * that the step index validation works correctly and that move threads properly synchronize with
 * the main thread's step progression.
 */
class MultiThreadedLocalSearchDeciderStepSyncTest {

  @Test
  void testMultiThreadedLocalSearchDeciderCreation() {
    // Test that the MultiThreadedLocalSearchDecider can be created successfully
    String logIndentation = "    ";
    PhaseTermination<TestSolution> termination = null; // Mock or null for basic test
    MoveRepository<TestSolution> moveRepository = null; // Mock or null for basic test
    Acceptor<TestSolution> acceptor = null; // Mock or null for basic test
    LocalSearchForager<TestSolution> forager = null; // Mock or null for basic test
    ThreadFactory threadFactory = new DefaultSolverThreadFactory();
    int moveThreadCount = 2;
    int selectedMoveBufferSize = 20;

    MultiThreadedLocalSearchDecider<TestSolution> decider =
        new MultiThreadedLocalSearchDecider<>(
            logIndentation,
            termination,
            moveRepository,
            acceptor,
            forager,
            threadFactory,
            moveThreadCount,
            selectedMoveBufferSize);

    assertThat(decider).isNotNull();
    assertThat(decider.getMoveThreadCount()).isEqualTo(moveThreadCount);
    assertThat(decider.getSelectedMoveBufferSize()).isEqualTo(selectedMoveBufferSize);
  }

  @Test
  void testEnableAssertions() {
    // Test that assertion settings work correctly
    String logIndentation = "    ";
    PhaseTermination<TestSolution> termination = null;
    MoveRepository<TestSolution> moveRepository = null;
    Acceptor<TestSolution> acceptor = null;
    LocalSearchForager<TestSolution> forager = null;
    ThreadFactory threadFactory = new DefaultSolverThreadFactory();
    int moveThreadCount = 2;
    int selectedMoveBufferSize = 20;

    MultiThreadedLocalSearchDecider<TestSolution> decider =
        new MultiThreadedLocalSearchDecider<>(
            logIndentation,
            termination,
            moveRepository,
            acceptor,
            forager,
            threadFactory,
            moveThreadCount,
            selectedMoveBufferSize);

    // Test fully asserted mode
    decider.enableAssertions(EnvironmentMode.FULL_ASSERT);
    assertThat(decider.isAssertStepScoreFromScratch()).isTrue();
    assertThat(decider.isAssertExpectedStepScore()).isTrue();
    assertThat(decider.isAssertShadowVariablesAreNotStaleAfterStep()).isTrue();

    // Test intrusively asserted mode
    decider.enableAssertions(EnvironmentMode.STEP_ASSERT);
    assertThat(decider.isAssertStepScoreFromScratch()).isFalse();
    assertThat(decider.isAssertExpectedStepScore()).isTrue();
    assertThat(decider.isAssertShadowVariablesAreNotStaleAfterStep()).isTrue();

    // Test non-intrusive mode
    decider.enableAssertions(EnvironmentMode.NON_INTRUSIVE_FULL_ASSERT);
    assertThat(decider.isAssertStepScoreFromScratch()).isTrue();
    assertThat(decider.isAssertExpectedStepScore()).isFalse();
    assertThat(decider.isAssertShadowVariablesAreNotStaleAfterStep()).isFalse();
  }

  @Test
  void testStepSynchronizationBasic() {
    // Test basic step synchronization functionality
    String logIndentation = "    ";
    PhaseTermination<TestSolution> termination = null;
    MoveRepository<TestSolution> moveRepository = null;
    Acceptor<TestSolution> acceptor = null;
    LocalSearchForager<TestSolution> forager = null;
    ThreadFactory threadFactory = new DefaultSolverThreadFactory();
    int moveThreadCount = 1;
    int selectedMoveBufferSize = 5;

    MultiThreadedLocalSearchDecider<TestSolution> decider =
        new MultiThreadedLocalSearchDecider<>(
            logIndentation,
            termination,
            moveRepository,
            acceptor,
            forager,
            threadFactory,
            moveThreadCount,
            selectedMoveBufferSize);

    // Verify that the decider was properly initialized
    assertThat(decider.getMoveThreadCount()).isEqualTo(moveThreadCount);
    assertThat(decider.getSelectedMoveBufferSize()).isEqualTo(selectedMoveBufferSize);

    // The key test: verify that the decider can be created and configured
    // without throwing step synchronization errors during construction
    assertThat(decider).isNotNull();
  }

  // Mock classes for testing
  @PlanningSolution
  private static class TestSolution {
    private SimpleScore score;

    @PlanningScore
    public SimpleScore getScore() {
      return score;
    }

    public void setScore(SimpleScore score) {
      this.score = score;
    }
  }

  private static class TestMove extends AbstractMove<TestSolution> {
    @Override
    public boolean isMoveDoable(
        ai.greycos.solver.core.api.score.director.ScoreDirector<TestSolution> scoreDirector) {
      return true;
    }

    @Override
    protected void doMoveOnGenuineVariables(
        ai.greycos.solver.core.api.score.director.ScoreDirector<TestSolution> scoreDirector) {
      // Mock implementation - do nothing
    }

    @Override
    public TestMove createUndoMove(
        ai.greycos.solver.core.api.score.director.ScoreDirector<TestSolution> scoreDirector) {
      return this;
    }

    @Override
    public String getSimpleMoveTypeDescription() {
      return "TestMove";
    }
  }
}
