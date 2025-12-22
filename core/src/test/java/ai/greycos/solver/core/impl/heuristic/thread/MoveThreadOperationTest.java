package ai.greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.heuristic.move.AbstractMove;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for MoveThreadOperation and its subclasses. Tests the basic operation functionality
 * and ensures proper encapsulation of move thread operations.
 */
class MoveThreadOperationTest {

  @Test
  void testMoveThreadOperationBaseClass() {
    // Test the base MoveThreadOperation class
    MoveThreadOperation<TestSolution> operation = new TestMoveThreadOperation<>();

    assertThat(operation).isNotNull();
    assertThat(operation.toString()).isEqualTo("TestMoveThreadOperation");
  }

  @Test
  void testSetupOperation() {
    // Test SetupOperation with score director
    InnerScoreDirector<TestSolution, ?> scoreDirector = null; // Mock for testing
    SetupOperation<TestSolution, ?> setupOperation = new SetupOperation<>(scoreDirector);

    assertThat(setupOperation).isNotNull();
    assertThat(setupOperation.getScoreDirector()).isEqualTo(scoreDirector);
    assertThat(setupOperation.toString()).isEqualTo("SetupOperation");
  }

  @Test
  void testDestroyOperation() {
    // Test DestroyOperation
    DestroyOperation<TestSolution> destroyOperation = new DestroyOperation<>();

    assertThat(destroyOperation).isNotNull();
    assertThat(destroyOperation.toString()).isEqualTo("DestroyOperation");
  }

  @Test
  void testMoveEvaluationOperation() {
    // Test MoveEvaluationOperation with move
    Move<TestSolution> move = new TestMove<>();
    int stepIndex = 5;
    int moveIndex = 10;

    MoveEvaluationOperation<TestSolution> moveEvalOperation =
        new MoveEvaluationOperation<>(stepIndex, moveIndex, move);

    assertThat(moveEvalOperation).isNotNull();
    assertThat(moveEvalOperation.getStepIndex()).isEqualTo(stepIndex);
    assertThat(moveEvalOperation.getMoveIndex()).isEqualTo(moveIndex);
    assertThat(moveEvalOperation.getMove()).isEqualTo(move);
    assertThat(moveEvalOperation.toString()).isEqualTo("MoveEvaluationOperation");
  }

  @Test
  void testApplyStepOperation() {
    // Test ApplyStepOperation with step and score
    Move<TestSolution> step = new TestMove<>();
    SimpleScore score = SimpleScore.of(100);
    int stepIndex = 3;

    ApplyStepOperation<TestSolution, SimpleScore> applyStepOperation =
        new ApplyStepOperation<>(stepIndex, step, score);

    assertThat(applyStepOperation).isNotNull();
    assertThat(applyStepOperation.getStepIndex()).isEqualTo(stepIndex);
    assertThat(applyStepOperation.getStep()).isEqualTo(step);
    assertThat(applyStepOperation.getScore()).isEqualTo(score);
    assertThat(applyStepOperation.toString()).isEqualTo("ApplyStepOperation");
  }

  @Test
  void testOperationToStringConsistency() {
    // Test that all operations have consistent toString() behavior
    MoveThreadOperation<TestSolution> setupOp = new SetupOperation<>(null);
    MoveThreadOperation<TestSolution> destroyOp = new DestroyOperation<>();
    MoveThreadOperation<TestSolution> moveEvalOp = new MoveEvaluationOperation<>(0, 0, null);
    MoveThreadOperation<TestSolution> applyStepOp =
        new ApplyStepOperation<TestSolution, SimpleScore>(0, null, null);

    assertThat(setupOp.toString()).isEqualTo("SetupOperation");
    assertThat(destroyOp.toString()).isEqualTo("DestroyOperation");
    assertThat(moveEvalOp.toString()).isEqualTo("MoveEvaluationOperation");
    assertThat(applyStepOp.toString()).isEqualTo("ApplyStepOperation");
  }

  @Test
  void testMoveEvaluationOperationWithNullMove() {
    // Test MoveEvaluationOperation with null move (edge case)
    MoveEvaluationOperation<TestSolution> moveEvalOperation =
        new MoveEvaluationOperation<>(0, 0, null);

    assertThat(moveEvalOperation).isNotNull();
    assertThat(moveEvalOperation.getStepIndex()).isEqualTo(0);
    assertThat(moveEvalOperation.getMoveIndex()).isEqualTo(0);
    assertThat(moveEvalOperation.getMove()).isNull();
  }

  @Test
  void testApplyStepOperationWithNullStep() {
    // Test ApplyStepOperation with null step (edge case)
    ApplyStepOperation<TestSolution, ?> applyStepOperation =
        new ApplyStepOperation<>(0, null, null);

    assertThat(applyStepOperation).isNotNull();
    assertThat(applyStepOperation.getStepIndex()).isEqualTo(0);
    assertThat(applyStepOperation.getStep()).isNull();
    assertThat(applyStepOperation.getScore()).isNull();
  }

  @Test
  void testOperationInheritance() {
    // Test that all operations properly inherit from MoveThreadOperation
    MoveThreadOperation<TestSolution> setupOp = new SetupOperation<>(null);
    MoveThreadOperation<TestSolution> destroyOp = new DestroyOperation<>();
    MoveThreadOperation<TestSolution> moveEvalOp = new MoveEvaluationOperation<>(0, 0, null);
    MoveThreadOperation<TestSolution> applyStepOp =
        new ApplyStepOperation<TestSolution, SimpleScore>(0, null, null);

    assertThat(setupOp).isInstanceOf(MoveThreadOperation.class);
    assertThat(destroyOp).isInstanceOf(MoveThreadOperation.class);
    assertThat(moveEvalOp).isInstanceOf(MoveThreadOperation.class);
    assertThat(applyStepOp).isInstanceOf(MoveThreadOperation.class);
  }

  // Mock classes for testing
  private static class TestSolution {
    // Mock solution class
  }

  private static class TestMoveThreadOperation<Solution_> extends MoveThreadOperation<Solution_> {
    // Test implementation of MoveThreadOperation
  }

  private static class TestMove<Solution_> extends AbstractMove<Solution_> {
    @Override
    public boolean isMoveDoable(
        ai.greycos.solver.core.api.score.director.ScoreDirector<Solution_> scoreDirector) {
      return true;
    }

    @Override
    protected void doMoveOnGenuineVariables(
        ai.greycos.solver.core.api.score.director.ScoreDirector<Solution_> scoreDirector) {
      // Mock implementation
    }

    @Override
    public String getSimpleMoveTypeDescription() {
      return "TestMove";
    }

    @Override
    public Move<Solution_> rebase(
        ai.greycos.solver.core.api.score.director.ScoreDirector<Solution_>
            destinationScoreDirector) {
      return this; // For testing, just return this
    }
  }
}
