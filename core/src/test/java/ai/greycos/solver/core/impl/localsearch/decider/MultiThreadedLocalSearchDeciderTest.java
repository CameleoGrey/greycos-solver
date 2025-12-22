package ai.greycos.solver.core.impl.localsearch.decider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadFactory;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.thread.DefaultSolverThreadFactory;
import ai.greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.neighborhood.MoveRepository;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;

import org.junit.jupiter.api.Test;

/**
 * Test for MultiThreadedLocalSearchDecider functionality.
 */
class MultiThreadedLocalSearchDeciderTest {

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

        MultiThreadedLocalSearchDecider<TestSolution> decider = new MultiThreadedLocalSearchDecider<>(
                logIndentation, termination, moveRepository, acceptor, forager,
                threadFactory, moveThreadCount, selectedMoveBufferSize);

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

        MultiThreadedLocalSearchDecider<TestSolution> decider = new MultiThreadedLocalSearchDecider<>(
                logIndentation, termination, moveRepository, acceptor, forager,
                threadFactory, moveThreadCount, selectedMoveBufferSize);

        // Test fully asserted mode
        decider.enableAssertions(EnvironmentMode.FULL_ASSERTED);
        assertThat(decider.isAssertStepScoreFromScratch()).isTrue();
        assertThat(decider.isAssertExpectedStepScore()).isTrue();
        assertThat(decider.isAssertShadowVariablesAreNotStaleAfterStep()).isTrue();

        // Test intrusively asserted mode
        decider.enableAssertions(EnvironmentMode.INTRUSIVELY_ASSERTED);
        assertThat(decider.isAssertStepScoreFromScratch()).isFalse();
        assertThat(decider.isAssertExpectedStepScore()).isTrue();
        assertThat(decider.isAssertShadowVariablesAreNotStaleAfterStep()).isTrue();

        // Test non-intrusive mode
        decider.enableAssertions(EnvironmentMode.NON_INTRUSIVE_FULL_ASSERTED);
        assertThat(decider.isAssertStepScoreFromScratch()).isTrue();
        assertThat(decider.isAssertExpectedStepScore()).isFalse();
        assertThat(decider.isAssertShadowVariablesAreNotStaleAfterStep()).isFalse();
    }

    // Mock classes for testing
    private static class TestSolution implements PlanningSolution {
        @Override
        public Score<?> getScore() {
            return null;
        }

        @Override
        public void setScore(Score<?> score) {
            // Mock implementation
        }
    }

    private static class TestMove implements Move<TestSolution> {
        @Override
        public boolean isMoveDoable(Object scoreDirector) {
            return true;
        }

        @Override
        public TestSolution doMove(Object scoreDirector) {
            return null;
        }

        @Override
        public TestMove rebase(Object destinationScoreDirector) {
            return this;
        }
    }
}