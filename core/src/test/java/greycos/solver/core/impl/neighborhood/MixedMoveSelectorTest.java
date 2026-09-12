package greycos.solver.core.impl.neighborhood;

import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.mockMoveSelector;
import static greycos.solver.core.impl.neighborhood.MixedMoveSelector.countMoveSelectors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.heuristic.move.SelectorBasedDummyMove;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.composite.CartesianProductMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.composite.UnionMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.CachingMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.FilteringMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.ProbabilityMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.SelectedCountLimitMoveSelector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class MixedMoveSelectorTest {

  @Test
  void plainSelectorCountsAsOne() {
    MoveSelector<TestdataSolution> moveSelector = mockMoveSelector();
    assertThat(countMoveSelectors(moveSelector)).isEqualTo(1);
  }

  @Test
  void flatUnionCountsChildren() {
    List<MoveSelector<TestdataSolution>> childMoveSelectorList =
        List.of(mockMoveSelector(), mockMoveSelector(), mockMoveSelector());
    var union = new UnionMoveSelector<>(childMoveSelectorList, true);
    assertThat(countMoveSelectors(union)).isEqualTo(3);
  }

  @Test
  void nestedUnionCountsLeaves() {
    List<MoveSelector<TestdataSolution>> innerChildMoveSelectorList =
        List.of(mockMoveSelector(), mockMoveSelector());
    var innerUnion = new UnionMoveSelector<>(innerChildMoveSelectorList, true);
    List<MoveSelector<TestdataSolution>> outerChildMoveSelectorList =
        List.of(innerUnion, mockMoveSelector());
    var outerUnion = new UnionMoveSelector<>(outerChildMoveSelectorList, true);
    assertThat(countMoveSelectors(outerUnion)).isEqualTo(3);
  }

  @Test
  void filteringSeesThroughToChild() {
    List<MoveSelector<TestdataSolution>> childMoveSelectorList =
        List.of(mockMoveSelector(), mockMoveSelector(), mockMoveSelector());
    var union = new UnionMoveSelector<>(childMoveSelectorList, true);
    SelectionFilter<TestdataSolution, Move<TestdataSolution>> filter =
        (scoreDirector, move) -> true;
    var filtering = FilteringMoveSelector.of(union, filter);
    assertThat(countMoveSelectors(filtering)).isEqualTo(3);
  }

  @Test
  void cachingSeesThroughToChild() {
    List<MoveSelector<TestdataSolution>> childMoveSelectorList =
        List.of(mockMoveSelector(), mockMoveSelector());
    var union = new UnionMoveSelector<>(childMoveSelectorList, true);
    var caching = new CachingMoveSelector<>(union, SelectionCacheType.PHASE, false);
    assertThat(countMoveSelectors(caching)).isEqualTo(2);
  }

  @Test
  void selectedCountLimitSeesThroughToChild() {
    List<MoveSelector<TestdataSolution>> childMoveSelectorList =
        List.of(mockMoveSelector(), mockMoveSelector());
    var union = new UnionMoveSelector<>(childMoveSelectorList, true);
    var limited = new SelectedCountLimitMoveSelector<>(union, 5L);
    assertThat(countMoveSelectors(limited)).isEqualTo(2);
  }

  @Test
  void cartesianProductCountsAsOne() {
    List<MoveSelector<TestdataSolution>> childMoveSelectorList =
        List.of(mockMoveSelector(), mockMoveSelector(), mockMoveSelector());
    var cartesianProduct = new CartesianProductMoveSelector<>(childMoveSelectorList, true, true);
    assertThat(countMoveSelectors(cartesianProduct)).isEqualTo(1);
  }

  @Test
  void probabilityMoveSelectorCountsAsOneAndIsNotRejected() {
    // ProbabilityMoveSelector weights a single selector's own moves;
    // it is unrelated to weighting one selector against another,
    // and must not be confused with a weighted union.
    MoveSelector<TestdataSolution> child = mockMoveSelector(new SelectorBasedDummyMove("a1"));
    var probability =
        new ProbabilityMoveSelector<>(
            child, SelectionCacheType.PHASE, (scoreDirector, move) -> 1.0);
    assertThat(countMoveSelectors(probability)).isEqualTo(1);
  }

  @Test
  void weightedUnionThrows() {
    MoveSelector<TestdataSolution> a = mockMoveSelector();
    MoveSelector<TestdataSolution> b = mockMoveSelector();
    Map<MoveSelector<TestdataSolution>, Double> weightMap = new HashMap<>();
    weightMap.put(a, 1.0);
    weightMap.put(b, 2.0);
    var weightedUnion =
        new UnionMoveSelector<>(
            List.of(a, b),
            true,
            (scoreDirector, selector) -> weightMap.getOrDefault(selector, 1.0));

    assertThatThrownBy(() -> countMoveSelectors(weightedUnion))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Neighborhoods API");
  }

  @Test
  void weightedNestedUnionThrows() {
    MoveSelector<TestdataSolution> a = mockMoveSelector();
    MoveSelector<TestdataSolution> b = mockMoveSelector();
    Map<MoveSelector<TestdataSolution>, Double> weightMap = new HashMap<>();
    weightMap.put(a, 1.0);
    weightMap.put(b, 2.0);
    var innerWeightedUnion =
        new UnionMoveSelector<>(
            List.of(a, b),
            true,
            (scoreDirector, selector) -> weightMap.getOrDefault(selector, 1.0));
    List<MoveSelector<TestdataSolution>> outerChildMoveSelectorList =
        List.of(innerWeightedUnion, mockMoveSelector());
    var outerUnion = new UnionMoveSelector<>(outerChildMoveSelectorList, true);

    assertThatThrownBy(() -> countMoveSelectors(outerUnion))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Neighborhoods API");
  }
}
