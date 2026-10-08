package greycos.solver.core.impl.score.director;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningVariableMetaModel;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TemporaryMoveScoreCallbackTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void callbackObservesExactNativeScoreBeforeUndo(boolean previousTemporaryState) {
    try (var fixture = fixture(true)) {
      var director = fixture.director();
      director.setAllChangesWillBeUndoneBeforeStepEnds(previousTemporaryState);
      var calculationCount = director.getCalculationCount();
      var observedScore = new AtomicReference<InnerScore<SimpleScore>>();

      var result =
          director.executeTemporaryMoveWithScore(
              fixture.move(),
              (view, score) -> {
                assertThat(view.getValue(fixture.variable(), fixture.entity()))
                    .isSameAs(fixture.badValue());
                assertThat(fixture.solution().getScore()).isEqualTo(SimpleScore.of(-1));
                assertThat(score).isEqualTo(InnerScore.withUnassignedCount(SimpleScore.of(-1), 1));
                assertThat(director.isAllChangesWillBeUndoneBeforeStepEnds()).isTrue();
                assertThat(director.getCalculationCount()).isEqualTo(calculationCount + 1);
                observedScore.set(score);
              },
              true);

      assertThat(result).isSameAs(observedScore.get());
      assertThat(director.getCalculationCount()).isEqualTo(calculationCount + 1);
      assertRestored(fixture, previousTemporaryState);
      assertThat(director.calculateScore()).isEqualTo(fixture.initialScore());
    }
  }

  @Test
  void legacyCallbacksAndNoConsumerOverloadRetainTemporaryMoveBehavior() {
    try (var fixture = fixture(false)) {
      var director = fixture.director();
      var calculationCount = director.getCalculationCount();
      var observedValue = new AtomicReference<TestdataValue>();

      var callbackResult =
          director.executeTemporaryMove(
              fixture.move(),
              view -> {
                observedValue.set(view.getValue(fixture.variable(), fixture.entity()));
                assertThat(director.isAllChangesWillBeUndoneBeforeStepEnds()).isTrue();
              },
              true);

      assertThat(observedValue.get()).isSameAs(fixture.badValue());
      assertThat(callbackResult).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-1)));
      assertRestored(fixture, false);
      assertThat(director.executeTemporaryMove(fixture.move(), true)).isEqualTo(callbackResult);
      assertThat(director.getCalculationCount()).isEqualTo(calculationCount + 2);
      assertRestored(fixture, false);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void callbackFailureUndoesMoveAndRestoresTemporaryFlag(boolean previousTemporaryState) {
    try (var fixture = fixture(false)) {
      var director = fixture.director();
      director.setAllChangesWillBeUndoneBeforeStepEnds(previousTemporaryState);
      var failure = new IllegalStateException("Callback failed.");
      var calculationCount = director.getCalculationCount();

      assertThatThrownBy(
              () ->
                  director.executeTemporaryMoveWithScore(
                      fixture.move(),
                      (view, score) -> {
                        assertThat(score).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-1)));
                        throw failure;
                      },
                      true))
          .isSameAs(failure)
          .satisfies(thrown -> assertThat(thrown.getSuppressed()).isEmpty());

      assertThat(director.getCalculationCount()).isEqualTo(calculationCount + 1);
      assertRestored(fixture, previousTemporaryState);
      assertThat(director.calculateScore()).isEqualTo(fixture.initialScore());
      assertThat(director.executeTemporaryMove(fixture.move(), true).raw())
          .isEqualTo(SimpleScore.of(-1));
      assertRestored(fixture, previousTemporaryState);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void callbackFailureRemainsPrimaryWhenUndoFails(boolean previousTemporaryState) {
    try (var fixture = fixture(false)) {
      var director = fixture.director();
      director.setAllChangesWillBeUndoneBeforeStepEnds(previousTemporaryState);
      var failure = new AssertionError("Callback failed.");
      var undoFailure = new IllegalStateException("Undo failed.");

      try {
        assertThatThrownBy(
                () ->
                    director.executeTemporaryMoveWithScore(
                        fixture.move(),
                        (view, score) -> {
                          failSubsequentVariableChange(director, undoFailure);
                          throw failure;
                        },
                        true))
            .isSameAs(failure)
            .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(undoFailure));

        assertThat(director.isAllChangesWillBeUndoneBeforeStepEnds())
            .isEqualTo(previousTemporaryState);
        assertThat(fixture.solution().getScore()).isEqualTo(fixture.initialScore().raw());
      } finally {
        director.setWorkingSolutionMutationObserver(null);
      }
    }
  }

  @Test
  void undoFailureWithoutEarlierFailureIsPropagated() {
    try (var fixture = fixture(false)) {
      var director = fixture.director();
      var undoFailure = new IllegalStateException("Undo failed.");

      try {
        assertThatThrownBy(
                () ->
                    director.executeTemporaryMoveWithScore(
                        fixture.move(),
                        (view, score) -> failSubsequentVariableChange(director, undoFailure),
                        false))
            .isSameAs(undoFailure)
            .satisfies(thrown -> assertThat(thrown.getSuppressed()).isEmpty());

        assertThat(director.isAllChangesWillBeUndoneBeforeStepEnds()).isFalse();
        assertThat(fixture.solution().getScore()).isEqualTo(fixture.initialScore().raw());
      } finally {
        director.setWorkingSolutionMutationObserver(null);
      }
    }
  }

  @Test
  void moveFailureDoesNotUndoHalfAppliedMove() {
    try (var fixture = fixture(false)) {
      var director = fixture.director();
      var failure = new IllegalStateException("Move failed.");
      var calculationCount = director.getCalculationCount();
      Move<TestdataSolution> failingMove =
          view -> {
            fixture.move().execute(view);
            throw failure;
          };

      assertThatThrownBy(
              () ->
                  director.executeTemporaryMoveWithScore(
                      failingMove,
                      (view, score) -> {
                        throw new AssertionError("A failed move must not invoke the callback.");
                      },
                      true))
          .isSameAs(failure)
          .satisfies(thrown -> assertThat(thrown.getSuppressed()).isEmpty());

      assertThat(fixture.entity().getValue()).isSameAs(fixture.badValue());
      assertThat(director.getCalculationCount()).isEqualTo(calculationCount);
      assertThat(director.isAllChangesWillBeUndoneBeforeStepEnds()).isFalse();
    }
  }

  private static void failSubsequentVariableChange(
      InnerScoreDirector<TestdataSolution, SimpleScore> director, RuntimeException failure) {
    director.setWorkingSolutionMutationObserver(
        new WorkingSolutionMutationObserver<>() {
          @Override
          public void workingSolutionChanged() {}

          @Override
          public void beforeVariableChanged(Object entity, String variableName) {
            throw failure;
          }
        });
  }

  private static void assertRestored(Fixture fixture, boolean previousTemporaryState) {
    assertThat(fixture.entity().getValue()).isSameAs(fixture.goodValue());
    assertThat(fixture.solution().getScore()).isEqualTo(fixture.initialScore().raw());
    assertThat(fixture.director().isAllChangesWillBeUndoneBeforeStepEnds())
        .isEqualTo(previousTemporaryState);
  }

  private static Fixture fixture(boolean includeUninitializedEntity) {
    var descriptor = TestdataSolution.buildSolutionDescriptor();
    var variable =
        descriptor
            .getMetaModel()
            .genuineEntity(TestdataEntity.class)
            .basicVariable("value", TestdataValue.class);
    var goodValue = new TestdataValue("good");
    var badValue = new TestdataValue("bad");
    var entity = new TestdataEntity("A", goodValue);
    var solution = new TestdataSolution("solution");
    solution.setValueList(List.of(goodValue, badValue));
    var entities = new ArrayList<TestdataEntity>();
    entities.add(entity);
    if (includeUninitializedEntity) {
      entities.add(new TestdataEntity("B"));
    }
    solution.setEntityList(entities);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>(
            descriptor,
            constraintFactory ->
                new Constraint[] {
                  constraintFactory
                      .forEach(TestdataEntity.class)
                      .filter(candidate -> candidate.getValue().getCode().equals("bad"))
                      .penalize(SimpleScore.ONE)
                      .asConstraint("Bad value")
                },
            EnvironmentMode.TRACKED_FULL_ASSERT,
            false);
    var director =
        new BavetConstraintStreamScoreDirector.Builder<>(
                factory, EnvironmentMode.TRACKED_FULL_ASSERT)
            .build();
    director.setWorkingSolution(solution);
    return new Fixture(
        director, solution, entity, goodValue, badValue, variable, director.calculateScore());
  }

  private record Fixture(
      BavetConstraintStreamScoreDirector<TestdataSolution, SimpleScore> director,
      TestdataSolution solution,
      TestdataEntity entity,
      TestdataValue goodValue,
      TestdataValue badValue,
      PlanningVariableMetaModel<TestdataSolution, TestdataEntity, TestdataValue> variable,
      InnerScore<SimpleScore> initialScore)
      implements AutoCloseable {

    Move<TestdataSolution> move() {
      return view -> view.changeVariable(variable, entity, badValue);
    }

    @Override
    public void close() {
      director.close();
    }
  }
}
