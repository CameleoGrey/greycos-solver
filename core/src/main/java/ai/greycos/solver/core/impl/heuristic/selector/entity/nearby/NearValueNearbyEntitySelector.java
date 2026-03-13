package ai.greycos.solver.core.impl.heuristic.selector.entity.nearby;

import java.util.Iterator;
import java.util.random.RandomGenerator;

import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;

import org.jspecify.annotations.NonNull;

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
      @NonNull NearbyRandom nearbyRandom,
      boolean randomSelection) {
    super(childEntitySelector, nearbyDistanceMeter, nearbyRandom, randomSelection);
    this.originValueSelector = originValueSelector;
    phaseLifecycleSupport.addEventListener(originValueSelector);
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
    private final int nearbySize;
    private int count = 0;

    public RandomNearbyEntityIterator(RandomGenerator workingRandom) {
      this.workingRandom = workingRandom;
      this.nearbySize = (int) childEntitySelector.getSize();
    }

    @Override
    public boolean hasNext() {
      return count < nearbySize;
    }

    @Override
    public Object next() {
      int nearbyIndex = nearbyRandom.nextInt(workingRandom, nearbySize);
      count++;

      // Get nearbyIndex-th entity from child selector
      Iterator<Object> childIterator = childEntitySelector.iterator();
      Object result = null;
      for (int i = 0; i <= nearbyIndex && childIterator.hasNext(); i++) {
        result = childIterator.next();
      }
      return result;
    }
  }

  private class OriginalNearbyEntityIterator implements Iterator<Object> {

    private final int nearbySize;
    private int index = 0;

    public OriginalNearbyEntityIterator() {
      this.nearbySize = (int) childEntitySelector.getSize();
    }

    @Override
    public boolean hasNext() {
      return index < nearbySize;
    }

    @Override
    public Object next() {
      // For original order, just iterate through entities in order
      Iterator<Object> childIterator = childEntitySelector.iterator();
      Object result = null;
      for (int i = 0; i <= index && childIterator.hasNext(); i++) {
        result = childIterator.next();
      }
      index++;
      return result;
    }
  }
}
