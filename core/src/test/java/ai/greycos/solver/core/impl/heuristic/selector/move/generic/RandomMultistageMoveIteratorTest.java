package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import ai.greycos.solver.core.impl.heuristic.move.CompositeMove;
import ai.greycos.solver.core.impl.heuristic.move.DummyMove;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;

class RandomMultistageMoveIteratorTest {

  @Test
  void generatesValidMoves() {
    MoveSelector<TestdataSolution> s1 =
        SelectorTestUtils.mockMoveSelector(new DummyMove("A"), new DummyMove("B"));
    MoveSelector<TestdataSolution> s2 =
        SelectorTestUtils.mockMoveSelector(new DummyMove("1"), new DummyMove("2"));

    // Each iteration needs 2 random values (one per stage)
    var iterator =
        new RandomMultistageMoveIterator<>(List.of(s1, s2), new TestRandom(0, 0, 1, 1, 0, 1, 1, 0));

    // Generate 4 moves with predictable random sequence
    for (int i = 0; i < 4; i++) {
      assertThat(iterator.hasNext()).isTrue();
      CompositeMove<TestdataSolution> move = (CompositeMove<TestdataSolution>) iterator.next();
      assertThat(move.getMoves()).hasSize(2);
      assertThat(((DummyMove) move.getMoves()[0]).getCode()).isIn("A", "B");
      assertThat(((DummyMove) move.getMoves()[1]).getCode()).isIn("1", "2");
    }
  }

  @Test
  void neverEnding() {
    MoveSelector<TestdataSolution> s1 = SelectorTestUtils.mockMoveSelector(new DummyMove("A"));
    MoveSelector<TestdataSolution> s2 = SelectorTestUtils.mockMoveSelector(new DummyMove("1"));

    var iterator = new RandomMultistageMoveIterator<>(List.of(s1, s2), new Random());

    // Random iterator should always have next
    for (int i = 0; i < 1000; i++) {
      assertThat(iterator.hasNext()).isTrue();
    }
  }

  @Test
  void cachesSmallStages() {
    // Create selector with < 1000 moves ( CACHE_THRESHOLD )
    List<DummyMove> moves = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      moves.add(new DummyMove("m" + i));
    }

    MoveSelector<TestdataSolution> selector =
        SelectorTestUtils.mockMoveSelector(moves.toArray(new DummyMove[0]));

    var iterator = new RandomMultistageMoveIterator<>(List.of(selector), new TestRandom(50, 50));

    // First call - single stage returns DummyMove directly
    DummyMove move1 = (DummyMove) iterator.next();
    assertThat(move1).isNotNull();

    // Second call with same random should return same move due to caching
    DummyMove move2 = (DummyMove) iterator.next();
    assertThat(move2).isNotNull();

    // With deterministic random (50, 50), both should return the same element
    assertThat(move1.getCode()).isEqualTo(move2.getCode());
  }

  @Test
  void skipsLargeStages() {
    // Create selector with > 1000 moves ( CACHE_THRESHOLD )
    List<DummyMove> moves = new ArrayList<>();
    for (int i = 0; i < 2000; i++) {
      moves.add(new DummyMove("m" + i));
    }

    MoveSelector<TestdataSolution> selector =
        SelectorTestUtils.mockMoveSelector(moves.toArray(new DummyMove[0]));

    var iterator = new RandomMultistageMoveIterator<>(List.of(selector), new Random());

    // Should not throw OutOfMemoryError (uses skip-ahead instead of caching)
    // Single stage returns DummyMove directly
    DummyMove move = (DummyMove) iterator.next();
    assertThat(move).isNotNull();
  }

  @Test
  void neverEndingStageHandledCorrectly() {
    MoveSelector<TestdataSolution> finite =
        SelectorTestUtils.mockMoveSelector(new DummyMove("A"), new DummyMove("B"));

    MoveSelector<TestdataSolution> infinite = mock(MoveSelector.class);
    when(infinite.isNeverEnding()).thenReturn(true);
    when(infinite.iterator()).thenReturn((Iterator) List.of(new DummyMove("X")).iterator());

    var iterator = new RandomMultistageMoveIterator<>(List.of(finite, infinite), new Random());

    // Should generate moves without hanging
    CompositeMove<TestdataSolution> move = (CompositeMove<TestdataSolution>) iterator.next();
    assertThat(move.getMoves()).hasSize(2);
  }

  @Test
  void threeStageRandomGeneration() {
    MoveSelector<TestdataSolution> s1 = SelectorTestUtils.mockMoveSelector(new DummyMove("A"));
    MoveSelector<TestdataSolution> s2 =
        SelectorTestUtils.mockMoveSelector(new DummyMove("1"), new DummyMove("2"));
    MoveSelector<TestdataSolution> s3 =
        SelectorTestUtils.mockMoveSelector(new DummyMove("X"), new DummyMove("Y"));

    var iterator = new RandomMultistageMoveIterator<>(List.of(s1, s2, s3), new TestRandom(0, 0, 0));

    CompositeMove<TestdataSolution> move = (CompositeMove<TestdataSolution>) iterator.next();

    assertThat(move.getMoves()).hasSize(3);
    assertThat(((DummyMove) move.getMoves()[0]).getCode()).isEqualTo("A");
    assertThat(((DummyMove) move.getMoves()[1]).getCode()).isEqualTo("1");
    assertThat(((DummyMove) move.getMoves()[2]).getCode()).isEqualTo("X");
  }

  @Test
  void singleStageRandomGeneration() {
    MoveSelector<TestdataSolution> s1 =
        SelectorTestUtils.mockMoveSelector(new DummyMove("A"), new DummyMove("B"));

    var iterator = new RandomMultistageMoveIterator<>(List.of(s1), new TestRandom(0));

    // Single stage returns DummyMove directly
    DummyMove move = (DummyMove) iterator.next();

    assertThat(move.getCode()).isIn("A", "B");
  }
}
