package greycos.solver.math.impl.cotwin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import java.util.Random;

import org.junit.jupiter.api.Test;

class InclusiveLongValueRangeTest {

  @Test
  void iteratesBothLongEndpointsWithoutWrapping() {
    for (long lower : new long[] {Long.MIN_VALUE, Long.MAX_VALUE - 2}) {
      var range = new InclusiveLongValueRange(lower, lower + 2);
      assertThat(range.getSize()).isEqualTo(3);
      assertThat(range.isEmpty()).isFalse();
      assertThat(range.contains(null)).isFalse();
      var iterator = range.createOriginalIterator();
      assertThat(iterator.next()).isEqualTo(lower);
      assertThat(iterator.next()).isEqualTo(lower + 1);
      assertThat(iterator.next()).isEqualTo(lower + 2);
      assertThat(iterator.hasNext()).isFalse();
      assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
      assertThatThrownBy(() -> range.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> range.get(3)).isInstanceOf(IndexOutOfBoundsException.class);
    }
    var singleton = new InclusiveLongValueRange(Long.MAX_VALUE, Long.MAX_VALUE);
    assertThat(singleton.createOriginalIterator()).toIterable().containsExactly(Long.MAX_VALUE);
    assertThat(singleton.createRandomIterator(new Random(0)).next()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void validatesCardinalityWithWiderArithmetic() {
    var range = new InclusiveLongValueRange(1, Long.MAX_VALUE);
    assertThat(range.getSize()).isEqualTo(Long.MAX_VALUE);
    assertThat(range.get(Long.MAX_VALUE - 1)).isEqualTo(Long.MAX_VALUE);
    assertThat(range.contains(0L)).isFalse();
    assertThat(range.contains(Long.MAX_VALUE)).isTrue();
    assertThatThrownBy(() -> new InclusiveLongValueRange(0, Long.MAX_VALUE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cardinality");
    assertThatThrownBy(() -> new InclusiveLongValueRange(Long.MIN_VALUE, Long.MAX_VALUE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InclusiveLongValueRange(1, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void randomIterationUsesItsSuppliedGeneratorAndStaysWithinBounds() {
    var range = new InclusiveLongValueRange(Long.MAX_VALUE - 100, Long.MAX_VALUE);
    var first = range.createRandomIterator(new Random(4711));
    var second = range.createRandomIterator(new Random(4711));
    for (int i = 0; i < 1000; i++) {
      var value = first.next();
      assertThat(value).isEqualTo(second.next());
      assertThat(range.contains(value)).isTrue();
    }
    assertThat(range).isEqualTo(new InclusiveLongValueRange(Long.MAX_VALUE - 100, Long.MAX_VALUE));
    assertThat(range.hashCode())
        .isEqualTo(new InclusiveLongValueRange(Long.MAX_VALUE - 100, Long.MAX_VALUE).hashCode());
  }
}
