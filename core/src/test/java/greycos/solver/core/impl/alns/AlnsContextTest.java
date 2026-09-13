package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.alns.AlnsAssignment;
import greycos.solver.core.api.solver.alns.AlnsListVariable;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTerminationException;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.valuerange.unassignedvar.pinned.TestdataListUnassignedPinnedEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.unassignedvar.pinned.TestdataListUnassignedPinnedEntityProvidingSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.valuerange.TestdataValueRangeEntity;
import greycos.solver.core.testcotwin.valuerange.TestdataValueRangeSolution;
import greycos.solver.core.testcotwin.valuerange.entityproviding.multivar.TestdataAllowsUnassignedMultiVarEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.multivar.TestdataAllowsUnassignedMultiVarEntityProvidingSolution;

import org.junit.jupiter.api.Test;

class AlnsContextTest {
  @Test
  void nestedProbesRestoreScoresAndRetainedCandidateExecutesOnce() {
    try (var f = new BasicFixture()) {
      f.context.beginTrial();
      var target = f.context.targets().getFirst();
      f.context.setPendingTargets(List.of(target));
      var one = new AlnsAssignment<>(target, f.entity, f.values.get(1), -1);
      var two = new AlnsAssignment<>(target, f.entity, f.values.get(2), -1);
      var outer =
          f.context.evaluate(
              view -> {
                view.assign(one);
                assertThat(f.context.score().score()).isEqualTo(SimpleScore.ONE);
                assertThat(f.context.evaluate(two).score()).isEqualTo(SimpleScore.of(2));
                assertThat(f.entity.getValue()).isSameAs(f.values.get(1));
                assertThat(f.solution.getScore()).isEqualTo(SimpleScore.ONE);
              });
      assertThat(outer.score()).isEqualTo(SimpleScore.ONE);
      f.assertOriginal();
      assertThat(f.context.pendingTargets()).containsExactly(target);
      assertThat(f.context.isChanged()).isFalse();
      var executions = new AtomicInteger();
      f.context.execute(
          view -> {
            executions.incrementAndGet();
            view.assign(two);
          });
      assertThat(f.context.isChanged()).isTrue();
      f.context.commit();
      assertThat(executions).hasValue(1);
      assertThat(f.entity.getValue()).isSameAs(f.values.get(2));
      assertThat(f.director.calculateScore().raw()).isEqualTo(SimpleScore.of(2));
    }
  }

  @Test
  void scratchFailureAndCooperativeCancellationPreserveTheIncumbent() {
    try (var f = new BasicFixture()) {
      f.context.beginTrial();
      var assignment =
          new AlnsAssignment<>(f.context.targets().getFirst(), f.entity, f.values.get(1), -1);
      var failure = new IllegalArgumentException("Operator failed after a balanced mutation.");
      assertThatThrownBy(
              () ->
                  f.context.evaluate(
                      view -> {
                        view.assign(assignment);
                        throw failure;
                      }))
          .isSameAs(failure);
      f.assertOriginal();
      f.context.rollback();

      f.context.beginTrial();
      assertThatThrownBy(
              () ->
                  f.context.evaluate(
                      view -> {
                        view.assign(assignment);
                        f.terminated.set(true);
                      }))
          .isInstanceOf(AlnsTerminationException.class);
      f.assertOriginal();
      f.context.rollback();
    }
  }

  @Test
  void partiallyNotifiedMutationRestoresSnapshotsAndRebuildsScoring() {
    try (var f = new BasicFixture()) {
      var transaction = new AlnsTransaction<>(f.director);
      transaction.begin();
      var variable = f.director.getSolutionDescriptor().getBasicVariableDescriptorList().getFirst();
      var failure = new IllegalStateException("Failure between before and after notification.");
      assertThatThrownBy(
              () ->
                  transaction.apply(
                      variable,
                      f.entity,
                      recorder -> {
                        recorder.beforeVariableChanged(variable, f.entity);
                        f.entity.setValue(f.values.get(2));
                        throw failure;
                      }))
          .isSameAs(failure);
      assertThat(transaction.isActive()).isFalse();
      f.assertOriginal();
    }
  }

  @Test
  void listProbesRestoreOwnerIndexAndOrderAndCommitKeepsExactlyTheScoredCandidate() {
    try (var f = new ListFixture()) {
      f.context.beginTrial();
      var target =
          f.context.targets().stream().filter(t -> t.value() == f.b).findFirst().orElseThrow();
      f.context.setPendingTargets(List.of(target));
      f.context.destroy(target);
      assertThat(f.context.score().unassignedCount()).isEqualTo(1);
      var assignment = new AlnsAssignment<>(target, f.right, f.b, 0);
      var evaluated = f.context.evaluate(assignment);
      assertThat(evaluated.isComplete()).isTrue();
      assertThat(f.b.getEntity()).isNull();
      assertThat(f.b.getIndex()).isNull();
      assertThat(f.left.getValueList()).containsExactly(f.a);
      assertThat(f.right.getValueList()).containsExactly(f.c);
      assertThat(f.context.pendingTargets()).containsExactly(target);
      f.context.assign(assignment);
      assertThat(f.context.score()).isEqualTo(evaluated);
      f.context.commit();
      assertThat(f.right.getValueList()).containsExactly(f.b, f.c);
      assertThat(f.b.getEntity()).isSameAs(f.right);
      assertThat(f.b.getIndex()).isZero();
      assertThat(f.c.getIndex()).isEqualTo(1);

      f.context.beginTrial();
      f.context.destroy(target);
      f.context.rollback();
      assertThat(f.right.getValueList()).containsExactly(f.b, f.c);
      assertThat(f.context.score()).isEqualTo(evaluated);
      assertThat(f.b.getEntity()).isSameAs(f.right);
      assertThat(f.b.getIndex()).isZero();
    }
  }

  @Test
  void repeatedListWindowsAndUnbalancedListFailureRestoreTheFullOriginalList() {
    try (var f = new ListFixture()) {
      f.context.beginTrial();
      var targetA =
          f.context.targets().stream().filter(t -> t.value() == f.a).findFirst().orElseThrow();
      var targetB =
          f.context.targets().stream().filter(t -> t.value() == f.b).findFirst().orElseThrow();
      f.context.destroy(targetA);
      f.context.destroy(targetB);
      f.context.assign(new AlnsAssignment<>(targetB, f.right, f.b, 1));
      f.context.assign(new AlnsAssignment<>(targetA, f.right, f.a, 0));
      f.context.rollback();
      f.assertOriginal();

      var transaction = new AlnsTransaction<>(f.director);
      transaction.begin();
      var descriptor = f.director.getSolutionDescriptor().getListVariableDescriptor();
      var failure = new IllegalStateException("Incomplete list mutation.");
      assertThatThrownBy(
              () ->
                  transaction.apply(
                      descriptor,
                      f.left,
                      recorder -> {
                        recorder.beforeListVariableChanged(descriptor, f.left, 0, 1);
                        f.left.getValueList().removeFirst();
                        throw failure;
                      }))
          .isSameAs(failure);
      f.assertOriginal();
    }
  }

  @Test
  void nullableRecoveryIsExplicitAndCannotAssignUntargetedVariables() {
    var solution = TestdataAllowsUnassignedMultiVarEntityProvidingSolution.generateSolution();
    var descriptor =
        TestdataAllowsUnassignedMultiVarEntityProvidingSolution.buildSolutionDescriptor();
    try (var director =
            director(
                descriptor,
                solution,
                factory ->
                    new Constraint[] {
                      factory
                          .forEachIncludingUnassigned(
                              TestdataAllowsUnassignedMultiVarEntityProvidingEntity.class)
                          .reward(SimpleScore.ONE, entity -> entity.getValue() == null ? 0 : 1)
                          .asConstraint("Assigned")
                    });
        var context = new DefaultAlnsContext<>(director, new Random(0), () -> false)) {
      context.beginTrial();
      var target =
          context.unassignedTargets().stream()
              .filter(t -> t.variable().variableName().equals("value"))
              .findFirst()
              .orElseThrow();
      var other =
          context.unassignedTargets().stream()
              .filter(t -> t.variable().variableName().equals("secondValue"))
              .findFirst()
              .orElseThrow();
      context.setPendingTargets(List.of(target));
      assertThat(context.assignments(target)).filteredOn(AlnsAssignment::isUnassigned).hasSize(1);
      var forbidden =
          context.assignments(other).stream()
              .filter(a -> !a.isUnassigned())
              .findFirst()
              .orElseThrow();
      assertThatThrownBy(() -> context.assign(forbidden))
          .isInstanceOf(IllegalArgumentException.class);
      var leaveUnassigned =
          context.assignments(target).stream()
              .filter(AlnsAssignment::isUnassigned)
              .findFirst()
              .orElseThrow();
      context.assign(leaveUnassigned);
      assertThat(context.pendingTargets()).isEmpty();
      assertThat(context.isChanged()).isFalse();
      context.commit();
      assertThat(solution.getEntityList().getFirst().getSecondValue()).isNull();
    }
  }

  @Test
  void listEntityRangesAndBothFormsOfPinningLimitDestinations() {
    var a = new TestdataValue("a");
    var b = new TestdataValue("b");
    var dropped = new TestdataValue("dropped");
    var left =
        new TestdataListUnassignedPinnedEntityProvidingEntity("left", List.of(a, b, dropped));
    left.setValueList(new ArrayList<>(List.of(a, b)));
    left.setPinIndex(1);
    var pinned =
        new TestdataListUnassignedPinnedEntityProvidingEntity("pinned", List.of(a, b, dropped));
    pinned.setPinned(true);
    var limited = new TestdataListUnassignedPinnedEntityProvidingEntity("limited", List.of(a));
    var solution = new TestdataListUnassignedPinnedEntityProvidingSolution();
    solution.setEntityList(List.of(left, pinned, limited));
    try (var director =
            director(
                TestdataListUnassignedPinnedEntityProvidingSolution.buildSolutionDescriptor(),
                solution,
                factory ->
                    new Constraint[] {
                      factory
                          .forEach(TestdataListUnassignedPinnedEntityProvidingEntity.class)
                          .reward(SimpleScore.ONE, e -> e.getValueList().size())
                          .asConstraint("Assigned")
                    });
        var context = new DefaultAlnsContext<>(director, new Random(0), () -> false)) {
      context.beginTrial();
      assertThat(context.targets()).extracting(AlnsTarget::value).containsExactly(b);
      var group =
          BuiltinAlnsOperators
              .<TestdataListUnassignedPinnedEntityProvidingSolution, SimpleScore>destroy(
                  new AlnsDestroyOperatorConfig().withType(AlnsDestroyOperatorType.GROUP_REMOVAL));
      assertThat(group.select(context, 100)).extracting(AlnsTarget::value).containsExactly(b);
      assertThat(context.unassignedTargets())
          .extracting(AlnsTarget::value)
          .containsExactly(dropped);
      var target = context.targets().getFirst();
      assertThat(context.assignments(target).stream().filter(a1 -> !a1.isUnassigned()).toList())
          .allSatisfy(
              assignment -> {
                assertThat(assignment.entity()).isSameAs(left);
                assertThat(assignment.index()).isEqualTo(1);
              });
      assertThatThrownBy(() -> context.assign(new AlnsAssignment<>(target, limited, b, 0)))
          .isInstanceOf(IllegalArgumentException.class);
      var pinnedTarget = new AlnsTarget<>(target.variable(), left, a);
      assertThatThrownBy(() -> context.destroy(pinnedTarget))
          .isInstanceOf(IllegalArgumentException.class);
      context.rollback();
      assertThat(left.getValueList()).containsExactly(a, b);
    }
  }

  @Test
  void mixedModelKeepsIndependentBasicBindingsAndTheirShadows() {
    var solution = TestdataMixedSolution.generateUninitializedSolution(2, 2, 2);
    var left = solution.getEntityList().getFirst();
    var right = solution.getEntityList().getLast();
    for (var entity : solution.getEntityList()) {
      entity.setBasicValue(solution.getOtherValueList().getFirst());
      entity.setSecondBasicValue(solution.getOtherValueList().getFirst());
    }
    left.getValueList().addAll(solution.getValueList());
    right.setPinned(true);
    left.setPinnedIndex(1);
    try (var director =
            director(
                TestdataMixedSolution.buildSolutionDescriptor(),
                solution,
                factory ->
                    new Constraint[] {
                      factory
                          .forEachIncludingUnassigned(TestdataMixedEntity.class)
                          .reward(
                              SimpleScore.ONE,
                              e -> e.getBasicValue() == null ? 0 : e.getBasicValue().getStrength())
                          .asConstraint("Basic")
                    });
        var context = new DefaultAlnsContext<>(director, new Random(0), () -> false)) {
      context.beginTrial();
      assertThat(context.variables()).hasSize(3);
      assertThat(context.targets()).hasSize(3);
      var group =
          BuiltinAlnsOperators.<TestdataMixedSolution, SimpleScore>destroy(
              new AlnsDestroyOperatorConfig().withType(AlnsDestroyOperatorType.GROUP_REMOVAL));
      assertThat(group.select(context, 100)).hasSize(1);
      var target =
          context.targets().stream()
              .filter(t -> t.variable().variableName().equals("basicValue"))
              .findFirst()
              .orElseThrow();
      context.setPendingTargets(List.of(target));
      context.destroy(target);
      assertThat(left.getBasicValue()).isNull();
      assertThat(left.getDeclarativeShadowVariableValue()).isNull();
      assertThat(left.getSecondBasicValue()).isSameAs(solution.getOtherValueList().getFirst());
      context.rollback();
      assertThat(left.getBasicValue()).isSameAs(solution.getOtherValueList().getFirst());
      assertThat(left.getDeclarativeShadowVariableValue())
          .isEqualTo(left.getBasicValue().getStrength());
      assertThat(left.getValueList()).containsExactlyElementsOf(solution.getValueList());
    }
  }

  @Test
  void unassignedListValuesReachableOnlyFromPinnedEntitiesAreNotRecoveryTargets() {
    var value = new TestdataValue("stranded");
    var entity = new TestdataListUnassignedPinnedEntityProvidingEntity("pinned", List.of(value));
    entity.setPinned(true);
    var solution = new TestdataListUnassignedPinnedEntityProvidingSolution();
    solution.setEntityList(List.of(entity));
    try (var director =
            director(
                TestdataListUnassignedPinnedEntityProvidingSolution.buildSolutionDescriptor(),
                solution,
                factory ->
                    new Constraint[] {
                      factory
                          .forEach(TestdataListUnassignedPinnedEntityProvidingEntity.class)
                          .reward(SimpleScore.ONE, e -> e.getValueList().size())
                          .asConstraint("Assigned")
                    });
        var context = new DefaultAlnsContext<>(director, new Random(0), () -> false)) {
      assertThat(context.targets()).isEmpty();
      assertThat(context.unassignedTargets()).isEmpty();
      var knownTarget = new AlnsTarget<>(context.variables().getFirst(), null, value);
      assertThat(context.currentAssignment(knownTarget).isUnassigned()).isTrue();
    }
  }

  @Test
  void listTargetIdentityNeverMergesEqualBusinessValues() {
    var variable =
        new AlnsListVariable<Object, Object, String>(Object.class, "items", String.class, true);
    assertThat(new AlnsTarget<>(variable, null, new String("same")))
        .isNotEqualTo(new AlnsTarget<>(variable, null, new String("same")));
  }

  @Test
  void equalBoxedBasicValuesAreNotAChangedCandidate() {
    var solution = new TestdataValueRangeSolution("numeric");
    var entity = new TestdataValueRangeEntity("entity");
    entity.setIntegerValue(solution.createIntValueRange().get(0));
    entity.setLongValue(solution.createLongValueRange().get(0));
    entity.setBigIntegerValue(solution.createBigIntegerValueRange().get(0));
    entity.setBigDecimalValue(solution.createBigDecimalValueRange().get(0));
    entity.setLocalDateValue(solution.createLocalDateValueRange().get(0));
    entity.setLocalTimeValue(solution.createLocaleTimeValueRange().get(0));
    entity.setLocalDateTimeValue(solution.createLocaleDateTimeValueRange().get(0));
    entity.setYearValue(solution.createYearValueRange().get(0));
    solution.setEntityList(List.of(entity));
    var originalValue = entity.getLongValue();
    try (var director =
            director(
                TestdataValueRangeSolution.buildSolutionDescriptor(),
                solution,
                factory ->
                    new Constraint[] {
                      factory
                          .forEach(TestdataValueRangeEntity.class)
                          .reward(SimpleScore.ONE, e -> e.getLongValue())
                          .asConstraint("Long value")
                    });
        var context = new DefaultAlnsContext<>(director, new Random(0), () -> false)) {
      context.beginTrial();
      var target =
          context.targets().stream()
              .filter(t -> t.variable().variableName().equals("longValue"))
              .findFirst()
              .orElseThrow();
      context.setPendingTargets(List.of(target));
      var equalAssignment =
          context.assignments(target).stream()
              .filter(a -> originalValue.equals(a.value()))
              .findFirst()
              .orElseThrow();
      assertThat(equalAssignment.value()).isEqualTo(originalValue).isNotSameAs(originalValue);
      context.destroy(target);
      context.assign(equalAssignment);
      assertThat(context.isChanged()).isFalse();
      context.rollback();
      assertThat(entity.getLongValue()).isSameAs(originalValue);

      context.beginTrial();
      context.assign(equalAssignment);
      assertThat(context.isChanged()).isFalse();
      context.commit();
      assertThat(entity.getLongValue()).isSameAs(originalValue);
    }
  }

  @Test
  void foreignListValuesAndAssignmentsWithChangedTargetIdentityAreRejected() {
    try (var f = new ListFixture()) {
      f.context.beginTrial();
      var target = f.context.targets().getFirst();
      var foreign = new AlnsTarget<>(target.variable(), null, new TestdataListValue(f.a.getCode()));
      assertThatThrownBy(() -> f.context.currentAssignment(foreign))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> f.context.destroy(foreign))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> f.context.assign(new AlnsAssignment<>(target, null, f.b, -1)))
          .isInstanceOf(IllegalArgumentException.class);
      f.context.rollback();
      f.assertOriginal();
    }
  }

  private static <S> BavetConstraintStreamScoreDirector<S, SimpleScore> director(
      SolutionDescriptor<S> descriptor, S solution, ConstraintProvider provider) {
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<S, SimpleScore>(
            descriptor, provider, EnvironmentMode.NO_ASSERT, false);
    var director = factory.createScoreDirectorBuilder().build();
    director.setWorkingSolution(solution);
    director.calculateScore();
    return director;
  }

  private static final class BasicFixture implements AutoCloseable {
    final List<TestdataValue> values =
        List.of(new TestdataValue("0"), new TestdataValue("1"), new TestdataValue("2"));
    final TestdataEntity entity = new TestdataEntity("entity", values.getFirst());
    final TestdataSolution solution = new TestdataSolution();
    final AtomicBoolean terminated = new AtomicBoolean();
    final BavetConstraintStreamScoreDirector<TestdataSolution, SimpleScore> director;
    final DefaultAlnsContext<TestdataSolution, SimpleScore> context;

    BasicFixture() {
      solution.setEntityList(List.of(entity));
      solution.setValueList(values);
      director =
          director(
              TestdataSolution.buildSolutionDescriptor(),
              solution,
              factory ->
                  new Constraint[] {
                    factory
                        .forEach(TestdataEntity.class)
                        .reward(SimpleScore.ONE, e -> Integer.parseInt(e.getValue().getCode()))
                        .asConstraint("Value")
                  });
      context = new DefaultAlnsContext<>(director, new Random(0), terminated::get);
    }

    void assertOriginal() {
      assertThat(entity.getValue()).isSameAs(values.getFirst());
      assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.ZERO);
    }

    @Override
    public void close() {
      context.close();
      director.close();
    }
  }

  private static final class ListFixture implements AutoCloseable {
    final TestdataListValue a = new TestdataListValue("a");
    final TestdataListValue b = new TestdataListValue("bb");
    final TestdataListValue c = new TestdataListValue("ccc");
    final TestdataListEntity left = new TestdataListEntity("left", a, b);
    final TestdataListEntity right = new TestdataListEntity("right", c);
    final TestdataListSolution solution = new TestdataListSolution();
    final BavetConstraintStreamScoreDirector<TestdataListSolution, SimpleScore> director;
    final DefaultAlnsContext<TestdataListSolution, SimpleScore> context;
    final SimpleScore original;

    ListFixture() {
      solution.setEntityList(List.of(left, right));
      solution.setValueList(List.of(a, b, c));
      director =
          director(
              TestdataListSolution.buildSolutionDescriptor(),
              solution,
              factory ->
                  new Constraint[] {
                    factory
                        .forEach(TestdataListValue.class)
                        .reward(
                            SimpleScore.ONE,
                            value ->
                                (value.getEntity() == left ? 10L : 1L)
                                    * (value.getIndex() + 1)
                                    * value.getCode().length())
                        .asConstraint("Placement")
                  });
      context = new DefaultAlnsContext<>(director, new Random(0), () -> false);
      original = director.calculateScore().raw();
    }

    void assertOriginal() {
      assertThat(left.getValueList()).containsExactly(a, b);
      assertThat(right.getValueList()).containsExactly(c);
      assertThat(a.getEntity()).isSameAs(left);
      assertThat(a.getIndex()).isZero();
      assertThat(b.getEntity()).isSameAs(left);
      assertThat(b.getIndex()).isEqualTo(1);
      assertThat(c.getEntity()).isSameAs(right);
      assertThat(c.getIndex()).isZero();
      assertThat(director.calculateScore().raw()).isEqualTo(original);
    }

    @Override
    public void close() {
      context.close();
      director.close();
    }
  }
}
