package greycos.solver.core.impl.score.stream.collector.consecutive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.stream.common.SequenceChain;

/** Checks the observable result and navigation without comparing tree bookkeeping or caches. */
public final class ConsecutiveSequenceTestUtils {

  private ConsecutiveSequenceTestUtils() {}

  public static void assertSameSequences(SequenceChain<?, ?> actual, SequenceChain<?, ?> expected) {
    var items = new ArrayList<List<?>>();
    var lengths = new ArrayList<Object>();
    for (var sequence : expected.getConsecutiveSequences()) {
      items.add(new ArrayList<>(sequence.getItems()));
      lengths.add(sequence.getLength());
    }
    assertChain(
        actual, items, lengths, expected.getBreaks().stream().map(b -> b.getLength()).toList());
  }

  public static void assertChain(
      SequenceChain<?, ?> actual,
      List<? extends List<?>> expectedItems,
      List<?> expectedLengths,
      List<?> expectedBreakLengths) {
    var sequences = new ArrayList<>(actual.getConsecutiveSequences());
    var breaks = new ArrayList<>(actual.getBreaks());
    assertThat(sequences).hasSize(expectedItems.size());
    assertThat(expectedLengths).hasSize(sequences.size());
    assertThat(breaks).hasSize(Math.max(0, sequences.size() - 1));
    assertThat(expectedBreakLengths).hasSize(breaks.size());
    assertThat(actual.getFirstSequence())
        .isSameAs(sequences.isEmpty() ? null : sequences.getFirst());
    assertThat(actual.getLastSequence()).isSameAs(sequences.isEmpty() ? null : sequences.getLast());
    assertThat(actual.getFirstBreak()).isSameAs(breaks.isEmpty() ? null : breaks.getFirst());
    assertThat(actual.getLastBreak()).isSameAs(breaks.isEmpty() ? null : breaks.getLast());
    for (int i = 0; i < sequences.size(); i++) {
      var sequence = sequences.get(i);
      var items = expectedItems.get(i);
      assertThat(new ArrayList<>(sequence.getItems())).isEqualTo(items);
      assertThat(sequence.getCount()).isEqualTo(items.size());
      assertThat(sequence.getLength()).isEqualTo(expectedLengths.get(i));
      assertThat(sequence.getFirstItem()).isEqualTo(items.getFirst());
      assertThat(sequence.getLastItem()).isEqualTo(items.getLast());
      assertThat(sequence.isFirst()).isEqualTo(i == 0);
      assertThat(sequence.isLast()).isEqualTo(i == sequences.size() - 1);
      assertThat(sequence.getPreviousBreak()).isSameAs(i == 0 ? null : breaks.get(i - 1));
      assertThat(sequence.getNextBreak()).isSameAs(i == breaks.size() ? null : breaks.get(i));
    }
    for (int i = 0; i < breaks.size(); i++) {
      var sequenceBreak = breaks.get(i);
      assertThat(sequenceBreak.getLength()).isEqualTo(expectedBreakLengths.get(i));
      assertThat(sequenceBreak.getPreviousSequenceEnd()).isSameAs(sequences.get(i).getLastItem());
      assertThat(sequenceBreak.getNextSequenceStart())
          .isSameAs(sequences.get(i + 1).getFirstItem());
      assertThat(sequenceBreak.isFirst()).isEqualTo(i == 0);
      assertThat(sequenceBreak.isLast()).isEqualTo(i == breaks.size() - 1);
    }
  }
}
