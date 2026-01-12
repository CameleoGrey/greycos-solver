package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import ai.greycos.solver.core.impl.heuristic.move.CompositeMove;
import ai.greycos.solver.core.impl.heuristic.move.DummyMove;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.testdomain.TestdataSolution;

import org.junit.jupiter.api.Test;

class SequentialMultistageMoveIteratorTest {

  @Test
  void twoStageCartesianProduct() {
    DummyMove a = new DummyMove("A");
    DummyMove b = new DummyMove("B");
    DummyMove one = new DummyMove("1");
    DummyMove two = new DummyMove("2");

    MoveSelector<TestdataSolution> s1 = SelectorTestUtils.mockMoveSelector(a, b);
    MoveSelector<TestdataSolution> s2 = SelectorTestUtils.mockMoveSelector(one, two);

    var iterator = new SequentialMultistageMoveIterator<>(List.of(s1, s2));

    assertThat(iterator.hasNext()).isTrue();

    CompositeMove<TestdataSolution> move1 = (CompositeMove<TestdataSolution>) iterator.next();
    assertThat(move1.getMoves()).containsExactly(a, one);

    CompositeMove<TestdataSolution> move2 = (CompositeMove<TestdataSolution>) iterator.next();
    assertThat(move2.getMoves()).containsExactly(a, two);

    CompositeMove<TestdataSolution> move3 = (CompositeMove<TestdataSolution>) iterator.next();
    assertThat(move3.getMoves()).containsExactly(b, one);

    CompositeMove<TestdataSolution> move4 = (CompositeMove<TestdataSolution>) iterator.next();
    assertThat(move4.getMoves()).containsExactly(b, two);

    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void threeStageCartesianProduct() {
    MoveSelector<TestdataSolution> s1 = SelectorTestUtils.mockMoveSelector(new DummyMove("A"));
    MoveSelector<TestdataSolution> s2 =
        SelectorTestUtils.mockMoveSelector(new DummyMove("1"), new DummyMove("2"));
    MoveSelector<TestdataSolution> s3 =
        SelectorTestUtils.mockMoveSelector(
            new DummyMove("X"), new DummyMove("Y"), new DummyMove("Z"));

    var iterator = new SequentialMultistageMoveIterator<>(List.of(s1, s2, s3));

    List<CompositeMove<TestdataSolution>> moves = new ArrayList<>();
    while (iterator.hasNext()) {
      moves.add((CompositeMove<TestdataSolution>) iterator.next());
    }

    assertThat(moves).hasSize(6); // 1 * 2 * 3

    // Check first combination
    assertThat(((DummyMove) moves.get(0).getMoves()[0]).getCode()).isEqualTo("A");
    assertThat(((DummyMove) moves.get(0).getMoves()[1]).getCode()).isEqualTo("1");
    assertThat(((DummyMove) moves.get(0).getMoves()[2]).getCode()).isEqualTo("X");

    // Check last combination
    assertThat(((DummyMove) moves.get(5).getMoves()[0]).getCode()).isEqualTo("A");
    assertThat(((DummyMove) moves.get(5).getMoves()[1]).getCode()).isEqualTo("2");
    assertThat(((DummyMove) moves.get(5).getMoves()[2]).getCode()).isEqualTo("Z");
  }

  @Test
  void emptyStage() {
    MoveSelector<TestdataSolution> s1 = SelectorTestUtils.mockMoveSelector(new DummyMove("A"));
    MoveSelector<TestdataSolution> s2 = SelectorTestUtils.mockMoveSelector(); // Empty

    var iterator = new SequentialMultistageMoveIterator<>(List.of(s1, s2));

    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void singleStage() {
    DummyMove a = new DummyMove("A");
    DummyMove b = new DummyMove("B");

    MoveSelector<TestdataSolution> s1 = SelectorTestUtils.mockMoveSelector(a, b);

    var iterator = new SequentialMultistageMoveIterator<>(List.of(s1));

    assertThat(iterator.hasNext()).isTrue();

    // Single stage returns the move directly (not wrapped in CompositeMove)
    DummyMove move1 = (DummyMove) iterator.next();
    assertThat(move1).isEqualTo(a);

    DummyMove move2 = (DummyMove) iterator.next();
    assertThat(move2).isEqualTo(b);

    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void nextWhenExhaustedThrowsException() {
    MoveSelector<TestdataSolution> s1 = SelectorTestUtils.mockMoveSelector(new DummyMove("A"));
    MoveSelector<TestdataSolution> s2 = SelectorTestUtils.mockMoveSelector(new DummyMove("1"));

    var iterator = new SequentialMultistageMoveIterator<>(List.of(s1, s2));

    // Exhaust iterator - with 2 stages each having 1 move, there's only 1 combination
    iterator.next();

    assertThat(iterator.hasNext()).isFalse();

    // Expecting NoSuchElementException on next call
    org.junit.jupiter.api.Assertions.assertThrows(
        NoSuchElementException.class, () -> iterator.next());
  }
}
