package greycos.solver.core.impl.heuristic.selector.entity.nearby;

import java.util.Iterator;

import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Nearby entity selector using an entity as origin. Filters and reorders destination entities by
 * distance from an origin entity.
 */
public final class NearEntityNearbyEntitySelector<Solution_>
    extends AbstractNearbyEntitySelector<Solution_> {

  private final @NonNull EntitySelector<Solution_> originEntitySelector;

  public NearEntityNearbyEntitySelector(
      @NonNull EntitySelector<Solution_> childEntitySelector,
      @NonNull EntitySelector<Solution_> originEntitySelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection) {
    this(
        childEntitySelector,
        originEntitySelector,
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        Integer.MAX_VALUE,
        false);
  }

  public NearEntityNearbyEntitySelector(
      @NonNull EntitySelector<Solution_> childEntitySelector,
      @NonNull EntitySelector<Solution_> originEntitySelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection,
      int maxNearbySortSize,
      boolean eagerInitialization) {
    super(
        childEntitySelector,
        originEntitySelector,
        "entity-nearby-entity",
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        maxNearbySortSize,
        eagerInitialization);
    this.originEntitySelector = originEntitySelector;
    phaseLifecycleSupport.addEventListener(originEntitySelector);
  }

  @Override
  protected @NonNull Iterator<?> endingOriginIterator() {
    return originEntitySelector.endingIterator();
  }

  @Override
  public boolean isNeverEnding() {
    return randomSelection;
  }

  @Override
  public long getSize() {
    long size = childEntitySelector.getSize() - 1;
    return Math.max(size, 0L);
  }

  @Override
  protected @NonNull Iterator<Object> originIterator() {
    return originEntitySelector.iterator();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NearEntityNearbyEntitySelector<?> that)) {
      return false;
    }
    return super.equals(o) && originEntitySelector.equals(that.originEntitySelector);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + originEntitySelector.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "NearEntityNearbyEntitySelector("
        + getEntityDescriptor().getEntityClass().getSimpleName()
        + ")";
  }

  @Override
  protected boolean excludeOrigin() {
    return true;
  }
}
