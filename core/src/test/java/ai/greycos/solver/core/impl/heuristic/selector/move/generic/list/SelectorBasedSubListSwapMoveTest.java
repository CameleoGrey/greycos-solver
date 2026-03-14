package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static ai.greycos.solver.core.testutil.PlannerTestUtils.mockRebasingScoreDirector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.score.director.ValueRangeManager;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;
import ai.greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import ai.greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import ai.greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelectorBasedSubListSwapMoveTest {

  private final TestdataListValue v1 = new TestdataListValue("1");
  private final TestdataListValue v2 = new TestdataListValue("2");
  private final TestdataListValue v3 = new TestdataListValue("3");
  private final TestdataListValue v4 = new TestdataListValue("4");
  private final TestdataListValue v5 = new TestdataListValue("5");
  private final TestdataListValue v6 = new TestdataListValue("6");
  private final TestdataListValue v7 = new TestdataListValue("7");

  private final InnerScoreDirector<TestdataListSolution, ?> scoreDirector =
      mock(InnerScoreDirector.class);
  private final ListVariableDescriptor<TestdataListSolution> variableDescriptor =
      TestdataListEntity.buildVariableDescriptorForValueList();
  private final InnerScoreDirector<TestdataListEntityProvidingSolution, ?> otherInnerScoreDirector =
      mock(InnerScoreDirector.class);

  private final ListVariableDescriptor<TestdataListEntityProvidingSolution>
      otherVariableDescriptor =
          TestdataListEntityProvidingEntity.buildVariableDescriptorForValueList();
  private final ValueRangeManager<TestdataListEntityProvidingSolution> valueRangeManager =
      new ValueRangeManager<>(TestdataListEntityProvidingSolution.buildSolutionDescriptor());

  @BeforeEach
  void setUp() {
    when(otherInnerScoreDirector.getValueRangeManager()).thenReturn(valueRangeManager);
  }

  @Test
  void isMoveDoable() {
    var e1 = new TestdataListEntity("e1", v1, v2, v3, v4);
    var e2 = new TestdataListEntity("e2", v5);

    assertThat(
            new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 3, e1, 2, 5, false)
                .isMoveDoable(scoreDirector))
        .isFalse();
    assertThat(
            new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 5, e1, 1, 2, false)
                .isMoveDoable(scoreDirector))
        .isFalse();
    assertThat(
            new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 1, e1, 4, 5, false)
                .isMoveDoable(scoreDirector))
        .isTrue();
    assertThat(
            new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 3, e1, 3, 5, false)
                .isMoveDoable(scoreDirector))
        .isTrue();
    assertThat(
            new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 2, 5, e1, 0, 2, false)
                .isMoveDoable(scoreDirector))
        .isTrue();
    assertThat(
            new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 5, e2, 0, 1, false)
                .isMoveDoable(scoreDirector))
        .isTrue();
  }

  @Test
  void isMoveDoableValueRangeProviderOnEntity() {
    var value1 = new TestdataListEntityProvidingValue("1");
    var value2 = new TestdataListEntityProvidingValue("2");
    var value3 = new TestdataListEntityProvidingValue("3");
    var value4 = new TestdataListEntityProvidingValue("4");
    var value5 = new TestdataListEntityProvidingValue("5");
    var entity1 =
        new TestdataListEntityProvidingEntity(
            "e1", List.of(value1, value2, value3), List.of(value1, value4, value2));
    var entity2 =
        new TestdataListEntityProvidingEntity(
            "e2", List.of(value1, value3, value4, value5), List.of(value3, value5));
    var solution = new TestdataListEntityProvidingSolution();
    solution.setEntityList(List.of(entity1, entity2));
    valueRangeManager.reset(solution);

    assertThat(
            new SelectorBasedSubListSwapMove<>(
                    otherVariableDescriptor, entity1, 0, 2, entity2, 0, 1, false)
                .isMoveDoable(otherInnerScoreDirector))
        .isTrue();
    assertThat(
            new SelectorBasedSubListSwapMove<>(
                    otherVariableDescriptor, entity1, 0, 2, entity2, 0, 1, true)
                .isMoveDoable(otherInnerScoreDirector))
        .isTrue();
    assertThat(
            new SelectorBasedSubListSwapMove<>(
                    otherVariableDescriptor, entity1, 0, 3, entity2, 0, 1, false)
                .isMoveDoable(otherInnerScoreDirector))
        .isFalse();
    assertThat(
            new SelectorBasedSubListSwapMove<>(
                    otherVariableDescriptor, entity1, 0, 3, entity2, 0, 1, true)
                .isMoveDoable(otherInnerScoreDirector))
        .isFalse();
    assertThat(
            new SelectorBasedSubListSwapMove<>(
                    otherVariableDescriptor, entity1, 0, 2, entity2, 0, 2, false)
                .isMoveDoable(otherInnerScoreDirector))
        .isFalse();
    assertThat(
            new SelectorBasedSubListSwapMove<>(
                    otherVariableDescriptor, entity1, 0, 2, entity2, 0, 2, true)
                .isMoveDoable(otherInnerScoreDirector))
        .isFalse();
  }

  @Test
  void doMove() {
    var e1 = new TestdataListEntity("e1", v1, v2, v3, v4);
    var e2 = new TestdataListEntity("e2", v5);

    var move = new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 1, 3, e2, 0, 1, false);

    move.doMoveOnly(scoreDirector);

    assertThat(e1.getValueList()).containsExactly(v1, v5, v4);
    assertThat(e2.getValueList()).containsExactly(v2, v3);

    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e1, 1, 3);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e1, 1, 2);
    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e2, 0, 1);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e2, 0, 2);
    verify(scoreDirector, atLeastOnce()).triggerVariableListeners();
  }

  @Test
  void doReversingMove() {
    var e1 = new TestdataListEntity("e1", v1, v2, v3, v4);
    var e2 = new TestdataListEntity("e2", v5, v6);

    var move = new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 3, e2, 0, 2, true);

    move.doMoveOnly(scoreDirector);

    assertThat(e1.getValueList()).containsExactly(v6, v5, v4);
    assertThat(e2.getValueList()).containsExactly(v3, v2, v1);

    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e1, 0, 3);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e1, 0, 2);
    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e2, 0, 2);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e2, 0, 3);
    verify(scoreDirector, atLeastOnce()).triggerVariableListeners();
  }

  @Test
  void doMoveOnSameEntity() {
    var e1 = new TestdataListEntity("e1", v1, v2, v3, v4, v5, v6, v7);

    var move = new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 1, e1, 4, 7, false);

    move.doMoveOnly(scoreDirector);

    assertThat(e1.getValueList()).containsExactly(v5, v6, v7, v2, v3, v4, v1);

    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e1, 0, 7);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e1, 0, 7);
    verify(scoreDirector, atLeastOnce()).triggerVariableListeners();
  }

  @Test
  void rebase() {
    var e1 = new TestdataListEntity("e1");
    var e2 = new TestdataListEntity("e2");

    var destinationE1 = new TestdataListEntity("e1");
    var destinationE2 = new TestdataListEntity("e2");

    var destinationScoreDirector =
        mockRebasingScoreDirector(
            variableDescriptor.getEntityDescriptor().getSolutionDescriptor(),
            new Object[][] {
              {e1, destinationE1},
              {e2, destinationE2},
            });

    var reversing = false;
    var leftFromIndex = 7;
    var leftToIndex = 11;
    var rightFromIndex = 3;
    var rightToIndex = 9;
    assertSameProperties(
        destinationE1,
        leftFromIndex,
        leftToIndex,
        destinationE2,
        rightFromIndex,
        rightToIndex,
        reversing,
        new SelectorBasedSubListSwapMove<>(
                variableDescriptor,
                e1,
                leftFromIndex,
                leftToIndex,
                e2,
                rightFromIndex,
                rightToIndex,
                reversing)
            .rebase(destinationScoreDirector));
  }

  static void assertSameProperties(
      Object leftEntity,
      int leftFromIndex,
      int leftToIndex,
      Object rightEntity,
      int rightFromIndex,
      int rightToIndex,
      boolean reversing,
      SelectorBasedSubListSwapMove<?> move) {
    var leftSubList = move.getLeftSubList();
    assertThat(leftSubList.entity()).isSameAs(leftEntity);
    assertThat(leftSubList.fromIndex()).isEqualTo(leftFromIndex);
    assertThat(leftSubList.getToIndex()).isEqualTo(leftToIndex);
    var rightSubList = move.getRightSubList();
    assertThat(rightSubList.entity()).isSameAs(rightEntity);
    assertThat(rightSubList.fromIndex()).isEqualTo(rightFromIndex);
    assertThat(rightSubList.getToIndex()).isEqualTo(rightToIndex);
    assertThat(move.isReversing()).isEqualTo(reversing);
  }

  @Test
  void tabuIntrospection_twoEntities() {
    var e1 = new TestdataListEntity("e1", v1, v2, v3, v4);
    var e2 = new TestdataListEntity("e2", v5);
    var e3 = new TestdataListEntity("e3");

    var moveTwoEntities =
        new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 3, e2, 0, 1, false);
    moveTwoEntities.doMoveOnly(scoreDirector);
    assertThat(moveTwoEntities.getPlanningEntities()).containsExactly(e1, e2);
    assertThat(moveTwoEntities.getPlanningValues()).containsExactly(v1, v2, v3, v5);

    assertThat(new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 3, e2, 0, 1, true))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 3, e2, 0, 2, false))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 3, e2, 1, 1, false))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 3, e3, 0, 1, false))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 1, 4, e2, 0, 1, false))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 1, 3, e2, 0, 1, false))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListSwapMove<>(variableDescriptor, e3, 0, 3, e2, 0, 1, false))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 3, e2, 0, 1, false))
        .isEqualTo(moveTwoEntities);
  }

  @Test
  void tabuIntrospection_oneEntity() {
    var e1 = new TestdataListEntity("e1", v1, v2, v3, v4);

    var moveOneEntity =
        new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 3, 4, e1, 0, 2, false);
    moveOneEntity.doMoveOnly(scoreDirector);
    assertThat(moveOneEntity.getPlanningEntities()).containsExactly(e1);
    assertThat(moveOneEntity.getPlanningValues()).containsExactly(v1, v2, v4);

    assertThat(new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 2, e1, 3, 4, false))
        .isEqualTo(moveOneEntity);
  }

  @Test
  void toStringTest() {
    var e1 = new TestdataListEntity("e1");
    var e2 = new TestdataListEntity("e2");

    assertThat(new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 1, 4, e1, 0, 1, false))
        .hasToString("{e1[0..1]} <-> {e1[1..4]}");
    assertThat(new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 1, e2, 1, 6, false))
        .hasToString("{e1[0..1]} <-> {e2[1..6]}");
    assertThat(new SelectorBasedSubListSwapMove<>(variableDescriptor, e1, 0, 1, e2, 1, 6, true))
        .hasToString("{e1[0..1]} <-reversing-> {e2[1..6]}");
  }
}
