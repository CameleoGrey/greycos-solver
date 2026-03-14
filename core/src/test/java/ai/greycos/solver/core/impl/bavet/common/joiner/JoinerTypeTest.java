package ai.greycos.solver.core.impl.bavet.common.joiner;

import static ai.greycos.solver.core.impl.bavet.common.joiner.JoinerType.CONTAINED_IN;
import static ai.greycos.solver.core.impl.bavet.common.joiner.JoinerType.CONTAINING;
import static ai.greycos.solver.core.impl.bavet.common.joiner.JoinerType.CONTAINING_ANY_OF;
import static ai.greycos.solver.core.impl.bavet.common.joiner.JoinerType.DISJOINT;
import static ai.greycos.solver.core.impl.bavet.common.joiner.JoinerType.EQUAL;
import static ai.greycos.solver.core.impl.bavet.common.joiner.JoinerType.GREATER_THAN;
import static ai.greycos.solver.core.impl.bavet.common.joiner.JoinerType.GREATER_THAN_OR_EQUAL;
import static ai.greycos.solver.core.impl.bavet.common.joiner.JoinerType.INTERSECTING;
import static ai.greycos.solver.core.impl.bavet.common.joiner.JoinerType.LESS_THAN;
import static ai.greycos.solver.core.impl.bavet.common.joiner.JoinerType.LESS_THAN_OR_EQUAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class JoinerTypeTest {

  @Test
  void equal() {
    assertThat(EQUAL.matches(1, 1)).isTrue();
    assertThat(EQUAL.matches(1, 2)).isFalse();
    assertThat(EQUAL.matches(1, null)).isFalse();
    assertThat(EQUAL.matches(null, 1)).isFalse();
  }

  @Test
  void lessThan() {
    assertThat(LESS_THAN.matches(1, 1)).isFalse();
    assertThat(LESS_THAN.matches(1, 2)).isTrue();
    assertThat(LESS_THAN.matches(2, 1)).isFalse();
  }

  @Test
  void lessThanOrEquals() {
    assertThat(LESS_THAN_OR_EQUAL.matches(1, 1)).isTrue();
    assertThat(LESS_THAN_OR_EQUAL.matches(1, 2)).isTrue();
    assertThat(LESS_THAN_OR_EQUAL.matches(2, 1)).isFalse();
  }

  @Test
  void greaterThan() {
    assertThat(GREATER_THAN.matches(1, 1)).isFalse();
    assertThat(GREATER_THAN.matches(2, 1)).isTrue();
    assertThat(GREATER_THAN.matches(1, 2)).isFalse();
  }

  @Test
  void greaterThanOrEquals() {
    assertThat(GREATER_THAN_OR_EQUAL.matches(1, 1)).isTrue();
    assertThat(GREATER_THAN_OR_EQUAL.matches(2, 1)).isTrue();
    assertThat(GREATER_THAN_OR_EQUAL.matches(1, 2)).isFalse();
  }

  @Test
  void contain() {
    assertThat(CONTAINING.matches(List.of(1, 3), 1)).isTrue();
    assertThat(CONTAINING.matches(List.of(1, 3), 2)).isFalse();
  }

  @Test
  void containedIn() {
    assertThat(CONTAINED_IN.matches(1, List.of(1, 3))).isTrue();
    assertThat(CONTAINED_IN.matches(2, List.of(1, 3))).isFalse();
  }

  @Test
  void containsAny() {
    assertThat(CONTAINING_ANY_OF.matches(List.of(1, 2, 3), List.of(2))).isTrue();
    assertThat(CONTAINING_ANY_OF.matches(List.of(1, 2, 3), List.of(6))).isFalse();
    assertThat(CONTAINING_ANY_OF.matches(List.of(1, 2, 3), List.of(3, 4, 5))).isTrue();
    assertThat(CONTAINING_ANY_OF.matches(List.of(3, 4, 5), List.of(1, 2, 3))).isTrue();
    assertThat(CONTAINING_ANY_OF.matches(List.of(1, 2, 3), List.of(4, 5, 6))).isFalse();
    assertThat(CONTAINING_ANY_OF.matches(List.of(1, 2, 3), List.of())).isFalse();
    assertThat(CONTAINING_ANY_OF.matches(List.of(), List.of(1))).isFalse();
    assertThat(CONTAINING_ANY_OF.matches(List.of(), List.of())).isFalse();
  }

  @Test
  void intersecting() {
    Collection<Integer> left = List.of(1, 2, 3);
    Collection<Integer> right = List.of(3, 4, 5);
    assertThat(INTERSECTING.matches(left, right)).isTrue();
    assertThat(INTERSECTING.matches(right, left)).isTrue();
    assertThat(INTERSECTING.matches(left, Collections.emptySet())).isFalse();
  }

  @Test
  void disjoint() {
    Collection<Integer> first = List.of(1, 2, 3);
    Collection<Integer> second = List.of(3, 4, 5);
    assertThat(DISJOINT.matches(first, second)).isFalse();
    assertThat(DISJOINT.matches(second, first)).isFalse();
    Collection<Integer> third = List.of(4, 5);
    assertThat(DISJOINT.matches(first, third)).isTrue();
    assertThat(DISJOINT.matches(third, first)).isTrue();
    // empty sets are disjoint
    assertThat(DISJOINT.matches(Collections.emptyList(), Collections.emptySet())).isTrue();
    assertThat(DISJOINT.matches(first, Collections.emptySet())).isTrue();
  }
}
