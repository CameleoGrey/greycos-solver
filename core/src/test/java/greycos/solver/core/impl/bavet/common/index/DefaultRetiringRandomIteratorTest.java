package greycos.solver.core.impl.bavet.common.index;

import static greycos.solver.core.impl.bavet.common.index.AbstractIndexerTest.toEntries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.stream.IntStream;

import greycos.solver.core.impl.util.ElementAwareArrayList;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class DefaultRetiringRandomIteratorTest {

  @Test
  void emptySet() {
    var emptySet =
        new DefaultRetiringRandomIterator<>(new ElementAwareArrayList<>(), new Random(0));

    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(emptySet.hasNext()).isFalse();
          softly.assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(emptySet::next);
          softly
              .assertThatExceptionOfType(IllegalStateException.class)
              .isThrownBy(emptySet::retire);
        });
  }

  @Test
  void singleElementSetPickAndRetire() {
    var list = List.of("A");
    var set = new DefaultRetiringRandomIterator<>(toEntries(list), new Random(0));

    assertThat(set.hasNext()).isTrue();

    var element = set.next();
    assertThat(element).isEqualTo("A");

    set.retire();
    assertThat(set.hasNext()).isFalse();

    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(set::next);
  }

  @Test
  void multipleElementSet() {
    var list = List.of("A", "B", "C", "D", "E");
    var set = new DefaultRetiringRandomIterator<>(toEntries(list), new Random(0));

    assertThat(set.hasNext()).isTrue();

    var element = set.next();
    assertThat(element).isIn(list);
  }

  @Test
  void pickDoesNotModifySet() {
    var list = List.of("A", "B", "C");
    var set = new DefaultRetiringRandomIterator<>(toEntries(list), new Random(0));

    var element1 = set.next();
    var element2 = set.next();
    var element3 = set.next();

    assertThat(set.hasNext()).isTrue();
    // Elements may repeat since we never called retire().
    assertThat(element1).isIn(list);
    assertThat(element2).isIn(list);
    assertThat(element3).isIn(list);
  }

  @Test
  void retireAllElements() {
    var list = List.of("A", "B", "C", "D", "E");
    var set = new DefaultRetiringRandomIterator<>(toEntries(list), new Random(0));

    var clearedElements = new HashSet<String>();
    for (int i = 0; i < 5; i++) {
      assertThat(set.hasNext()).isTrue();
      clearedElements.add(set.next());
      set.retire();
    }

    assertThat(set.hasNext()).isFalse();
    assertThat(clearedElements).containsExactlyInAnyOrderElementsOf(list);

    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(set::next);
  }

  /**
   * Every other test here uses few enough elements that {@code SlotReservationMap}'s cap is {@code
   * slotCount / 2}; this one is large enough for its {@code MAX_SPARSE_ENTRY_COUNT} to be the
   * binding cap, so the drain crosses into the dense stage.
   */
  @Test
  void retireAllElementsAcrossUpgradeThreshold() {
    var list = IntStream.range(0, 100).boxed().toList();
    var set = new DefaultRetiringRandomIterator<>(toEntries(list), new Random(0));

    var drainedElements = new HashSet<Integer>();
    for (int i = 0; i < 100; i++) {
      assertThat(set.hasNext()).isTrue();
      drainedElements.add(set.next());
      set.retire();
    }

    assertThat(set.hasNext()).isFalse();
    assertThat(drainedElements).containsExactlyInAnyOrderElementsOf(list);
  }

  /**
   * The shuffle runs over physical slots, so a gapped list makes it draw slots which hold nothing.
   * Those must be rejected and redrawn, never returned and never counted as an element.
   */
  @Test
  void drainSkipsGapsAndReturnsEveryLiveElementOnce() {
    var source = new ElementAwareArrayList<Integer>();
    var entryList = IntStream.range(0, 100).mapToObj(source::addEntry).toList();
    // Remove every fifth element, staying under the list's own compaction threshold so the gaps
    // survive.
    for (var i = 0; i < 100; i += 5) {
      entryList.get(i).remove();
    }
    var liveElements = IntStream.range(0, 100).boxed().filter(i -> i % 5 != 0).toList();
    assertThat(source.slotCount()).isGreaterThan(source.size()); // Gaps really are present.

    var set = new DefaultRetiringRandomIterator<>(source, new Random(0));
    var drainedElements = new ArrayList<Integer>();
    while (set.hasNext()) {
      drainedElements.add(set.next());
      set.retire();
    }

    assertThat(drainedElements).containsExactlyInAnyOrderElementsOf(liveElements);
  }

  @Test
  void nearlyExhaustedPoolRejectsEachGapAtMostOnce() {
    var source = new ElementAwareArrayList<Integer>();
    var entries = IntStream.range(0, 10_000).mapToObj(source::addEntry).toList();
    for (var i = 0; i < entries.size(); i += 5) {
      entries.get(i).remove();
    }
    var gapCount = source.slotCount() - source.size();
    assertThat(gapCount).isEqualTo(2_000);
    var random = new CountingRandom(0);
    var iterator = new DefaultRetiringRandomIterator<>(source, random);
    var retired = new HashSet<Integer>();
    for (var i = 0; i < source.size() - 1; i++) {
      assertThat(retired.add(iterator.next())).isTrue();
      iterator.retire();
    }

    var countBeforeRepeatedDraws = random.drawCount;
    var survivor = iterator.next();
    assertThat(survivor).isNotIn(retired);
    for (var i = 1; i < 1_000; i++) {
      assertThat(iterator.next()).isEqualTo(survivor);
    }
    assertThat(random.drawCount - countBeforeRepeatedDraws).isLessThanOrEqualTo(1_000 + gapCount);
    assertThat(random.drawCount).isLessThanOrEqualTo(source.size() - 1 + 1_000 + gapCount);
    iterator.retire();
    assertThat(iterator.hasNext()).isFalse();
    retired.add(survivor);
    assertThat(retired)
        .containsExactlyInAnyOrderElementsOf(
            IntStream.range(0, 10_000).filter(i -> i % 5 != 0).boxed().toList());
  }

  @Test
  void gappedDrainHasUniformPermutationDistribution() {
    var source = new ElementAwareArrayList<Integer>();
    var entries = IntStream.range(0, 5).mapToObj(source::addEntry).toList();
    entries.getFirst().remove();
    assertThat(source.slotCount()).isEqualTo(5);
    assertThat(source.size()).isEqualTo(4);
    var orderCounts = new HashMap<List<Integer>, Integer>();
    // Enumerate all 5! equally likely choices over a shrinking five-slot pool. Removing the
    // single gap from each order must leave every permutation of the four elements equally likely.
    for (var rank = 0; rank < 120; rank++) {
      var initialRank = rank;
      var random =
          new Random() {
            private int remainingRank = initialRank;
            private int drawCount;

            @Override
            public int nextInt(int bound) {
              assertThat(++drawCount).isLessThanOrEqualTo(5);
              var choice = remainingRank % bound;
              remainingRank /= bound;
              return choice;
            }
          };
      var iterator = new DefaultRetiringRandomIterator<>(source, random);
      var order = new ArrayList<Integer>();
      while (iterator.hasNext()) {
        order.add(iterator.next());
        iterator.retire();
      }
      assertThat(order).containsExactlyInAnyOrder(1, 2, 3, 4);
      orderCounts.merge(order, 1, Integer::sum);
    }
    assertThat(orderCounts).hasSize(24);
    assertThat(orderCounts.values()).containsOnly(5);
  }

  private static final class CountingRandom extends Random {
    private int drawCount;

    private CountingRandom(long seed) {
      super(seed);
    }

    @Override
    public int nextInt(int bound) {
      drawCount++;
      return super.nextInt(bound);
    }
  }
}
