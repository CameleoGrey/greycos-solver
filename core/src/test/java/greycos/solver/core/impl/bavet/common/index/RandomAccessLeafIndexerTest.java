package greycos.solver.core.impl.bavet.common.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.ArrayList;
import java.util.Random;
import java.util.stream.IntStream;

import greycos.solver.core.impl.bavet.common.tuple.UniTuple;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RandomAccessLeafIndexerTest extends AbstractIndexerTest {

  @Test
  void isRemovable() {
    var indexer = new RandomAccessLeafIndexer<>();
    assertSoftly(
        softly -> {
          softly.assertThat(forEachToTuples(indexer)).isEmpty();
          softly.assertThat(indexer.isRemovable()).isTrue();
        });
  }

  @Test
  void put() {
    var indexer = new RandomAccessLeafIndexer<>();
    var annTuple = newTuple("Ann-F-40");
    assertThat(indexer.size(CompositeKey.none())).isZero();
    indexer.put(CompositeKey.none(), annTuple);
    assertThat(indexer.size(CompositeKey.none())).isEqualTo(1);
    assertSoftly(
        softly -> {
          softly.assertThat(indexer.isRemovable()).isFalse();
          softly.assertThat(forEachToTuples(indexer)).containsExactly(annTuple);
        });
  }

  @Test
  void removeTwice() {
    var indexer = new RandomAccessLeafIndexer<>();
    var annTuple = newTuple("Ann-F-40");
    var annEntry = indexer.put(CompositeKey.none(), annTuple);
    assertSoftly(
        softly -> {
          softly.assertThat(indexer.isRemovable()).isFalse();
          softly.assertThat(forEachToTuples(indexer)).containsExactly(annTuple);
        });

    indexer.remove(CompositeKey.none(), annEntry);
    assertSoftly(
        softly -> {
          softly.assertThat(indexer.isRemovable()).isTrue();
          softly.assertThat(forEachToTuples(indexer)).isEmpty();
        });
    assertThatThrownBy(() -> indexer.remove(CompositeKey.none(), annEntry))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void forEach() {
    var indexer = new RandomAccessLeafIndexer<>();

    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.none(), annTuple);
    var bethTuple = newTuple("Beth-F-30");
    indexer.put(CompositeKey.none(), bethTuple);

    assertThat(forEachToTuples(indexer)).containsOnly(annTuple, bethTuple);
  }

  @Test
  void randomIteratorNeverEnds() {
    var indexer = new RandomAccessLeafIndexer<UniTuple<String>>();
    indexer.put(CompositeKey.none(), newTuple("Ann-F-40"));
    indexer.put(CompositeKey.none(), newTuple("Beth-F-30"));

    assertRepeatingRandomNeverEnds(indexer, CompositeKey.none(), 30);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void uniqueRandomIteratorSurvivesReadOnlyTraversal(boolean useForEach) {
    var indexer = new RandomAccessLeafIndexer<Integer>();
    var key = CompositeKey.none();
    var entries = IntStream.range(0, 100).mapToObj(i -> indexer.put(key, i)).toList();
    for (var i = 0; i < entries.size(); i += 5) {
      indexer.remove(key, entries.get(i));
    }
    var expected = IntStream.range(0, 100).filter(i -> i % 5 != 0).boxed().toList();
    var randomIterator = indexer.uniqueRandomIterator(key, new Random(0));
    var randomResults = new ArrayList<Integer>();
    randomResults.add(randomIterator.next());

    var traversed = new ArrayList<Integer>();
    if (useForEach) {
      indexer.forEach(key, traversed::add);
    } else {
      indexer.iterator(key).forEachRemaining(traversed::add);
    }

    assertThat(traversed).containsExactlyElementsOf(expected);
    randomIterator.forEachRemaining(randomResults::add);
    assertThat(randomResults).containsExactlyInAnyOrderElementsOf(expected);
  }

  private static UniTuple<String> newTuple(String factA) {
    return UniTuple.of(factA, 0);
  }
}
