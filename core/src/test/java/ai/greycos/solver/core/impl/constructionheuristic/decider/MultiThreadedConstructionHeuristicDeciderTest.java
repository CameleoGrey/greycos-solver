package ai.greycos.solver.core.impl.constructionheuristic.decider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadFactory;

import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.solver.thread.DefaultSolverThreadFactory;

import org.junit.jupiter.api.Test;

/**
 * Test for MultiThreadedConstructionHeuristicDecider functionality. Tests the construction
 * heuristic specific multithreading behavior and integration.
 */
class MultiThreadedConstructionHeuristicDeciderTest {

  @Test
  void testMultiThreadedConstructionHeuristicDeciderCreation() {
    // Test that the MultiThreadedConstructionHeuristicDecider can be created successfully
    String logIndentation = "    ";
    PhaseTermination<TestSolution> termination = null; // Mock or null for basic test
    ConstructionHeuristicForager<TestSolution> forager = null; // Mock or null for basic test
    ThreadFactory threadFactory = new DefaultSolverThreadFactory();
    int moveThreadCount = 2;
    int selectedMoveBufferSize = 20;

    MultiThreadedConstructionHeuristicDecider<TestSolution> decider =
        new MultiThreadedConstructionHeuristicDecider<>(
            logIndentation,
            termination,
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
    // Test that assertion settings work correctly for construction heuristic
    String logIndentation = "    ";
    PhaseTermination<TestSolution> termination = null;
    ConstructionHeuristicForager<TestSolution> forager = null;
    ThreadFactory threadFactory = new DefaultSolverThreadFactory();
    int moveThreadCount = 2;
    int selectedMoveBufferSize = 20;

    MultiThreadedConstructionHeuristicDecider<TestSolution> decider =
        new MultiThreadedConstructionHeuristicDecider<>(
            logIndentation,
            termination,
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
  void testConstructionHeuristicSpecificConfiguration() {
    // Test construction heuristic specific configuration
    String logIndentation = "    ";
    PhaseTermination<TestSolution> termination = null;
    ConstructionHeuristicForager<TestSolution> forager = null;
    ThreadFactory threadFactory = new DefaultSolverThreadFactory();
    int moveThreadCount = 4;
    int selectedMoveBufferSize = 50;

    MultiThreadedConstructionHeuristicDecider<TestSolution> decider =
        new MultiThreadedConstructionHeuristicDecider<>(
            logIndentation,
            termination,
            forager,
            threadFactory,
            moveThreadCount,
            selectedMoveBufferSize);

    assertThat(decider.getMoveThreadCount()).isEqualTo(moveThreadCount);
    assertThat(decider.getSelectedMoveBufferSize()).isEqualTo(selectedMoveBufferSize);
    assertThat(decider).isNotNull();
  }

  @Test
  void testConstructionHeuristicThreadCountValidation() {
    // Test that construction heuristic handles different thread counts correctly
    String logIndentation = "    ";
    PhaseTermination<TestSolution> termination = null;
    ConstructionHeuristicForager<TestSolution> forager = null;
    ThreadFactory threadFactory = new DefaultSolverThreadFactory();

    // Test with 1 thread
    MultiThreadedConstructionHeuristicDecider<TestSolution> decider1 =
        new MultiThreadedConstructionHeuristicDecider<>(
            logIndentation, termination, forager, threadFactory, 1, 10);
    assertThat(decider1.getMoveThreadCount()).isEqualTo(1);

    // Test with 4 threads
    MultiThreadedConstructionHeuristicDecider<TestSolution> decider4 =
        new MultiThreadedConstructionHeuristicDecider<>(
            logIndentation, termination, forager, threadFactory, 4, 40);
    assertThat(decider4.getMoveThreadCount()).isEqualTo(4);

    // Test with larger buffer
    MultiThreadedConstructionHeuristicDecider<TestSolution> deciderLargeBuffer =
        new MultiThreadedConstructionHeuristicDecider<>(
            logIndentation, termination, forager, threadFactory, 2, 100);
    assertThat(deciderLargeBuffer.getSelectedMoveBufferSize()).isEqualTo(100);
  }

  @Test
  void testConstructionHeuristicAssertionInheritance() {
    // Test that construction heuristic inherits assertion settings correctly
    String logIndentation = "    ";
    PhaseTermination<TestSolution> termination = null;
    ConstructionHeuristicForager<TestSolution> forager = null;
    ThreadFactory threadFactory = new DefaultSolverThreadFactory();
    int moveThreadCount = 2;
    int selectedMoveBufferSize = 20;

    MultiThreadedConstructionHeuristicDecider<TestSolution> decider =
        new MultiThreadedConstructionHeuristicDecider<>(
            logIndentation,
            termination,
            forager,
            threadFactory,
            moveThreadCount,
            selectedMoveBufferSize);

    // Initially all assertions should be false
    assertThat(decider.isAssertStepScoreFromScratch()).isFalse();
    assertThat(decider.isAssertExpectedStepScore()).isFalse();
    assertThat(decider.isAssertShadowVariablesAreNotStaleAfterStep()).isFalse();

    // After enabling assertions, they should be set correctly
    decider.enableAssertions(EnvironmentMode.FULL_ASSERT);
    assertThat(decider.isAssertStepScoreFromScratch()).isTrue();
    assertThat(decider.isAssertExpectedStepScore()).isTrue();
    assertThat(decider.isAssertShadowVariablesAreNotStaleAfterStep()).isTrue();
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
}
