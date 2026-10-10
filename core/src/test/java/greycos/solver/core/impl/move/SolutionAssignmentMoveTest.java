package greycos.solver.core.impl.move;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEasyScoreCalculator;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEntity;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListValue;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingScoreCalculator;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingValue;
import greycos.solver.core.testcotwin.mixed.singleentity.unassignedvar.TestdataUnassignedMixedEasyScoreCalculator;
import greycos.solver.core.testcotwin.mixed.singleentity.unassignedvar.TestdataUnassignedMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.unassignedvar.TestdataUnassignedMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.unassignedvar.TestdataUnassignedMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.unassignedvar.TestdataUnassignedMixedValue;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataMixedListElementEntity;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataMixedListElementSolution;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataMixedListElementValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class SolutionAssignmentMoveTest {

  @ParameterizedTest
  @MethodSource("listAssignments")
  void optionalMembershipAndOrderApplyUndoAndRetainSession(int[][] before, int[][] after) {
    var scope = optionalScope(before);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var original = capture(director);
      var beforeScore = director.calculateScore();
      var session = ((BavetConstraintStreamScoreDirector<?, ?>) director).getSession();
      var source = director.cloneWorkingSolution();
      setLists(source, after);
      var target =
          SolutionAssignments.captureComplete(director.getSolutionDescriptor(), source)
              .rebase(director);
      var move = new SolutionAssignmentMove<>(target);
      var undo =
          director
              .getMoveDirector()
              .executeTemporaryProducingUndoMove(
                  move,
                  score -> {
                    assertThat(target.matchesCurrent(director)).isTrue();
                    assertOptionalState(director, after);
                    director.assertWorkingScoreFromScratch(score, move);
                  });
      assertThat(original.matchesCurrent(director)).isTrue();
      assertThat(director.calculateScore()).isEqualTo(beforeScore);
      assertOptionalState(director, before);
      director.executeMove(move);
      assertOptionalState(director, after);
      director.executeMove(undo);
      assertOptionalState(director, before);
      assertThat(((BavetConstraintStreamScoreDirector<?, ?>) director).getSession())
          .isSameAs(session);
    }
  }

  static Stream<Arguments> listAssignments() {
    return Stream.of(
        Arguments.of(new int[][] {{0, 1}, {}}, new int[][] {{0}, {}}),
        Arguments.of(new int[][] {{}, {}}, new int[][] {{0, 1}, {}}),
        Arguments.of(new int[][] {{0, 1}, {2}}, new int[][] {{3}, {1}}),
        Arguments.of(new int[][] {{0, 1}, {2, 3}}, new int[][] {{3, 1}, {2, 0}}),
        Arguments.of(new int[][] {{0, 1}, {}}, new int[][] {{}, {}}),
        Arguments.of(new int[][] {{0}, {1}}, new int[][] {{0}, {1}}));
  }

  @Test
  void rebasedTransitionAndUndoUseDestinationIdentities() {
    var before = new int[][] {{0, 1}, {2}};
    var after = new int[][] {{3}, {1}};
    try (var director = optionalScope(before).<SimpleScore>getScoreDirector();
        var destination = optionalScope(before).<SimpleScore>getScoreDirector()) {
      var source = director.cloneWorkingSolution();
      setLists(source, after);
      var move =
          new SolutionAssignmentMove<>(
              SolutionAssignments.captureComplete(director.getSolutionDescriptor(), source)
                  .rebase(director));
      var undo = director.getMoveDirector().executeTemporaryProducingUndoMove(move, score -> {});
      destination.executeMove(move.rebase(destination));
      assertOptionalState(destination, after);
      destination.executeMove(undo.rebase(destination.getMoveDirector()));
      assertOptionalState(destination, before);
      assertOptionalState(director, before);
      assertThat(destination.getWorkingSolution().getValueList())
          .noneMatch(
              value ->
                  director.getWorkingSolution().getValueList().stream()
                      .anyMatch(other -> other == value));
    }
  }

  @Test
  void completeSnapshotRetainsPinnedBindingsAndImmutableAssignmentContainers() {
    var scope = mixedScope();
    var initial = mixedProblem();
    initial.getEntityList().getLast().setPinned(true);
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var snapshot = capture(director);
      assertThat(snapshot.getPlanningEntities()).hasSize(2);
      assertThat(snapshot.matchesCurrent(director)).isTrue();
      assertThat(new SolutionAssignmentMove<>(snapshot).isMoveDoable(director)).isFalse();
      var watched = spy(director);
      snapshot.apply(watched);
      verify(watched, never()).changeVariableFacade(any(), any(), any());
      assertThatThrownBy(() -> snapshot.getBasicChanges().clear())
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> snapshot.getListChanges().values().iterator().next().clear())
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(
              () ->
                  snapshot.getListChanges().values().iterator().next().getFirst().values().clear())
          .isInstanceOf(UnsupportedOperationException.class);
      var source = director.cloneWorkingSolution();
      var capturedSource =
          SolutionAssignments.captureComplete(director.getSolutionDescriptor(), source);
      source.getEntityList().getFirst().getValueList().clear();
      source.getEntityList().getFirst().setBasicValue(null);
      assertThat(capturedSource.rebase(director).sameAssignments(snapshot)).isTrue();
      assertThat(
              SolutionAssignments.captureComplete(director.getSolutionDescriptor(), source)
                  .rebase(director)
                  .sameAssignments(snapshot))
          .isFalse();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "duplicate",
        "foreignList",
        "pinnedPrefix",
        "shortPrefix",
        "mandatoryBasic",
        "foreignBasic",
        "equalForeignBasic",
        "pinnedEntity",
        "missingEntity"
      })
  void malformedMixedTargetFailsBeforeAnyWrite(String defect) {
    var scope = mixedScope();
    var initial = mixedProblem();
    initial.getEntityList().getLast().setPinnedIndex(1);
    if (defect.equals("pinnedEntity")) {
      initial.getEntityList().getLast().setPinned(true);
    }
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = capture(director);
      var score = director.calculateScore();
      var source = director.cloneWorkingSolution();
      var first = source.getEntityList().getFirst();
      var last = source.getEntityList().getLast();
      first.setBasicValue(source.getOtherValueList().getLast());
      switch (defect) {
        case "duplicate" -> first.getValueList().add(last.getValueList().getFirst());
        case "foreignList" -> first.getValueList().add(new TestdataUnassignedMixedValue("foreign"));
        case "pinnedPrefix" -> last.getValueList().set(0, source.getValueList().getFirst());
        case "shortPrefix" -> last.getValueList().clear();
        case "mandatoryBasic" -> last.setSecondBasicValue(null);
        case "foreignBasic" -> last.setBasicValue(new TestdataUnassignedMixedOtherValue("foreign"));
        case "equalForeignBasic" -> {} // Installed below to bypass working-object rebasing.
        case "pinnedEntity" -> last.setBasicValue(source.getOtherValueList().getLast());
        case "missingEntity" -> source.getEntityList().removeLast();
        default -> throw new IllegalStateException(defect);
      }
      // Rebase the entities and known values; unknown values are intentionally retained on the
      // current graph below so that range validation, rather than lookup, rejects them.
      if (defect.equals("foreignList")) {
        first.getValueList().removeLast();
      } else if (defect.equals("foreignBasic")) {
        last.setBasicValue(source.getOtherValueList().getFirst());
      }
      var target =
          SolutionAssignments.captureComplete(director.getSolutionDescriptor(), source)
              .rebase(director);
      if (defect.equals("foreignList")
          || defect.equals("foreignBasic")
          || defect.equals("equalForeignBasic")) {
        // Capture the malformed reference directly from a temporarily changed working graph.
        var working = director.getWorkingSolution();
        var workingFirst = working.getEntityList().getFirst();
        if (defect.equals("foreignList")) {
          workingFirst.getValueList().add(new TestdataUnassignedMixedValue("foreign"));
          target = capture(director);
          workingFirst.getValueList().removeLast();
        } else {
          var oldValue = workingFirst.getBasicValue();
          workingFirst.setBasicValue(
              defect.equals("equalForeignBasic")
                  ? new EqualOtherValue(oldValue.getCode())
                  : new TestdataUnassignedMixedOtherValue("foreign"));
          target = capture(director);
          workingFirst.setBasicValue(oldValue);
        }
      }
      var move = new SolutionAssignmentMove<>(target);
      assertThatThrownBy(() -> director.executeMove(move))
          .isInstanceOf(IllegalStateException.class);
      assertThat(before.matchesCurrent(director)).isTrue();
      assertThat(director.calculateScore()).isEqualTo(score);
      director.assertWorkingScoreFromScratch(score, defect);
    }
  }

  @Test
  void legalPinnedPrefixAndWholeEntityRemainIntactDuringMixedTransition() {
    var scope = mixedScope();
    var initial = mixedProblem();
    initial.getEntityList().getFirst().setPinnedIndex(1);
    initial.getEntityList().getLast().setPinned(true);
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = capture(director);
      var source = director.cloneWorkingSolution();
      source.getEntityList().getFirst().setBasicValue(null);
      source.getEntityList().getFirst().getValueList().add(source.getValueList().getLast());
      var move =
          new SolutionAssignmentMove<>(
              SolutionAssignments.captureComplete(director.getSolutionDescriptor(), source)
                  .rebase(director));
      director
          .getMoveDirector()
          .executeTemporaryProducingUndoMove(
              move,
              score -> {
                var working = director.getWorkingSolution();
                assertThat(working.getEntityList().getFirst().getBasicValue()).isNull();
                assertThat(working.getEntityList().getFirst().getValueList())
                    .containsExactly(
                        working.getValueList().getFirst(), working.getValueList().getLast());
                assertThat(working.getEntityList().getLast().getValueList())
                    .containsExactly(working.getValueList().get(1));
                assertThat(working.getEntityList().getLast().getBasicValue())
                    .isSameAs(working.getOtherValueList().getFirst());
                director.assertWorkingScoreFromScratch(score, move);
              });
      assertThat(before.matchesCurrent(director)).isTrue();
    }
  }

  @Test
  void mandatoryListRangeWithNoOwnersIsStillValidated() {
    var scope =
        SolutionAssignmentMoveTest.<TestdataPinnedWithIndexListSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                    TestdataPinnedWithIndexListSolution.class,
                    TestdataPinnedWithIndexListEntity.class,
                    TestdataPinnedWithIndexListValue.class)
                .withEasyScoreCalculatorClass(
                    TestdataPinnedWithIndexListEasyScoreCalculator.class));
    scope.setInitialSolution(
        TestdataPinnedWithIndexListSolution.generateUninitializedSolution(2, 0));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var snapshot = capture(director);
      assertThatThrownBy(() -> new SolutionAssignmentMove<>(snapshot).isMoveDoable(director))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("2 value(s) unassigned");
    }
  }

  @Test
  void mandatoryListMissingValueIsRejected() {
    var scope =
        SolutionAssignmentMoveTest.<TestdataPinnedWithIndexListSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                    TestdataPinnedWithIndexListSolution.class,
                    TestdataPinnedWithIndexListEntity.class,
                    TestdataPinnedWithIndexListValue.class)
                .withEasyScoreCalculatorClass(
                    TestdataPinnedWithIndexListEasyScoreCalculator.class));
    scope.setInitialSolution(TestdataPinnedWithIndexListSolution.generateInitializedSolution(4, 2));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = capture(director);
      var source = director.cloneWorkingSolution();
      source.getEntityList().getLast().getValueList().removeLast();
      var move =
          new SolutionAssignmentMove<>(
              SolutionAssignments.captureComplete(director.getSolutionDescriptor(), source)
                  .rebase(director));
      assertThatThrownBy(() -> director.executeMove(move))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Mandatory list variable");
      assertThat(before.matchesCurrent(director)).isTrue();
    }
  }

  @Test
  void destinationEntityRangeIsCheckedBeforeCrossListTransfer() {
    var scope =
        SolutionAssignmentMoveTest.<TestdataListEntityProvidingSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                    TestdataListEntityProvidingSolution.class,
                    TestdataListEntityProvidingEntity.class,
                    TestdataListEntityProvidingValue.class)
                .withEasyScoreCalculatorClass(TestdataListEntityProvidingScoreCalculator.class));
    var initial = TestdataListEntityProvidingSolution.generateSolution(4, 2, false);
    initial.getEntityList().forEach(entity -> entity.getValueList().addAll(entity.getValueRange()));
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = capture(director);
      var source = director.cloneWorkingSolution();
      var value = source.getEntityList().getFirst().getValueList().removeLast();
      source.getEntityList().getLast().getValueList().add(value);
      var move =
          new SolutionAssignmentMove<>(
              SolutionAssignments.captureComplete(director.getSolutionDescriptor(), source)
                  .rebase(director));
      assertThatThrownBy(() -> director.executeMove(move))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("outside the working value range");
      assertThat(before.matchesCurrent(director)).isTrue();
    }
  }

  @Test
  void mixedBasicAndListChangesPropagateDeclarativeShadowsAndNativeScore() {
    var scope =
        SolutionAssignmentMoveTest.<TestdataMixedListElementSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                    TestdataMixedListElementSolution.class,
                    TestdataMixedListElementEntity.class,
                    TestdataMixedListElementValue.class)
                .withScoreDirectorFactory(
                    new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(MixedScoreProvider.class)));
    var value0 = new TestdataMixedListElementValue("v0");
    value0.setDuration(1);
    var value1 = new TestdataMixedListElementValue("v1");
    value1.setDuration(2);
    var entity0 = new TestdataMixedListElementEntity("e0");
    var entity1 = new TestdataMixedListElementEntity("e1");
    entity0.getValues().add(value0);
    entity1.getValues().add(value1);
    var initial = new TestdataMixedListElementSolution();
    initial.setEntities(List.of(entity0, entity1));
    initial.setValues(List.of(value0, value1));
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = capture(director);
      var beforeScore = director.calculateScore();
      var source = director.cloneWorkingSolution();
      source.getValues().getFirst().setDuration(5);
      source.getEntities().getFirst().getValues().clear();
      source.getEntities().getLast().getValues().add(source.getValues().getFirst());
      var move =
          new SolutionAssignmentMove<>(
              SolutionAssignments.captureComplete(director.getSolutionDescriptor(), source)
                  .rebase(director));
      director
          .getMoveDirector()
          .executeTemporaryProducingUndoMove(
              move,
              score -> {
                var working = director.getWorkingSolution();
                assertThat(working.getValues().getFirst().getPaddedDuration()).isEqualTo(6);
                assertThat(working.getEntities().getFirst().getTotalDuration()).isZero();
                assertThat(working.getEntities().getLast().getTotalDuration()).isEqualTo(9);
                assertThat(score).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-9)));
                director.assertWorkingScoreFromScratch(score, move);
              });
      assertThat(before.matchesCurrent(director)).isTrue();
      assertThat(director.calculateScore()).isEqualTo(beforeScore);
      director.assertWorkingScoreFromScratch(beforeScore, move);
    }
  }

  private static <Solution_> SolutionAssignments<Solution_> capture(
      InnerScoreDirector<Solution_, ?> director) {
    return SolutionAssignments.captureComplete(
        director.getSolutionDescriptor(), director.getWorkingSolution());
  }

  private static SolverScope<TestdataUnassignedMixedSolution> mixedScope() {
    return scope(
        PlannerTestUtils.buildSolverConfig(
                TestdataUnassignedMixedSolution.class, TestdataUnassignedMixedEntity.class)
            .withEasyScoreCalculatorClass(TestdataUnassignedMixedEasyScoreCalculator.class)
            .withPhases(new CustomPhaseConfig().withCustomPhaseCommands(context -> {})));
  }

  private static TestdataUnassignedMixedSolution mixedProblem() {
    var solution = TestdataUnassignedMixedSolution.generateUninitializedSolution(2, 4, 2);
    for (int i = 0; i < 2; i++) {
      var entity = solution.getEntityList().get(i);
      entity.setBasicValue(solution.getOtherValueList().getFirst());
      entity.setSecondBasicValue(solution.getOtherValueList().getFirst());
      entity.getValueList().add(solution.getValueList().get(i));
    }
    return solution;
  }

  private static SolverScope<TestdataAllowsUnassignedValuesListSolution> optionalScope(
      int[][] assignments) {
    var scope =
        SolutionAssignmentMoveTest.<TestdataAllowsUnassignedValuesListSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                    TestdataAllowsUnassignedValuesListSolution.class,
                    TestdataAllowsUnassignedValuesListEntity.class,
                    TestdataAllowsUnassignedValuesListValue.class)
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                .withScoreDirectorFactory(
                    new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(OptionalScoreProvider.class)));
    var problem = TestdataAllowsUnassignedValuesListSolution.generateUninitializedSolution(4, 2);
    setLists(problem, assignments);
    scope.setInitialSolution(problem);
    return scope;
  }

  private static <Solution_> SolverScope<Solution_> scope(SolverConfig config) {
    return ((DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver())
        .getSolverScope();
  }

  private static void setLists(
      TestdataAllowsUnassignedValuesListSolution solution, int[][] assignments) {
    for (int i = 0; i < assignments.length; i++) {
      solution
          .getEntityList()
          .get(i)
          .setValueList(
              new ArrayList<>(
                  Arrays.stream(assignments[i]).mapToObj(solution.getValueList()::get).toList()));
    }
  }

  private static void assertOptionalState(
      InnerScoreDirector<TestdataAllowsUnassignedValuesListSolution, SimpleScore> director,
      int[][] assignments) {
    var working = director.getWorkingSolution();
    var count = 0;
    for (int entityIndex = 0; entityIndex < assignments.length; entityIndex++) {
      var entity = working.getEntityList().get(entityIndex);
      var expected =
          Arrays.stream(assignments[entityIndex]).mapToObj(working.getValueList()::get).toList();
      assertThat(entity.getValueList()).containsExactlyElementsOf(expected);
      count += expected.size();
      for (int i = 0; i < expected.size(); i++) {
        var value = expected.get(i);
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(i);
        assertThat(value.getPrevious()).isSameAs(i == 0 ? null : expected.get(i - 1));
        assertThat(value.getNext()).isSameAs(i + 1 == expected.size() ? null : expected.get(i + 1));
      }
    }
    for (var value : working.getValueList()) {
      if (working.getEntityList().stream()
          .noneMatch(entity -> entity.getValueList().contains(value))) {
        assertThat(value.getEntity()).isNull();
        assertThat(value.getIndex()).isNull();
        assertThat(value.getPrevious()).isNull();
        assertThat(value.getNext()).isNull();
      }
    }
    var variable = director.getSolutionDescriptor().getListVariableDescriptor();
    assertThat(director.getListVariableState(variable).getUnassignedCount()).isEqualTo(4 - count);
    var score = director.calculateScore();
    assertThat(score).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-4 - count)));
    director.assertWorkingScoreFromScratch(score, "Assignment transition");
  }

  public static final class OptionalScoreProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataAllowsUnassignedValuesListEntity.class)
            .penalize(SimpleScore.of(2), entity -> entity.getValueList().size())
            .asConstraint("Assigned values"),
        factory
            .forEachIncludingUnassigned(TestdataAllowsUnassignedValuesListValue.class)
            .filter(value -> value.getEntity() == null)
            .penalize(SimpleScore.ONE)
            .asConstraint("Unassigned values")
      };
    }
  }

  public static final class MixedScoreProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataMixedListElementEntity.class)
            .penalize(SimpleScore.ONE, TestdataMixedListElementEntity::getTotalDuration)
            .asConstraint("Duration")
      };
    }
  }

  private static final class EqualOtherValue extends TestdataUnassignedMixedOtherValue {
    private EqualOtherValue(String code) {
      super(code);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof TestdataUnassignedMixedOtherValue value
          && getCode().equals(value.getCode());
    }

    @Override
    public int hashCode() {
      return getCode().hashCode();
    }
  }
}
