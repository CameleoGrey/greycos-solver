package ai.greycos.solver.core.impl.bavet.common.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Random;

import ai.greycos.solver.core.api.score.stream.Joiners;
import ai.greycos.solver.core.impl.bavet.bi.joiner.DefaultBiJoiner;
import ai.greycos.solver.core.impl.bavet.common.tuple.UniTuple;
import ai.greycos.solver.core.impl.neighborhood.stream.joiner.DefaultBiNeighborhoodsJoiner;
import ai.greycos.solver.core.preview.api.neighborhood.stream.joiner.NeighborhoodsJoiners;

import org.junit.jupiter.api.Test;

class EqualsAndComparisonIndexerTest extends AbstractIndexerTest {

  private final DefaultBiJoiner<Person, Person> joiner =
      (DefaultBiJoiner<Person, Person>)
          Joiners.equal(Person::gender).and(Joiners.lessThanOrEqual(Person::age));

  private final DefaultBiNeighborhoodsJoiner<Person, Person> neighborhoodsJoiner =
      (DefaultBiNeighborhoodsJoiner<Person, Person>)
          NeighborhoodsJoiners.equal(Person::gender)
              .and(NeighborhoodsJoiners.lessThanOrEqual(Person::age));

  @Test
  void iEmpty() {
    var indexer = new IndexerFactory<>(joiner).buildIndexer(true);
    assertThat(getTuples(indexer, "F", 40)).isEmpty();
  }

  @Test
  void put() {
    var indexer = new IndexerFactory<>(joiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    assertThat(indexer.size(CompositeKey.ofMany("F", 40))).isEqualTo(0);
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);
    assertThat(indexer.size(CompositeKey.ofMany("F", 40))).isEqualTo(1);
  }

  @Test
  void removeTwice() {
    var indexer = new IndexerFactory<>(joiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    var annEntry = indexer.put(CompositeKey.ofMany("F", 40), annTuple);

    indexer.remove(CompositeKey.ofMany("F", 40), annEntry);
    assertThatThrownBy(() -> indexer.remove(CompositeKey.ofMany("F", 40), annEntry))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void forEach() {
    var indexer = new IndexerFactory<>(joiner).buildIndexer(true);

    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);
    var bethTuple = newTuple("Beth-F-30");
    indexer.put(CompositeKey.ofMany("F", 30), bethTuple);
    indexer.put(CompositeKey.ofMany("M", 40), newTuple("Carl-M-40"));
    indexer.put(CompositeKey.ofMany("M", 30), newTuple("Dan-M-30"));
    var ednaTuple = newTuple("Edna-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), ednaTuple);

    assertThat(getTuples(indexer, "F", 40)).containsOnly(annTuple, bethTuple, ednaTuple);
    assertThat(getTuples(indexer, "F", 35)).containsOnly(bethTuple);
    assertThat(getTuples(indexer, "F", 30)).containsOnly(bethTuple);
    assertThat(getTuples(indexer, "F", 20)).isEmpty();
  }

  private static UniTuple<String> newTuple(String factA) {
    return UniTuple.of(factA, 0);
  }

  @Test
  void iteratorEmpty() {
    Indexer<UniTuple<String>> indexer = new IndexerFactory<>(joiner).buildIndexer(true);
    var iterator = indexer.iterator(CompositeKey.ofMany("F", 40));
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void iteratorSingleElement() {
    Indexer<UniTuple<String>> indexer = new IndexerFactory<>(joiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);

    var iterator = indexer.iterator(CompositeKey.ofMany("F", 40));
    assertThat(iterator).hasNext();
    assertThat(iterator.next()).isEqualTo(annTuple);
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void iteratorMultipleElementsSameKey() {
    Indexer<UniTuple<String>> indexer = new IndexerFactory<>(joiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);
    var bethTuple = newTuple("Beth-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), bethTuple);

    var iterator = indexer.iterator(CompositeKey.ofMany("F", 40));
    var resultList = new ArrayList<UniTuple<String>>();
    while (iterator.hasNext()) {
      resultList.add(iterator.next());
    }
    assertThat(resultList).containsOnly(annTuple, bethTuple);
  }

  @Test
  void iteratorComparisonBoundary() {
    Indexer<UniTuple<String>> indexer = new IndexerFactory<>(joiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);
    var bethTuple = newTuple("Beth-F-30");
    indexer.put(CompositeKey.ofMany("F", 30), bethTuple);
    var carolTuple = newTuple("Carol-F-50");
    indexer.put(CompositeKey.ofMany("F", 50), carolTuple);

    var iterator = indexer.iterator(CompositeKey.ofMany("F", 40));
    var resultList = new ArrayList<UniTuple<String>>();
    while (iterator.hasNext()) {
      resultList.add(iterator.next());
    }
    assertThat(resultList).containsOnly(annTuple, bethTuple);
  }

  @Test
  void iteratorComparisonExactBoundary() {
    Indexer<UniTuple<String>> indexer = new IndexerFactory<>(joiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);
    var bethTuple = newTuple("Beth-F-30");
    indexer.put(CompositeKey.ofMany("F", 30), bethTuple);

    var iterator = indexer.iterator(CompositeKey.ofMany("F", 30));
    var resultList = new ArrayList<UniTuple<String>>();
    while (iterator.hasNext()) {
      resultList.add(iterator.next());
    }
    assertThat(resultList).containsOnly(bethTuple);
  }

  @Test
  void iteratorComparisonNoneMatch() {
    Indexer<UniTuple<String>> indexer = new IndexerFactory<>(joiner).buildIndexer(true);
    indexer.put(CompositeKey.ofMany("F", 40), newTuple("Ann-F-40"));
    indexer.put(CompositeKey.ofMany("F", 50), newTuple("Beth-F-50"));

    var iterator = indexer.iterator(CompositeKey.ofMany("F", 20));
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void iteratorDifferentGender() {
    Indexer<UniTuple<String>> indexer = new IndexerFactory<>(joiner).buildIndexer(true);
    indexer.put(CompositeKey.ofMany("F", 40), newTuple("Ann-F-40"));
    indexer.put(CompositeKey.ofMany("M", 30), newTuple("Bob-M-30"));

    var iterator = indexer.iterator(CompositeKey.ofMany("M", 40));
    var resultList = new ArrayList<UniTuple<String>>();
    while (iterator.hasNext()) {
      resultList.add(iterator.next());
    }
    assertThat(resultList).singleElement().extracting(UniTuple::getA).isEqualTo("Bob-M-30");
  }

  @Test
  void randomIteratorEmpty() {
    Indexer<UniTuple<String>> indexer =
        new IndexerFactory<>(neighborhoodsJoiner).buildIndexer(true);
    var random = new Random(0);
    var iterator = indexer.randomIterator(CompositeKey.ofMany("F", 40), random);

    assertThat(iterator.hasNext()).isFalse();
    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(iterator::next);
  }

  @Test
  void randomIteratorSingleElement() {
    Indexer<UniTuple<String>> indexer =
        new IndexerFactory<>(neighborhoodsJoiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);

    var random = new Random(0);
    var iterator = indexer.randomIterator(CompositeKey.ofMany("F", 40), random);

    assertThat(iterator).hasNext();
    assertThat(iterator.next()).isEqualTo(annTuple);

    iterator.remove();
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void randomIteratorMultipleElements() {
    Indexer<UniTuple<String>> indexer =
        new IndexerFactory<>(neighborhoodsJoiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);
    var bethTuple = newTuple("Beth-F-30");
    indexer.put(CompositeKey.ofMany("F", 30), bethTuple);

    var random = new Random(0);
    var iterator = indexer.randomIterator(CompositeKey.ofMany("F", 40), random);

    assertThat(iterator).hasNext();
    assertThat(iterator.next()).isIn(annTuple, bethTuple);
  }

  @Test
  void randomIteratorComparisonBoundary() {
    Indexer<UniTuple<String>> indexer =
        new IndexerFactory<>(neighborhoodsJoiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);
    var bethTuple = newTuple("Beth-F-30");
    indexer.put(CompositeKey.ofMany("F", 30), bethTuple);
    var carolTuple = newTuple("Carol-F-50");
    indexer.put(CompositeKey.ofMany("F", 50), carolTuple);

    var random = new Random(0);
    var iterator = indexer.randomIterator(CompositeKey.ofMany("F", 40), random);

    var resultList = new ArrayList<UniTuple<String>>();
    while (iterator.hasNext()) {
      resultList.add(iterator.next());
      iterator.remove();
    }

    assertThat(resultList).containsOnly(annTuple, bethTuple);
    assertThat(resultList).doesNotContain(carolTuple);
  }

  @Test
  void randomIteratorRemoveAllElements() {
    Indexer<UniTuple<String>> indexer =
        new IndexerFactory<>(neighborhoodsJoiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);
    var bethTuple = newTuple("Beth-F-30");
    indexer.put(CompositeKey.ofMany("F", 30), bethTuple);
    var carolTuple = newTuple("Carol-F-35");
    indexer.put(CompositeKey.ofMany("F", 35), carolTuple);

    var random = new Random(0);
    var iterator = indexer.randomIterator(CompositeKey.ofMany("F", 40), random);

    var clearedElementSet = new HashSet<UniTuple<String>>();
    for (int i = 0; i < 3; i++) {
      assertThat(iterator).hasNext();
      var element = iterator.next();
      clearedElementSet.add(element);
      iterator.remove();
    }

    assertThat(iterator.hasNext()).isFalse();
    assertThat(clearedElementSet).containsExactlyInAnyOrder(annTuple, bethTuple, carolTuple);
    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(iterator::next);
  }

  @Test
  void randomIteratorWithFilter() {
    Indexer<UniTuple<String>> indexer =
        new IndexerFactory<>(neighborhoodsJoiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);
    var bethTuple = newTuple("Beth-F-30");
    indexer.put(CompositeKey.ofMany("F", 30), bethTuple);
    var carolTuple = newTuple("Carol-F-35");
    indexer.put(CompositeKey.ofMany("F", 35), carolTuple);

    var random = new Random(0);
    var iterator =
        indexer.randomIterator(
            CompositeKey.ofMany("F", 40),
            random,
            tuple -> {
              var fact = tuple.getA();
              return fact.startsWith("A") || fact.startsWith("C");
            });

    var resultList = new ArrayList<UniTuple<String>>();
    while (iterator.hasNext()) {
      resultList.add(iterator.next());
      iterator.remove();
    }

    assertThat(resultList).containsExactlyInAnyOrder(annTuple, carolTuple);
    assertThat(resultList).doesNotContain(bethTuple);
  }

  @Test
  void randomIteratorWithFilterEmpty() {
    Indexer<UniTuple<String>> indexer =
        new IndexerFactory<>(neighborhoodsJoiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);
    var bethTuple = newTuple("Beth-F-30");
    indexer.put(CompositeKey.ofMany("F", 30), bethTuple);

    var random = new Random(0);
    var iterator = indexer.randomIterator(CompositeKey.ofMany("F", 40), random, tuple -> false);

    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void randomIteratorWithFilterAndBoundary() {
    Indexer<UniTuple<String>> indexer =
        new IndexerFactory<>(neighborhoodsJoiner).buildIndexer(true);
    var annTuple = newTuple("Ann-F-40");
    indexer.put(CompositeKey.ofMany("F", 40), annTuple);
    var bethTuple = newTuple("Beth-F-30");
    indexer.put(CompositeKey.ofMany("F", 30), bethTuple);
    var carolTuple = newTuple("Carol-F-50");
    indexer.put(CompositeKey.ofMany("F", 50), carolTuple);

    var random = new Random(0);
    var iterator =
        indexer.randomIterator(
            CompositeKey.ofMany("F", 40), random, tuple -> tuple.getA().startsWith("B"));

    var resultList = new ArrayList<UniTuple<String>>();
    while (iterator.hasNext()) {
      resultList.add(iterator.next());
      iterator.remove();
    }

    assertThat(resultList).containsOnly(bethTuple);
    assertThat(resultList).doesNotContain(annTuple, carolTuple);
  }
}
