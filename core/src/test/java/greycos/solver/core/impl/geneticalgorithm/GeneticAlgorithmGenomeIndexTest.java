package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class GeneticAlgorithmGenomeIndexTest {

  @Test
  void returnsFirstEntryInCurrentOrderIncludingHashCollisionsAndEqualDuplicates() {
    var index = new GeneticAlgorithmGenomeIndex<String>();
    var first = genome("same", new int[][] {{0, 31}});
    var collision = genome("same", new int[][] {{1, 0}});
    var equal = genome(new String("same"), first.lists());
    index.add(first, "first");
    index.add(collision, "collision");
    index.add(equal, "later duplicate");
    assertThat(index.firstMatch(equal)).isEqualTo("first");
    assertThat(index.firstMatch(collision)).isEqualTo("collision");
    assertThat(index.firstMatch(genome("different", first.lists()))).isNull();

    index.clear();
    index.add(equal, "later duplicate");
    index.add(first, "first");
    assertThat(index.firstMatch(first)).isEqualTo("later duplicate");
    assertThat(index.firstMatch(collision)).isNull();
  }

  @Test
  void mutableBasicHashesAndEqualityAreAlwaysEvaluatedLive() {
    for (var lists : List.of(new int[0][], new int[][] {{0, 1}})) {
      for (int wrapping = 0; wrapping < 3; wrapping++) {
        var storedValue = new MutableValue(1);
        var queryValue = new MutableValue(2);
        var stored = genome(wrap(storedValue, wrapping), lists);
        var query = genome(wrap(queryValue, wrapping), lists);
        var index = new GeneticAlgorithmGenomeIndex<String>();
        index.add(stored, "stored");
        int oldHash = stored.hashCode();
        assertThat(index.firstMatch(query)).isNull();
        storedValue.id = 2;
        assertThat(stored.hashCode()).isNotEqualTo(oldHash);
        assertThat(index.firstMatch(query)).isEqualTo("stored");
        assertThat(stored).isEqualTo(query).hasSameHashCodeAs(query);
        queryValue.id = 3;
        assertThat(index.firstMatch(query)).isNull();
      }
    }
  }

  @Test
  void bucketLookupMatchesLinearReferenceAcrossAppendReorderClearAndMutation() {
    var random = new Random(37);
    var index = new GeneticAlgorithmGenomeIndex<Integer>();
    var entries = new ArrayList<GeneticAlgorithmGenome>();
    var mutable = new MutableValue(0);
    for (int iteration = 0; iteration < 200; iteration++) {
      if (iteration % 7 == 0) mutable.id = random.nextInt(4);
      entries.add(
          genome(
              iteration % 3 == 0 ? mutable : new MutableValue(random.nextInt(4)),
              iteration % 2 == 0 ? new int[][] {{0, 31}} : new int[][] {{1, 0}}));
      if (iteration % 11 == 0) Collections.shuffle(entries, random);
      if (iteration % 31 == 0) entries.subList(0, entries.size() / 2).clear();
      index.clear();
      for (int i = 0; i < entries.size(); i++) index.add(entries.get(i), i);
      for (int value = 0; value < 4; value++) {
        for (var lists : List.of(new int[][] {{0, 31}}, new int[][] {{1, 0}}, new int[][] {{9}})) {
          var query = genome(new MutableValue(value), lists);
          Integer expected = null;
          for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).equals(query)) {
              expected = i;
              break;
            }
          }
          assertThat(index.firstMatch(query)).isEqualTo(expected);
        }
      }
    }
  }

  @Test
  void distinctListFingerprintsAvoidPopulationWideBasicComparisons() {
    var comparisons = new AtomicLong();
    var index = new GeneticAlgorithmGenomeIndex<Integer>();
    for (int i = 0; i < 1024; i++) {
      index.add(genome(new CountedValue(comparisons), new int[][] {{i}}), i);
    }
    for (int i = 0; i < 1024; i++) {
      assertThat(index.firstMatch(genome(new CountedValue(comparisons), new int[][] {{i}})))
          .isEqualTo(i);
    }
    assertThat(comparisons.get()).isEqualTo(1024);
  }

  private static GeneticAlgorithmGenome genome(Object value, int[][] lists) {
    return new GeneticAlgorithmGenome(new Object[] {value}, lists);
  }

  private static Object wrap(MutableValue value, int wrapping) {
    return switch (wrapping) {
      case 0 -> value;
      case 1 -> new Wrapped(value);
      default -> Optional.of(value);
    };
  }

  private record Wrapped(MutableValue value) {}

  private static final class MutableValue {
    private int id;

    private MutableValue(int id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof MutableValue value && id == value.id;
    }

    @Override
    public int hashCode() {
      return id;
    }
  }

  private record CountedValue(AtomicLong comparisons) {
    @Override
    public boolean equals(Object other) {
      comparisons.incrementAndGet();
      return other instanceof CountedValue;
    }

    @Override
    public int hashCode() {
      throw new AssertionError("Basic values must not be hashed for indexing.");
    }
  }
}
