package greycos.solver.core.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.NoSuchElementException;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SingletonIteratorTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "value")
  void cursorMovesBetweenTheTwoValidPositions(@Nullable String value) {
    var iterator = new SingletonIterator<>(value);
    for (var round = 0; round < 3; round++) {
      assertThat(iterator.hasNext()).isTrue();
      assertThat(iterator.hasPrevious()).isFalse();
      assertThat(iterator.nextIndex()).isZero();
      assertThat(iterator.previousIndex()).isEqualTo(-1);
      assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(iterator::previous);

      assertThat(iterator.next()).isEqualTo(value);
      assertThat(iterator.hasNext()).isFalse();
      assertThat(iterator.hasPrevious()).isTrue();
      assertThat(iterator.nextIndex()).isEqualTo(1);
      assertThat(iterator.previousIndex()).isZero();
      assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(iterator::next);

      assertThat(iterator.previous()).isEqualTo(value);
    }
  }

  @Test
  void mutatorsAreUnsupportedAtBothCursorPositions() {
    var iterator = new SingletonIterator<>("value");
    for (var position = 0; position < 2; position++) {
      assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(iterator::remove);
      assertThatExceptionOfType(UnsupportedOperationException.class)
          .isThrownBy(() -> iterator.set("replacement"));
      assertThatExceptionOfType(UnsupportedOperationException.class)
          .isThrownBy(() -> iterator.add(null));
      if (position == 0) {
        assertThat(iterator.next()).isEqualTo("value");
      }
    }
    assertThat(iterator.previous()).isEqualTo("value");
  }
}
