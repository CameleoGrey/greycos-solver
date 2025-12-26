package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ai.greycos.solver.core.impl.heuristic.move.CompositeMove;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;

import org.junit.jupiter.api.Test;

class MultistageMoveSelectorTest {

  @Test
  void testConstructor_withEmptyStageSelectors_throwsException() {
    StageProvider<TestSolution> provider = mock(StageProvider.class);
    List<MoveSelector<TestSolution>> emptyList = List.of();

    assertThatThrownBy(() -> new MultistageMoveSelector<>(provider, emptyList, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Stage selectors list cannot be empty");
  }

  @Test
  void testGetSize_returnsProductOfStageSizes() {
    // Create mock stage selectors with specific sizes
    MoveSelector<TestSolution> stage1 =
        SelectorTestUtils.mockMoveSelector(createMockMove("move1a"), createMockMove("move1b"));
    MoveSelector<TestSolution> stage2 =
        SelectorTestUtils.mockMoveSelector(
            createMockMove("move2a"), createMockMove("move2b"), createMockMove("move2c"));
    MoveSelector<TestSolution> stage3 =
        SelectorTestUtils.mockMoveSelector(createMockMove("move3a"));

    StageProvider<TestSolution> provider = mock(StageProvider.class);
    when(provider.getStageCount()).thenReturn(3);

    MultistageMoveSelector<TestSolution> selector =
        new MultistageMoveSelector<>(provider, List.of(stage1, stage2, stage3), false);

    // Size should be product of all stage sizes: 2 * 3 * 1 = 6
    assertThat(selector.getSize()).isEqualTo(6L);
  }

  @Test
  void testIsNeverEnding_returnsTrueIfAnyStageNeverEnding() {
    MoveSelector<TestSolution> stage1 = mock(MoveSelector.class);
    when(stage1.isNeverEnding()).thenReturn(false);

    MoveSelector<TestSolution> stage2 = mock(MoveSelector.class);
    when(stage2.isNeverEnding()).thenReturn(true); // Never ending

    MoveSelector<TestSolution> stage3 = mock(MoveSelector.class);
    when(stage3.isNeverEnding()).thenReturn(false);

    StageProvider<TestSolution> provider = mock(StageProvider.class);

    MultistageMoveSelector<TestSolution> selector =
        new MultistageMoveSelector<>(provider, List.of(stage1, stage2, stage3), false);

    assertThat(selector.isNeverEnding()).isTrue();
  }

  @Test
  void testIsNeverEnding_returnsFalseIfAllStagesCountable() {
    MoveSelector<TestSolution> stage1 = mock(MoveSelector.class);
    when(stage1.isNeverEnding()).thenReturn(false);

    MoveSelector<TestSolution> stage2 = mock(MoveSelector.class);
    when(stage2.isNeverEnding()).thenReturn(false);

    StageProvider<TestSolution> provider = mock(StageProvider.class);

    MultistageMoveSelector<TestSolution> selector =
        new MultistageMoveSelector<>(provider, List.of(stage1, stage2), false);

    assertThat(selector.isNeverEnding()).isFalse();
  }

  @Test
  void testSequentialIterator_generatesAllCombinations() {
    // Stage 1 has 2 moves
    Move<TestSolution> move1a = createMockMove("1a");
    Move<TestSolution> move1b = createMockMove("1b");
    MoveSelector<TestSolution> stage1 = SelectorTestUtils.mockMoveSelector(move1a, move1b);

    // Stage 2 has 3 moves
    Move<TestSolution> move2a = createMockMove("2a");
    Move<TestSolution> move2b = createMockMove("2b");
    Move<TestSolution> move2c = createMockMove("2c");
    MoveSelector<TestSolution> stage2 = SelectorTestUtils.mockMoveSelector(move2a, move2b, move2c);

    StageProvider<TestSolution> provider = mock(StageProvider.class);

    MultistageMoveSelector<TestSolution> selector =
        new MultistageMoveSelector<>(provider, List.of(stage1, stage2), false);

    // Should generate 2 * 3 = 6 combinations
    List<Move<TestSolution>> generatedMoves = new ArrayList<>();
    selector.iterator().forEachRemaining(generatedMoves::add);

    assertThat(generatedMoves).hasSize(6);

    // All moves should be CompositeMoves
    for (Move<TestSolution> move : generatedMoves) {
      assertThat(move).isInstanceOf(CompositeMove.class);
    }
  }

  @Test
  void testRandomIterator_neverEnds() {
    MoveSelector<TestSolution> stage1 = createNeverEndingSelector();
    MoveSelector<TestSolution> stage2 = createNeverEndingSelector();

    StageProvider<TestSolution> provider = mock(StageProvider.class);

    MultistageMoveSelector<TestSolution> selector =
        new MultistageMoveSelector<>(provider, List.of(stage1, stage2), true);

    var iterator = selector.iterator();

    // Should be able to generate many moves without ending
    for (int i = 0; i < 100; i++) {
      assertThat(iterator.hasNext()).isTrue();
      Move<TestSolution> move = iterator.next();
      assertThat(move).isNotNull();
    }
  }

  @Test
  void testStepStarted_propagatesToStageSelectors() {
    MoveSelector<TestSolution> stage1 = mock(MoveSelector.class);
    MoveSelector<TestSolution> stage2 = mock(MoveSelector.class);

    StageProvider<TestSolution> provider = mock(StageProvider.class);

    MultistageMoveSelector<TestSolution> selector =
        new MultistageMoveSelector<>(provider, List.of(stage1, stage2), false);

    AbstractStepScope<TestSolution> stepScope = createMockStepScope();

    selector.stepStarted(stepScope);

    verify(stage1).stepStarted(stepScope);
    verify(stage2).stepStarted(stepScope);
  }

  @Test
  void testStepEnded_propagatesToStageSelectors() {
    MoveSelector<TestSolution> stage1 = mock(MoveSelector.class);
    MoveSelector<TestSolution> stage2 = mock(MoveSelector.class);

    StageProvider<TestSolution> provider = mock(StageProvider.class);

    MultistageMoveSelector<TestSolution> selector =
        new MultistageMoveSelector<>(provider, List.of(stage1, stage2), false);

    AbstractStepScope<TestSolution> stepScope = createMockStepScope();

    selector.stepEnded(stepScope);

    verify(stage1).stepEnded(stepScope);
    verify(stage2).stepEnded(stepScope);
  }

  @Test
  void testGetStageProvider_returnsProvidedProvider() {
    StageProvider<TestSolution> provider = mock(StageProvider.class);
    MoveSelector<TestSolution> stage = SelectorTestUtils.mockMoveSelector(createMockMove("move"));

    MultistageMoveSelector<TestSolution> selector =
        new MultistageMoveSelector<>(provider, List.of(stage), false);

    assertThat(selector.getStageProvider()).isSameAs(provider);
  }

  @Test
  void testGetStageSelectors_returnsImmutableList() {
    StageProvider<TestSolution> provider = mock(StageProvider.class);
    MoveSelector<TestSolution> stage = SelectorTestUtils.mockMoveSelector(createMockMove("move"));

    MultistageMoveSelector<TestSolution> selector =
        new MultistageMoveSelector<>(provider, List.of(stage), false);

    List<MoveSelector<TestSolution>> stageSelectors = selector.getStageSelectors();

    // The returned list should be immutable (List.copyOf creates an immutable list)
    assertThatThrownBy(() -> stageSelectors.add(mock(MoveSelector.class)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testCompositeMove_containsMovesFromAllStages() {
    Move<TestSolution> move1 = createMockMove("stage1");
    Move<TestSolution> move2 = createMockMove("stage2");

    MoveSelector<TestSolution> stage1 = SelectorTestUtils.mockMoveSelector(move1);
    MoveSelector<TestSolution> stage2 = SelectorTestUtils.mockMoveSelector(move2);

    StageProvider<TestSolution> provider = mock(StageProvider.class);

    MultistageMoveSelector<TestSolution> selector =
        new MultistageMoveSelector<>(provider, List.of(stage1, stage2), false);

    Move<TestSolution> compositeMove = selector.iterator().next();

    assertThat(compositeMove).isInstanceOf(CompositeMove.class);
    // The composite move should execute both stage moves
  }

  // ************************************************************************
  // Helper methods
  // ************************************************************************

  private Move<TestSolution> createMockMove(String name) {
    Move<TestSolution> move = mock(Move.class);
    when(move.toString()).thenReturn(name);
    return move;
  }

  private MoveSelector<TestSolution> createNeverEndingSelector() {
    MoveSelector<TestSolution> selector = mock(MoveSelector.class);
    when(selector.isNeverEnding()).thenReturn(true);
    when(selector.iterator())
        .thenAnswer(
            invocation ->
                new java.util.Iterator<>() {
                  private int count = 0;

                  @Override
                  public boolean hasNext() {
                    return true;
                  }

                  @Override
                  public Move<TestSolution> next() {
                    return createMockMove("move" + (count++));
                  }
                });
    return selector;
  }

  private AbstractStepScope<TestSolution> createMockStepScope() {
    AbstractStepScope<TestSolution> stepScope = mock(AbstractStepScope.class);
    when(stepScope.getWorkingRandom()).thenReturn(new Random(12345L));
    return stepScope;
  }

  private static class TestSolution {
    // Test solution class
  }
}
