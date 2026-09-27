package greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;

import org.jspecify.annotations.Nullable;

/**
 * A selector and the stable population used to cache its nearby distances. Only {@code selector}
 * receives lifecycle events; {@code populationSelector} is an already-owned subtree. The live
 * filter is evaluated when selecting candidates, before applying the nearby rank limit. An optional
 * membership supplier preserves finite child selection limits; consumers obtain one current
 * identity-based snapshot per selection, never during distance initialization.
 */
public record NearbySelectionSource<Solution_, Selector_>(
    Selector_ selector,
    Selector_ populationSelector,
    @Nullable SelectionFilter<Solution_, Object> liveFilter,
    @Nullable Supplier<Set<Object>> membershipSupplier) {

  public NearbySelectionSource(
      Selector_ selector,
      Selector_ populationSelector,
      @Nullable SelectionFilter<Solution_, Object> liveFilter) {
    this(selector, populationSelector, liveFilter, null);
  }

  public NearbySelectionSource {
    Objects.requireNonNull(selector);
    Objects.requireNonNull(populationSelector);
  }
}
