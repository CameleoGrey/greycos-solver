package greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class RuinRecreateMoveSelectorSizeTest {

  @Test
  void orderedSelectionsAndClippedBounds() {
    assertThat(RuinRecreateMoveSelectorSize.count(3, 1, 1)).isEqualTo(3);
    assertThat(RuinRecreateMoveSelectorSize.count(3, 2, 2)).isEqualTo(6);
    assertThat(RuinRecreateMoveSelectorSize.count(3, 1, 3)).isEqualTo(15);
    assertThat(RuinRecreateMoveSelectorSize.count(3, 5, 40)).isEqualTo(6);
    assertThat(RuinRecreateMoveSelectorSize.count(0, 5, 40)).isZero();
    assertThat(RuinRecreateMoveSelectorSize.count(3, 0, 0)).isEqualTo(3);
  }

  @Test
  void largePopulationsAndOverflow() {
    assertThat(RuinRecreateMoveSelectorSize.count(20, 20, 20)).isEqualTo(2432902008176640000L);
    assertThat(RuinRecreateMoveSelectorSize.count(21, 1, 1)).isEqualTo(21);
    assertThat(RuinRecreateMoveSelectorSize.count(21, 21, 21)).isEqualTo(Long.MAX_VALUE);
    assertThat(RuinRecreateMoveSelectorSize.count(Long.MAX_VALUE, 1, 1)).isEqualTo(Long.MAX_VALUE);
    assertThat(RuinRecreateMoveSelectorSize.count(Long.MAX_VALUE, 1, 2)).isEqualTo(Long.MAX_VALUE);
    // Neither term overflows, but their sum does.
    assertThat(RuinRecreateMoveSelectorSize.count(3_000_000_000L, 1, 2))
        .isEqualTo(9_000_000_000_000_000_000L);
    assertThat(RuinRecreateMoveSelectorSize.count(3_037_000_500L, 1, 2)).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void rejectsInvalidRanges() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> RuinRecreateMoveSelectorSize.count(-1, 1, 2));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> RuinRecreateMoveSelectorSize.count(10, -1, 2));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> RuinRecreateMoveSelectorSize.count(10, 3, 2));
  }
}
