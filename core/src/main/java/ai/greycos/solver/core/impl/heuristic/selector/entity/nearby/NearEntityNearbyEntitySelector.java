package ai.greycos.solver.core.impl.heuristic.selector.entity.nearby;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.random.RandomGenerator;

import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Nearby entity selector using an entity as origin. Filters and reorders destination entities by
 * distance from an origin entity.
 */
public final class NearEntityNearbyEntitySelector<Solution_>
    extends AbstractNearbyEntitySelector<Solution_> {

  private final @NonNull EntitySelector<Solution_> originEntitySelector;
  private final boolean discardNearbyIndexZero = true;

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
    return randomSelection || childEntitySelector.isNeverEnding();
  }

  @Override
  public long getSize() {
    long size = childEntitySelector.getSize() - (discardNearbyIndexZero ? 1 : 0);
    return Math.max(size, 0L);
  }

  @Override
  public @NonNull Iterator<Object> iterator() {
    if (randomSelection) {
      return new RandomNearbyEntityIterator(workingRandom);
    } else {
      return new OriginalNearbyEntityIterator();
    }
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
  protected int getDestinationSizeMaximumAdjustment() {
    return discardNearbyIndexZero ? 1 : 0;
  }

  // ************************************************************************
  // Inner classes
  // ************************************************************************

  private class RandomNearbyEntityIterator implements Iterator<Object> {

    private final RandomGenerator random;
    private final Iterator<Object> replayingOriginIterator;
    private Object origin = null;
    private Object cachedOrigin = null;
    private int cachedNearbySize = -1;

    public RandomNearbyEntityIterator(RandomGenerator random) {
      this.random = random != null ? random : NearEntityNearbyEntitySelector.this.workingRandom;
      this.replayingOriginIterator = originEntitySelector.iterator();
    }

    @Override
    public boolean hasNext() {
      return origin != null || replayingOriginIterator.hasNext();
    }

    @Override
    public Object next() {
      if (replayingOriginIterator.hasNext()) {
        origin = replayingOriginIterator.next();
      }
      if (origin == null || nearbyRandom == null) {
        throw new NoSuchElementException();
      }
      if (origin != cachedOrigin) {
        cachedOrigin = origin;
        cachedNearbySize = getNearbySize(origin) - (discardNearbyIndexZero ? 1 : 0);
      }
      if (cachedNearbySize <= 0) {
        throw new NoSuchElementException();
      }
      int nearbyIndex = nearbyRandom.nextInt(random, cachedNearbySize);
      if (discardNearbyIndexZero) {
        nearbyIndex++;
      }
      return getNearbyDestination(origin, nearbyIndex);
    }
  }

  private class OriginalNearbyEntityIterator implements Iterator<Object> {

    private final Iterator<Object> replayingOriginIterator;
    private final long childSize;
    private int nextNearbyIndex;

    public OriginalNearbyEntityIterator() {
      this.replayingOriginIterator = originEntitySelector.iterator();
      this.childSize = childEntitySelector.getSize();
      this.nextNearbyIndex = discardNearbyIndexZero ? 1 : 0;
    }

    @Override
    public boolean hasNext() {
      return replayingOriginIterator.hasNext() && nextNearbyIndex < childSize;
    }

    @Override
    public Object next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      Object origin = replayingOriginIterator.next();
      Object destination = getNearbyDestination(origin, nextNearbyIndex);
      nextNearbyIndex++;
      return destination;
    }
  }
}
