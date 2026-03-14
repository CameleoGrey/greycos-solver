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

class SelectorBasedSubListChangeMoveTest {

  private final TestdataListValue v1 = new TestdataListValue("1");
  private final TestdataListValue v2 = new TestdataListValue("2");
  private final TestdataListValue v3 = new TestdataListValue("3");
  private final TestdataListValue v4 = new TestdataListValue("4");
  private final TestdataListValue v5 = new TestdataListValue("5");
  private final TestdataListValue v6 = new TestdataListValue("6");

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
            new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 1, 2, e1, 1, false)
                .isMoveDoable(scoreDirector))
        .isFalse();
    assertThat(
            new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 1, 2, e1, 0, false)
                .isMoveDoable(scoreDirector))
        .isTrue();
    assertThat(
            new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 0, 3, e1, 1, false)
                .isMoveDoable(scoreDirector))
        .isTrue();
    assertThat(
            new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 0, 3, e1, 2, false)
                .isMoveDoable(scoreDirector))
        .isFalse();
    assertThat(
            new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 1, 2, e2, 0, false)
                .isMoveDoable(scoreDirector))
        .isTrue();
  }

  @Test
  void isMoveDoableValueRangeProviderOnEntity() {
    var value1 = new TestdataListEntityProvidingValue("1");
    var value2 = new TestdataListEntityProvidingValue("2");
    var value3 = new TestdataListEntityProvidingValue("3");
    var value4 = new TestdataListEntityProvidingValue("4");
    var entity1 =
        new TestdataListEntityProvidingEntity(
            "e1", List.of(value1, value2, value4), List.of(value1, value4, value2));
    var entity2 =
        new TestdataListEntityProvidingEntity(
            "e2", List.of(value1, value3, value4), List.of(value3));
    var solution = new TestdataListEntityProvidingSolution();
    solution.setEntityList(List.of(entity1, entity2));
    valueRangeManager.reset(solution);

    assertThat(
            new SelectorBasedSubListChangeMove<>(
                    otherVariableDescriptor, entity1, 0, 2, entity2, 0, false)
                .isMoveDoable(otherInnerScoreDirector))
        .isTrue();
    assertThat(
            new SelectorBasedSubListChangeMove<>(
                    otherVariableDescriptor, entity1, 0, 2, entity2, 0, true)
                .isMoveDoable(otherInnerScoreDirector))
        .isTrue();
    assertThat(
            new SelectorBasedSubListChangeMove<>(
                    otherVariableDescriptor, entity1, 0, 3, entity2, 0, false)
                .isMoveDoable(otherInnerScoreDirector))
        .isFalse();
    assertThat(
            new SelectorBasedSubListChangeMove<>(
                    otherVariableDescriptor, entity1, 0, 3, entity2, 0, true)
                .isMoveDoable(otherInnerScoreDirector))
        .isFalse();
    assertThat(
            new SelectorBasedSubListChangeMove<>(
                    otherVariableDescriptor, entity1, 1, 2, entity2, 0, false)
                .isMoveDoable(otherInnerScoreDirector))
        .isFalse();
    assertThat(
            new SelectorBasedSubListChangeMove<>(
                    otherVariableDescriptor, entity1, 1, 2, entity2, 0, true)
                .isMoveDoable(otherInnerScoreDirector))
        .isFalse();
  }

  @Test
  void doMove() {
    var e1 = new TestdataListEntity("e1", v1, v2, v3, v4);
    var e2 = new TestdataListEntity("e2", v5);

    var move = new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 1, 2, e2, 0, false);

    move.doMoveOnly(scoreDirector);

    assertThat(e1.getValueList()).containsExactly(v1, v4);
    assertThat(e2.getValueList()).containsExactly(v2, v3, v5);

    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e1, 1, 3);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e1, 1, 1);
    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e2, 0, 0);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e2, 0, 2);
    verify(scoreDirector, atLeastOnce()).triggerVariableListeners();
  }

  @Test
  void doReversingMove() {
    var e1 = new TestdataListEntity("e1", v1, v2, v3, v4);
    var e2 = new TestdataListEntity("e2", v5);

    var move = new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 0, 3, e2, 1, true);

    move.doMoveOnly(scoreDirector);

    assertThat(e1.getValueList()).containsExactly(v4);
    assertThat(e2.getValueList()).containsExactly(v5, v3, v2, v1);

    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e1, 0, 3);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e1, 0, 0);
    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e2, 1, 1);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e2, 1, 4);
    verify(scoreDirector, atLeastOnce()).triggerVariableListeners();
  }

  @Test
  void doMoveOnSameEntity() {
    var e1 = new TestdataListEntity("e1", v1, v2, v3, v4, v5, v6);

    var move = new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 3, 2, e1, 0, false);

    move.doMoveOnly(scoreDirector);

    assertThat(e1.getValueList()).containsExactly(v4, v5, v1, v2, v3, v6);

    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e1, 0, 5);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e1, 0, 5);
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

    var sourceIndex = 3;
    var length = 5;
    var destinationIndex = 7;
    var reversing = false;
    assertSameProperties(
        destinationE1,
        sourceIndex,
        length,
        destinationE2,
        destinationIndex,
        reversing,
        new SelectorBasedSubListChangeMove<>(
                variableDescriptor, e1, sourceIndex, length, e2, destinationIndex, reversing)
            .rebase(destinationScoreDirector));
  }

  static void assertSameProperties(
      Object sourceEntity,
      int fromIndex,
      int length,
      Object destinationEntity,
      int destinationIndex,
      boolean reversing,
      SelectorBasedSubListChangeMove<?> move) {
    assertThat(move.getSourceEntity()).isSameAs(sourceEntity);
    assertThat(move.getFromIndex()).isEqualTo(fromIndex);
    assertThat(move.getSubListSize()).isEqualTo(length);
    assertThat(move.getDestinationEntity()).isSameAs(destinationEntity);
    assertThat(move.getDestinationIndex()).isEqualTo(destinationIndex);
    assertThat(move.isReversing()).isEqualTo(reversing);
  }

  @Test
  void tabuIntrospection_twoEntities() {
    var e1 = new TestdataListEntity("e1", v1, v2, v3, v4);
    var e2 = new TestdataListEntity("e2", v5);
    var e3 = new TestdataListEntity("e3");

    var moveTwoEntities =
        new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 1, 3, e2, 0, false);
    moveTwoEntities.doMoveOnly(scoreDirector);
    assertThat(moveTwoEntities.getPlanningEntities()).containsExactly(e1, e2);
    assertThat(moveTwoEntities.getPlanningValues()).containsExactly(v2, v3, v4);

    assertThat(new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 1, 3, e2, 0, true))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 1, 3, e2, 1, false))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 1, 3, e3, 0, false))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 1, 2, e2, 0, false))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 2, 4, e2, 0, false))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListChangeMove<>(variableDescriptor, e3, 1, 3, e2, 0, false))
        .isNotEqualTo(moveTwoEntities);
    assertThat(new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 1, 3, e2, 0, false))
        .isEqualTo(moveTwoEntities);
  }

  @Test
  void tabuIntrospection_oneEntity() {
    var e1 = new TestdataListEntity("e1", v1, v2, v3, v4);

    var moveOneEntity =
        new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 0, 2, e1, 2, false);
    moveOneEntity.doMoveOnly(scoreDirector);
    assertThat(moveOneEntity.getPlanningEntities()).containsExactly(e1);
    assertThat(moveOneEntity.getPlanningValues()).containsExactly(v1, v2);
  }

  @Test
  void toStringTest() {
    var e1 = new TestdataListEntity("e1", v1, v2, v3, v4);
    var e2 = new TestdataListEntity("e2", v5);

    assertThat(new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 1, 3, e1, 0, false))
        .hasToString("|3| {e1[1..4] -> e1[0]}");
    assertThat(new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 0, 1, e2, 1, false))
        .hasToString("|1| {e1[0..1] -> e2[1]}");
    assertThat(new SelectorBasedSubListChangeMove<>(variableDescriptor, e1, 0, 1, e2, 1, true))
        .hasToString("|1| {e1[0..1] -reversing-> e2[1]}");
  }
}
