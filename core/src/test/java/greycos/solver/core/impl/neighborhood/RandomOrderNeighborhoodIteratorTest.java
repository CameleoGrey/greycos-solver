package greycos.solver.core.impl.neighborhood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.random.RandomGenerator;

import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;
import greycos.solver.core.preview.api.neighborhood.stream.MoveIterable;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * {@code RandomOrderNeighborhoodIterator} must draw a fresh neighborhood for every move, not just
 * once per step. Every {@link NeverEndingMoveIterable} here is deliberately endless (like a real
 * neighborhood's move iterator almost always is), so a per-exhaustion draw would only ever pick the
 * first neighborhood drawn.
 */
@Execution(ExecutionMode.CONCURRENT)
class RandomOrderNeighborhoodIteratorTest {

  @Test
  void drawsFromEveryNeighborhoodWithinOneRun() {
    var moveIterableList =
        List.<MoveIterable<TestdataSolution>>of(
            new NeverEndingMoveIterable(0),
            new NeverEndingMoveIterable(1),
            new NeverEndingMoveIterable(2));
    var iterator = new RandomOrderNeighborhoodIterator<>(moveIterableList, new Random(0));

    var seenNeighborhoodIndexSet = new HashSet<Integer>();
    for (var i = 0; i < 30; i++) {
      var move = (LabeledMove) iterator.next();
      seenNeighborhoodIndexSet.add(move.neighborhoodIndex());
    }

    assertThat(seenNeighborhoodIndexSet).containsExactlyInAnyOrder(0, 1, 2);
  }

  @Test
  void finiteNeighborhoodsExhaustAndRepeatedHasNextDoesNotChangeTheDraw() {
    var moveIterableList =
        List.<MoveIterable<TestdataSolution>>of(
            random -> List.<Move<TestdataSolution>>of().iterator(),
            random ->
                List.<Move<TestdataSolution>>of(new LabeledMove(1), new LabeledMove(2)).iterator(),
            random -> List.<Move<TestdataSolution>>of(new LabeledMove(3)).iterator());
    var iterator = new RandomOrderNeighborhoodIterator<>(moveIterableList, new Random(37));
    var reference = new RandomOrderNeighborhoodIterator<>(moveIterableList, new Random(37));
    var moves = new ArrayList<Move<TestdataSolution>>();
    while (iterator.hasNext()) {
      assertThat(iterator.hasNext()).isTrue();
      assertThat(iterator.hasNext()).isTrue();
      var move = iterator.next();
      assertThat(move).isEqualTo(reference.next());
      moves.add(move);
    }
    assertThat(moves)
        .containsExactlyInAnyOrder(new LabeledMove(1), new LabeledMove(2), new LabeledMove(3));
    assertThat(iterator.hasNext()).isFalse();
    assertThat(reference.hasNext()).isFalse();
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
  }

  /** Never reports exhaustion, matching a real neighborhood's move iterator. */
  @NullMarked
  private record NeverEndingMoveIterable(int neighborhoodIndex)
      implements MoveIterable<TestdataSolution> {

    @Override
    public Iterator<Move<TestdataSolution>> iterator(RandomGenerator random) {
      return new Iterator<>() {
        @Override
        public boolean hasNext() {
          return true;
        }

        @Override
        public Move<TestdataSolution> next() {
          return new LabeledMove(neighborhoodIndex);
        }
      };
    }
  }

  @NullMarked
  private record LabeledMove(int neighborhoodIndex) implements Move<TestdataSolution> {

    @Override
    public void execute(MutableSolutionView<TestdataSolution> solutionView) {
      // Never actually executed by this test.
    }
  }
}
