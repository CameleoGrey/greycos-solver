package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import greycos.solver.core.api.solver.alns.AlnsAssignment;
import greycos.solver.core.api.solver.alns.AlnsTerminationException;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;

class AlnsMultipleListTest {

  @Test
  void jointProbeRollsBackBothFieldsAndBasicThenCommitsExactlyTheCandidate() {
    var solution = TestdataMultipleListSolution.generateInitializedSolution(4, 4, 2);
    var left = solution.getEntityList().getFirst();
    var right = solution.getEntityList().getLast();
    left.setFirstPinnedIndex(1);
    left.setSecondPinnedIndex(2);
    try (var director = TestdataMultipleListSolution.buildScoreDirector()) {
      director.setWorkingSolution(solution);
      var baseline = director.calculateScore();
      var terminated = new AtomicBoolean();
      try (var context = new DefaultAlnsContext<>(director, new Random(0), terminated::get)) {
        context.beginTrial();
        assertThat(context.targets())
            .noneMatch(
                target ->
                    target.value() == solution.getFirstValueList().get(0)
                        || target.value() == solution.getSecondValueList().get(0)
                        || target.value() == solution.getSecondValueList().get(2));
        var firstTarget =
            context.targets().stream()
                .filter(target -> target.value() == solution.getFirstValueList().get(2))
                .findFirst()
                .orElseThrow();
        var secondTarget =
            context.targets().stream()
                .filter(target -> target.value() == solution.getSecondValueList().get(1))
                .findFirst()
                .orElseThrow();
        var basicTarget =
            context.targets().stream()
                .filter(
                    target ->
                        target.entity() == left
                            && target.variable().variableName().equals("basicValue"))
                .findFirst()
                .orElseThrow();
        var first = new AlnsAssignment<>(firstTarget, right, firstTarget.value(), 0);
        var second = new AlnsAssignment<>(secondTarget, left, secondTarget.value(), 2);
        var basic = new AlnsAssignment<>(basicTarget, left, 3, -1);
        var evaluated =
            context.evaluate(
                view -> {
                  view.assign(first);
                  view.assign(second);
                  view.assign(basic);
                });
        assertThat(evaluated.isComplete()).isTrue();
        assertThat(director.calculateScore()).isEqualTo(baseline);
        assertThat(left.getFirstValueList()).hasSize(2);
        assertThat(left.getSecondValueList()).hasSize(2);
        assertThat(left.getBasicValue()).isZero();
        var failure = new IllegalStateException("Failure after a joint assignment.");
        assertThatThrownBy(
                () ->
                    context.evaluate(
                        view -> {
                          view.assign(first);
                          view.assign(second);
                          view.assign(basic);
                          throw failure;
                        }))
            .isSameAs(failure);
        assertThat(director.calculateScore()).isEqualTo(baseline);
        assertThatThrownBy(
                () ->
                    context.evaluate(
                        view -> {
                          view.assign(first);
                          view.assign(second);
                          view.assign(basic);
                          terminated.set(true);
                        }))
            .isInstanceOf(AlnsTerminationException.class);
        terminated.set(false);
        assertThat(director.calculateScore()).isEqualTo(baseline);
        assertThat(solution.getFirstValueList().get(2).getEntity()).isSameAs(left);
        assertThat(solution.getSecondValueList().get(1).getEntity()).isSameAs(right);
        context.rollback();
        context.beginTrial();
        context.execute(
            view -> {
              view.assign(first);
              view.assign(second);
              view.assign(basic);
            });
        context.commit();
        assertThat(director.calculateScore().raw()).isEqualTo(evaluated.score());
        assertThat(solution.getFirstValueList().get(2).getEntity()).isSameAs(right);
        assertThat(solution.getSecondValueList().get(1).getEntity()).isSameAs(left);
        assertThat(left.getBasicValue()).isEqualTo(3);
      }
    }
  }

  @Test
  void preparedAssignmentsRebindEachListInAnIndependentModel() {
    try (var source = TestdataMultipleListSolution.buildScoreDirector();
        var destination = TestdataMultipleListSolution.buildScoreDirector()) {
      source.setWorkingSolution(TestdataMultipleListSolution.generateInitializedSolution(4, 4, 2));
      destination.setWorkingSolution(
          TestdataMultipleListSolution.generateInitializedSolution(4, 4, 2));
      var model = new AlnsModel<>(source, () -> {});
      for (var name : List.of("firstValueList", "secondValueList", "basicValue")) {
        var target =
            model.targets(false).stream()
                .filter(candidate -> candidate.variable().variableName().equals(name))
                .findFirst()
                .orElseThrow();
        var assignments = model.prepareAssignments(target);
        var rebased = assignments.rebase(destination.getMoveDirector());
        for (int i = 0; i < assignments.size(); i++) {
          assertThat(destination.executeTemporaryMove(rebased.move(i), true))
              .isEqualTo(source.executeTemporaryMove(assignments.move(i), true));
        }
      }
    }
  }
}
