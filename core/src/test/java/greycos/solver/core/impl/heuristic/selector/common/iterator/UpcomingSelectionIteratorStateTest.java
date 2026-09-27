package greycos.solver.core.impl.heuristic.selector.common.iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

class UpcomingSelectionIteratorStateTest {

  @Test
  void directNextChecksInitialAndSubsequentExhaustion() {
    var empty = new MutableIterator();
    assertThatThrownBy(empty::next).isInstanceOf(NoSuchElementException.class);
    assertThat(empty.hasNext()).isFalse();
    assertThatThrownBy(empty::next).isInstanceOf(NoSuchElementException.class);
    assertThat(empty.preparations).isEqualTo(1);

    var singleton = new MutableIterator("value");
    assertThat(singleton.next()).isEqualTo("value");
    assertThatThrownBy(singleton::next).isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(singleton::next).isInstanceOf(NoSuchElementException.class);
    assertThat(singleton.preparations).isEqualTo(2);
  }

  @Test
  void nullIsASelectionAndRepeatedHasNextDoesNotConsumeIt() {
    var iterator = new MutableIterator(null, "value");
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.preparations).isEqualTo(1);
    assertThat(iterator.next()).isNull();
    assertThat(iterator.next()).isEqualTo("value");
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void discardRevivesExhaustionAndPreservesNull() {
    var iterator = new MutableIterator();
    assertThat(iterator.hasNext()).isFalse();
    iterator.replace((Object) null);
    iterator.discardUpcomingSelection();
    assertThat(iterator.next()).isNull();
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);

    iterator.replace("value");
    iterator.discardUpcomingSelection();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo("value");
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void discardRemovesPreparedSelectionWithoutAdvancingAgain() {
    var iterator = new MutableIterator("old", null, "new");
    assertThat(iterator.hasNext()).isTrue();
    iterator.discardUpcomingSelection();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isNull();
    assertThat(iterator.next()).isEqualTo("new");
  }

  private static final class MutableIterator extends UpcomingSelectionIterator<Object> {
    private Iterator<Object> values;
    private int preparations;

    private MutableIterator(Object... values) {
      replace(values);
    }

    private void replace(Object... values) {
      this.values = Arrays.asList(values).iterator();
    }

    @Override
    protected Object createUpcomingSelection() {
      preparations++;
      return values.hasNext() ? values.next() : noUpcomingSelection();
    }
  }
}
