package greycos.solver.core.impl.constructionheuristic.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class RankSumCartesianIteratorTest {

  @Test
  void ordersByRankSumThenLexicographicRanks() {
    var cursor =
        new RankSumCartesianIterator<>(
            List.of(List.of("a0", "a1", "a2").iterator(), List.of("b0", "b1", "b2").iterator()));
    var tuples = new ArrayList<List<String>>();
    cursor.forEachRemaining(tuples::add);
    assertThat(tuples)
        .containsExactly(
            List.of("a0", "b0"),
            List.of("a0", "b1"),
            List.of("a1", "b0"),
            List.of("a0", "b2"),
            List.of("a1", "b1"),
            List.of("a2", "b0"),
            List.of("a1", "b2"),
            List.of("a2", "b1"),
            List.of("a2", "b2"));
  }

  @Test
  void producesAllThreeDimensionalTuplesOnce() {
    var cursor =
        new RankSumCartesianIterator<>(
            List.of(
                List.of(0, 1).iterator(), List.of(0, 1, 2).iterator(), List.of(0, 1).iterator()));
    var tuples = new ArrayList<List<Integer>>();
    cursor.forEachRemaining(tuples::add);
    assertThat(tuples).hasSize(12).doesNotHaveDuplicates();
    assertThat(tuples)
        .isSortedAccordingTo(
            (left, right) -> {
              int comparison =
                  Integer.compare(
                      left.stream().mapToInt(Integer::intValue).sum(),
                      right.stream().mapToInt(Integer::intValue).sum());
              for (int i = 0; comparison == 0 && i < left.size(); i++) {
                comparison = Integer.compare(left.get(i), right.get(i));
              }
              return comparison;
            });
  }

  @Test
  void cachesOnlyTheExploredRanksOfHugeProducts() {
    var reads = new AtomicInteger();
    var cursor =
        new RankSumCartesianIterator<>(
            List.of(countedIterator(reads), countedIterator(reads), countedIterator(reads)));
    assertThat(reads).hasValue(0);
    assertThat(cursor.hasNext()).isTrue();
    assertThat(reads).hasValue(3);
    for (int i = 0; i < 40; i++) {
      cursor.next();
    }
    assertThat(reads.get()).isLessThan(40);
    cursor.close();
    assertThat(cursor.hasNext()).isFalse();
    assertThatThrownBy(cursor::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void emptyChildProducesNoTuple() {
    var cursor =
        new RankSumCartesianIterator<>(
            List.of(List.of(0, 1).iterator(), Collections.<Integer>emptyIterator()));
    assertThat(cursor.hasNext()).isFalse();
    assertThat(cursor.hasNext()).isFalse();
  }

  @Test
  void zeroChildrenProduceOneEmptyTuple() {
    var cursor = new RankSumCartesianIterator<Integer>(List.of());
    assertThat(cursor.next()).isEmpty();
    assertThat(cursor.hasNext()).isFalse();
  }

  @Test
  void singletonChildrenAndNullAlternativesArePreserved() {
    var withNull = new ArrayList<String>();
    withNull.add(null);
    withNull.add("a1");
    var cursor =
        new RankSumCartesianIterator<>(List.of(withNull.iterator(), List.of("b0").iterator()));
    assertThat(cursor.next()).containsExactly(null, "b0");
    assertThat(cursor.next()).containsExactly("a1", "b0");
    assertThat(cursor.hasNext()).isFalse();
  }

  private static Iterator<Integer> countedIterator(AtomicInteger reads) {
    return IntStream.range(0, 1_000_000).peek(index -> reads.incrementAndGet()).boxed().iterator();
  }
}
