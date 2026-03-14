package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static ai.greycos.solver.core.testutil.PlannerTestUtils.mockRebasingScoreDirector;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.score.director.ValueRangeManager;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;
import ai.greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import ai.greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import ai.greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SelectorBasedListChangeMoveTest {

  private final TestdataListValue v0 = new TestdataListValue("0");
  private final TestdataListValue v1 = new TestdataListValue("1");
  private final TestdataListValue v2 = new TestdataListValue("2");
  private final TestdataListValue v3 = new TestdataListValue("3");
  private final TestdataListValue v4 = new TestdataListValue("4");

  private final InnerScoreDirector<TestdataListSolution, ?> scoreDirector =
      mock(InnerScoreDirector.class);
  private final ListVariableDescriptor<TestdataListSolution> variableDescriptor =
      TestdataListEntity.buildVariableDescriptorForValueList();
  private final InnerScoreDirector<TestdataListEntityProvidingSolution, ?> otherInnerScoreDirector =
      mock(InnerScoreDirector.class);
  private final ListVariableDescriptor<TestdataListEntityProvidingSolution>
      otherVariableDescriptor =
          TestdataListEntityProvidingEntity.buildVariableDescriptorForValueList();

  @BeforeEach
  void setUp() {
    when(otherInnerScoreDirector.getValueRangeManager())
        .thenReturn(
            new ValueRangeManager<>(
                otherVariableDescriptor.getEntityDescriptor().getSolutionDescriptor()));
  }

  @Test
  void isMoveDoable() {
    var e1 = new TestdataListEntity("e1", v1, v2);
    var e2 = new TestdataListEntity("e2", v3);

    assertThat(
            new SelectorBasedListChangeMove<>(variableDescriptor, e1, 1, e1, 1)
                .isMoveDoable(scoreDirector))
        .isFalse();
    assertThat(
            new SelectorBasedListChangeMove<>(variableDescriptor, e1, 0, e1, 1)
                .isMoveDoable(scoreDirector))
        .isTrue();
    assertThat(
            new SelectorBasedListChangeMove<>(variableDescriptor, e1, 0, e1, 2)
                .isMoveDoable(scoreDirector))
        .isFalse();
    assertThat(
            new SelectorBasedListChangeMove<>(variableDescriptor, e1, 0, e2, 0)
                .isMoveDoable(scoreDirector))
        .isTrue();
  }

  @Disabled("Temporarily disabled")
  @Test
  void isMoveDoableValueRangeProviderOnEntity() {
    var value1 = new TestdataListEntityProvidingValue("1");
    var value2 = new TestdataListEntityProvidingValue("2");
    var value3 = new TestdataListEntityProvidingValue("3");
    var entity1 =
        new TestdataListEntityProvidingEntity(
            "e1", List.of(value1, value2), List.of(value1, value2));
    var entity2 =
        new TestdataListEntityProvidingEntity("e2", List.of(value1, value3), List.of(value3));
    assertThat(
            new SelectorBasedListChangeMove<>(otherVariableDescriptor, entity1, 0, entity2, 0)
                .isMoveDoable(otherInnerScoreDirector))
        .isTrue();
    assertThat(
            new SelectorBasedListChangeMove<>(otherVariableDescriptor, entity1, 1, entity2, 0)
                .isMoveDoable(otherInnerScoreDirector))
        .isFalse();
  }

  @Test
  void doMove() {
    var e1 = new TestdataListEntity("e1", v1, v2);
    var e2 = new TestdataListEntity("e2", v3);

    var move = new SelectorBasedListChangeMove<>(variableDescriptor, e1, 1, e2, 1);

    move.doMoveOnly(scoreDirector);

    assertThat(e1.getValueList()).containsExactly(v1);
    assertThat(e2.getValueList()).containsExactly(v3, v2);

    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e1, 1, 2);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e1, 1, 1);
    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e2, 1, 1);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e2, 1, 2);
    verify(scoreDirector, atLeastOnce()).triggerVariableListeners();
  }

  static Stream<Arguments> doAndUndoMoveOnTheSameEntity() {
    return Stream.of(
        arguments(0, asList("2", "0", "1", "3", "4"), 0, 3),
        arguments(1, asList("0", "2", "1", "3", "4"), 1, 3),
        arguments(2, null, -1, -1),
        arguments(3, asList("0", "1", "3", "2", "4"), 2, 4),
        arguments(4, asList("0", "1", "3", "4", "2"), 2, 5),
        arguments(5, null, -1, -1));
  }

  @ParameterizedTest
  @MethodSource
  void doAndUndoMoveOnTheSameEntity(
      int destinationIndex, List<String> expectedValueList, int fromIndex, int toIndex) {
    final var sourceIndex = 2;
    var e = new TestdataListEntity("E", v0, v1, v2, v3, v4);

    var move =
        new SelectorBasedListChangeMove<>(variableDescriptor, e, sourceIndex, e, destinationIndex);

    if (expectedValueList == null) {
      assertThat(move.isMoveDoable(scoreDirector)).isFalse();
      return;
    }

    assertThat(move.isMoveDoable(scoreDirector)).isTrue();
    move.doMoveOnly(scoreDirector);
    assertThat(e.getValueList().indexOf(v2)).isEqualTo(destinationIndex);
    assertThat((TestdataListValue) variableDescriptor.getElement(e, destinationIndex))
        .isEqualTo(v2);
    assertThat(e.getValueList()).map(TestdataObject::toString).isEqualTo(expectedValueList);

    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e, fromIndex, toIndex);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e, fromIndex, toIndex);
    verify(scoreDirector, atLeastOnce()).triggerVariableListeners();
  }

  @Test
  void rebase() {
    var e1 = new TestdataListEntity("e1", v1, v2);
    var e2 = new TestdataListEntity("e2", v3);

    var destinationV1 = new TestdataListValue("1");
    var destinationV2 = new TestdataListValue("2");
    var destinationV3 = new TestdataListValue("3");
    var destinationE1 = new TestdataListEntity("e1", destinationV1, destinationV2);
    var destinationE2 = new TestdataListEntity("e2", destinationV3);

    var destinationScoreDirector =
        mockRebasingScoreDirector(
            variableDescriptor.getEntityDescriptor().getSolutionDescriptor(),
            new Object[][] {
              {v1, destinationV1},
              {v2, destinationV2},
              {v3, destinationV3},
              {e1, destinationE1},
              {e2, destinationE2},
            });

    assertSameProperties(
        destinationE1,
        0,
        destinationV1,
        destinationE2,
        1,
        new SelectorBasedListChangeMove<>(variableDescriptor, e1, 0, e2, 1)
            .rebase(destinationScoreDirector));
    assertSameProperties(
        destinationE2,
        0,
        destinationV3,
        destinationE2,
        0,
        new SelectorBasedListChangeMove<>(variableDescriptor, e2, 0, e2, 0)
            .rebase(destinationScoreDirector));
  }

  static void assertSameProperties(
      Object sourceEntity,
      int sourceIndex,
      Object movedValue,
      Object destinationEntity,
      int destinationIndex,
      SelectorBasedListChangeMove<?> move) {
    assertThat(move.getSourceEntity()).isSameAs(sourceEntity);
    assertThat(move.getSourceIndex()).isEqualTo(sourceIndex);
    assertThat(move.getMovedValue()).isSameAs(movedValue);
    assertThat(move.getDestinationEntity()).isSameAs(destinationEntity);
    assertThat(move.getDestinationIndex()).isEqualTo(destinationIndex);
  }

  @Test
  void tabuIntrospection_twoEntities() {
    var e1 = new TestdataListEntity("e1", v1, v2);
    var e2 = new TestdataListEntity("e2", v3);

    var moveTwoEntities = new SelectorBasedListChangeMove<>(variableDescriptor, e1, 1, e2, 1);
    moveTwoEntities.doMoveOnly(scoreDirector);
    assertThat(moveTwoEntities.getPlanningEntities()).containsExactly(e1, e2);
    assertThat(moveTwoEntities.getPlanningValues()).containsExactly(v2);
  }

  @Test
  void tabuIntrospection_oneEntity() {
    var e1 = new TestdataListEntity("e1", v1, v2);

    var moveOneEntity = new SelectorBasedListChangeMove<>(variableDescriptor, e1, 0, e1, 1);
    moveOneEntity.doMoveOnly(scoreDirector);
    assertThat(moveOneEntity.getPlanningEntities()).containsExactly(e1);
    assertThat(moveOneEntity.getPlanningValues()).containsExactly(v1);
  }

  @Test
  void toStringTest() {
    var e1 = new TestdataListEntity("e1", v1, v2);
    var e2 = new TestdataListEntity("e2", v3);

    assertThat(new SelectorBasedListChangeMove<>(variableDescriptor, e1, 1, e1, 0))
        .hasToString("2 {e1[1] -> e1[0]}");
    assertThat(new SelectorBasedListChangeMove<>(variableDescriptor, e1, 0, e2, 1))
        .hasToString("1 {e1[0] -> e2[1]}");
  }

  @Test
  void testEnableNearbyMixedModel() {
    var moveSelectorConfig = new ListChangeMoveSelectorConfig();
    assertThat(moveSelectorConfig.canEnableNearbyInMixedModels()).isTrue();
  }
}
