package greycos.solver.core.impl.move;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.DefaultPlanningVariableMetaModel;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;

class TemporaryMoveDirectorTest {

  @Test
  void scoreCallbackAndConsumerSeeTemporaryStateAcrossRepeatedMoves() {
    try (var fixture = new Fixture()) {
      for (var value : fixture.values.subList(1, fixture.values.size())) {
        var expectedScore = SimpleScore.of(Integer.parseInt(value.getCode()));
        var result =
            fixture.moveDirector.executeTemporaryWithScore(
                fixture.change(value),
                score -> {
                  assertThat(fixture.entity.getValue()).isSameAs(value);
                  assertThat(score.raw()).isEqualTo(expectedScore);
                  return value.getCode();
                });
        assertThat(result).isEqualTo(value.getCode());
        fixture.assertOriginalState();

        var score =
            fixture.scoreDirector.executeTemporaryMove(
                fixture.change(value),
                view -> assertThat(fixture.entity.getValue()).isSameAs(value),
                true);
        assertThat(score.raw()).isEqualTo(expectedScore);
        fixture.assertOriginalState();
      }
    }
  }

  @Test
  void nestedTemporaryMovesRestoreOuterStateBeforeRestoringOriginalState() {
    try (var fixture = new Fixture()) {
      var result =
          fixture.moveDirector.executeTemporaryWithScore(
              fixture.change(fixture.values.get(1)),
              outerScore -> {
                var innerScore =
                    fixture.moveDirector.executeTemporaryWithScore(
                        fixture.change(fixture.values.get(2)), Function.identity());
                assertThat(innerScore.raw()).isEqualTo(SimpleScore.of(2));
                assertThat(fixture.entity.getValue()).isSameAs(fixture.values.get(1));
                assertThat(fixture.scoreDirector.calculateScore()).isEqualTo(outerScore);
                return outerScore;
              });
      assertThat(result.raw()).isEqualTo(SimpleScore.ONE);
      fixture.assertOriginalState();
      fixture.moveDirector.executeTemporaryWithScore(
          fixture.change(fixture.values.get(2)), Function.identity());
      fixture.assertOriginalState();
    }
  }

  @Test
  void retainedUndoSurvivesLaterTemporaryMoves() {
    try (var fixture = new Fixture()) {
      var undoMove =
          fixture.moveDirector.executeTemporary(
              fixture.change(fixture.values.get(1)), (score, undo) -> undo);
      fixture.assertOriginalState();

      fixture.moveDirector.executeTemporaryWithScore(
          fixture.change(fixture.values.get(2)), Function.identity());
      fixture.assertOriginalState();

      fixture.moveDirector.execute(fixture.change(fixture.values.get(1)));
      fixture.moveDirector.execute(undoMove);
      fixture.assertOriginalState();
    }
  }

  @Test
  void undoRetainedThroughNonDelegatingRecorderSurvivesReuse() {
    try (var fixture = new Fixture()) {
      var recorder =
          new VariableChangeRecordingScoreDirector<TestdataSolution, SimpleScore>(
              fixture.scoreDirector);
      fixture.recordChange(recorder, fixture.values.get(1));
      var undoMove = recorder.getNonDelegating().createUndoMove();
      recorder.undoChanges();
      fixture.assertOriginalState();

      fixture.recordChange(recorder, fixture.values.get(2));
      recorder.undoChanges();
      fixture.assertOriginalState();

      fixture.moveDirector.execute(fixture.change(fixture.values.get(1)));
      fixture.moveDirector.execute(undoMove);
      fixture.assertOriginalState();
    }
  }

  @Test
  void retainedEmptyUndoRemainsEmptyAfterRecorderReuse() {
    try (var fixture = new Fixture()) {
      var recorder =
          new VariableChangeRecordingScoreDirector<TestdataSolution, SimpleScore>(
              fixture.scoreDirector);
      var undoMove = recorder.createUndoMove();
      recorder.undoChanges();

      fixture.recordChange(recorder, fixture.values.get(1));
      recorder.undoChanges();
      fixture.assertOriginalState();

      fixture.moveDirector.execute(fixture.change(fixture.values.get(2)));
      fixture.moveDirector.execute(undoMove);
      assertThat(fixture.entity.getValue()).isSameAs(fixture.values.get(2));
      assertThat(fixture.scoreDirector.calculateScore().raw()).isEqualTo(SimpleScore.of(2));
    }
  }

  @Test
  void callbackFailureRestoresStateAndAllowsNextTemporaryMove() {
    try (var fixture = new Fixture()) {
      var failure = new IllegalStateException("Callback failed");
      assertThatThrownBy(
              () ->
                  fixture.moveDirector.executeTemporaryWithScore(
                      fixture.change(fixture.values.get(1)),
                      score -> {
                        throw failure;
                      }))
          .isSameAs(failure);
      fixture.assertOriginalState();

      fixture.moveDirector.executeTemporaryWithScore(
          fixture.change(fixture.values.get(2)), Function.identity());
      fixture.assertOriginalState();
    }
  }

  @Test
  void scoreFailureUndoesCompletedMove() {
    var scoreDirector =
        (InnerScoreDirector<TestdataSolution, SimpleScore>) mock(InnerScoreDirector.class);
    var moveDirector = new MoveDirector<>(scoreDirector);
    var variableMetaModel =
        TestdataSolution.buildMetaModel()
            .genuineEntity(TestdataEntity.class)
            .basicVariable("value", TestdataValue.class);
    var originalValue = new TestdataValue("original");
    var entity = new TestdataEntity("entity", originalValue);
    var failure = new IllegalStateException("Scoring failed");
    when(scoreDirector.calculateScore()).thenThrow(failure);

    assertThatThrownBy(
            () ->
                moveDirector.executeTemporaryWithScore(
                    view ->
                        view.changeVariable(variableMetaModel, entity, new TestdataValue("next")),
                    Function.identity()))
        .isSameAs(failure);
    assertThat(entity.getValue()).isSameAs(originalValue);
  }

  @Test
  void failedMoveExecutionIsNotUndone() {
    try (var fixture = new Fixture()) {
      var failure = new IllegalStateException("Move failed");
      Move<TestdataSolution> failingMove =
          view -> {
            fixture.change(fixture.values.get(1)).execute(view);
            throw failure;
          };
      assertThatThrownBy(
              () ->
                  fixture.moveDirector.executeTemporaryWithScore(failingMove, Function.identity()))
          .isSameAs(failure);
      // Partial move execution is fatal; it must not trigger an additional, potentially invalid
      // undo.
      assertThat(fixture.entity.getValue()).isSameAs(fixture.values.get(1));
      assertThat(fixture.scoreDirector.calculateScore().raw()).isEqualTo(SimpleScore.ONE);
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final List<TestdataValue> values =
        List.of(new TestdataValue("0"), new TestdataValue("1"), new TestdataValue("2"));
    private final TestdataEntity entity = new TestdataEntity("entity", values.getFirst());
    private final BavetConstraintStreamScoreDirector<TestdataSolution, SimpleScore> scoreDirector;
    private final MoveDirector<TestdataSolution, SimpleScore> moveDirector;
    private final DefaultPlanningVariableMetaModel<TestdataSolution, TestdataEntity, TestdataValue>
        variableMetaModel;

    private Fixture() {
      var solutionDescriptor = TestdataSolution.buildSolutionDescriptor();
      variableMetaModel =
          (DefaultPlanningVariableMetaModel<TestdataSolution, TestdataEntity, TestdataValue>)
              solutionDescriptor
                  .getMetaModel()
                  .genuineEntity(TestdataEntity.class)
                  .basicVariable("value", TestdataValue.class);
      var factory =
          new BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>(
              solutionDescriptor,
              constraintFactory ->
                  new Constraint[] {
                    constraintFactory
                        .forEach(TestdataEntity.class)
                        .reward(SimpleScore.ONE, e -> Integer.parseInt(e.getValue().getCode()))
                        .asConstraint("Value")
                  },
              EnvironmentMode.NO_ASSERT,
              false);
      scoreDirector = factory.createScoreDirectorBuilder().build();
      var solution = new TestdataSolution();
      solution.setEntityList(List.of(entity));
      solution.setValueList(values);
      scoreDirector.setWorkingSolution(solution);
      moveDirector = scoreDirector.getMoveDirector();
      assertOriginalState();
    }

    private Move<TestdataSolution> change(TestdataValue value) {
      return view -> view.changeVariable(variableMetaModel, entity, value);
    }

    private void recordChange(
        VariableChangeRecordingScoreDirector<TestdataSolution, SimpleScore> recorder,
        TestdataValue value) {
      recorder.beforeVariableChanged(variableMetaModel.variableDescriptor(), entity);
      entity.setValue(value);
      recorder.afterVariableChanged(variableMetaModel.variableDescriptor(), entity);
      recorder.triggerVariableListeners();
    }

    private void assertOriginalState() {
      assertThat(entity.getValue()).isSameAs(values.getFirst());
      assertThat(scoreDirector.calculateScore().raw()).isEqualTo(SimpleScore.ZERO);
    }

    @Override
    public void close() {
      scoreDirector.close();
    }
  }
}
