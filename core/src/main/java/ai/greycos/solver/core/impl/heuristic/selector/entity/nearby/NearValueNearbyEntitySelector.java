package ai.greycos.solver.core.impl.heuristic.selector.entity.nearby;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.random.RandomGenerator;

import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;

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
    return randomSelection || childEntitySelector.isNeverEnding();
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

  // ************************************************************************
  // Inner classes
  // ************************************************************************

  private class RandomNearbyEntityIterator implements Iterator<Object> {

    private final RandomGenerator workingRandom;
    private final Iterator<Object> replayingOriginIterator;
    private Object origin = null;
    private Object cachedOrigin = null;
    private int cachedNearbySize = -1;

    public RandomNearbyEntityIterator(RandomGenerator workingRandom) {
      this.workingRandom = workingRandom;
      this.replayingOriginIterator = originValueSelector.iterator();
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
        cachedNearbySize = getNearbySize(origin);
      }
      if (cachedNearbySize <= 0) {
        throw new NoSuchElementException();
      }
      int nearbyIndex = nearbyRandom.nextInt(workingRandom, cachedNearbySize);
      return getNearbyDestination(origin, nearbyIndex);
    }
  }

  private class OriginalNearbyEntityIterator implements Iterator<Object> {

    private final Iterator<Object> replayingOriginIterator;
    private int nearbySize = -1;
    private int index = 0;
    private boolean originSelected = false;
    private boolean originIsNotEmpty;
    private Object origin = null;

    public OriginalNearbyEntityIterator() {
      this.replayingOriginIterator = originValueSelector.iterator();
    }

    private void selectOrigin() {
      if (originSelected) {
        return;
      }
      originIsNotEmpty = replayingOriginIterator.hasNext();
      if (originIsNotEmpty) {
        origin = replayingOriginIterator.next();
        nearbySize = getNearbySize(origin);
      }
      originSelected = true;
    }

    @Override
    public boolean hasNext() {
      selectOrigin();
      return originIsNotEmpty && index < nearbySize;
    }

    @Override
    public Object next() {
      selectOrigin();
      if (!originIsNotEmpty || nearbySize <= 0 || index >= nearbySize) {
        throw new NoSuchElementException();
      }
      Object result = getNearbyDestination(origin, index);
      index++;
      return result;
    }
  }
}
