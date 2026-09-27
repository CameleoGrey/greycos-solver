package greycos.solver.core.impl.heuristic.selector.entity.nearby;

import java.util.Iterator;

import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Nearby entity selector using a value as origin. Filters and reorders destination entities by
 * distance from an origin value.
 */
public final class NearValueNearbyEntitySelector<Solution_>
    extends AbstractNearbyEntitySelector<Solution_> {

  private final @NonNull IterableValueSelector<Solution_> originValueSelector;

  public NearValueNearbyEntitySelector(
      @NonNull EntitySelector<Solution_> childEntitySelector,
      @NonNull IterableValueSelector<Solution_> originValueSelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection) {
    this(
        childEntitySelector,
        originValueSelector,
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        Integer.MAX_VALUE,
        false);
  }

  public NearValueNearbyEntitySelector(
      @NonNull EntitySelector<Solution_> childEntitySelector,
      @NonNull IterableValueSelector<Solution_> originValueSelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection,
      int maxNearbySortSize,
      boolean eagerInitialization) {
    super(
        childEntitySelector,
        originValueSelector,
        "entity-nearby-value",
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        maxNearbySortSize,
        eagerInitialization);
    this.originValueSelector = originValueSelector;
    phaseLifecycleSupport.addEventListener(originValueSelector);
  }

  @Override
  protected @NonNull Iterator<?> endingOriginIterator() {
    return originValueSelector.endingIterator(null);
  }

  @Override
  public boolean isNeverEnding() {
    return randomSelection;
  }

  @Override
  protected @NonNull Iterator<Object> originIterator() {
    return originValueSelector.iterator();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NearValueNearbyEntitySelector<?> that)) {
      return false;
    }
    return super.equals(o) && originValueSelector.equals(that.originValueSelector);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + originValueSelector.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "NearValueNearbyEntitySelector("
        + getEntityDescriptor().getEntityClass().getSimpleName()
        + ")";
  }
}
