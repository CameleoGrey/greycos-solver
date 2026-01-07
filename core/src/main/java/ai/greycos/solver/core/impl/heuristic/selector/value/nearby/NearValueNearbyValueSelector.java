package ai.greycos.solver.core.impl.heuristic.selector.value.nearby;

import java.util.Iterator;
import java.util.Objects;

import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Nearby value selector that uses a value as origin.
 *
 * <p>Filters and reorders the selection of destination values based on distance from an origin
 * value.
 */
public final class NearValueNearbyValueSelector<Solution_>
    extends AbstractNearbyValueSelector<Solution_> {

  private final @NonNull IterableValueSelector<Solution_> originValueSelector;

  public NearValueNearbyValueSelector(
      @NonNull IterableValueSelector<Solution_> childValueSelector,
      @NonNull IterableValueSelector<Solution_> originValueSelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection) {
    super(childValueSelector, nearbyDistanceMeter, nearbyRandom, randomSelection);
    this.originValueSelector = originValueSelector;
    phaseLifecycleSupport.addEventListener(originValueSelector);
  }

  @Override
  public boolean isNeverEnding() {
    return randomSelection || childValueSelector.isNeverEnding();
  }

  @Override
  public @NonNull Iterator<Object> iterator() {
    return new EntityDependentNearbyIterator();
  }

  @Override
  public @NonNull Iterator<Object> iterator(@NonNull Object entity) {
    if (randomSelection) {
      return new RandomNearbyValueIterator(workingRandom, entity);
    } else {
      return new OriginalNearbyValueIterator(entity);
    }
  }

  @Override
  public @NonNull Iterator<Object> endingIterator(@NonNull Object entity) {
    // For nearby selection, ending iterator is the same as regular iterator
    return iterator(entity);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NearValueNearbyValueSelector<?> that)) {
      return false;
    }
    return super.equals(o) && Objects.equals(originValueSelector, that.originValueSelector);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), originValueSelector);
  }

  @Override
  public String toString() {
    return "NearValueNearbyValueSelector(" + getVariableDescriptor().getVariableName() + ")";
  }

  // ************************************************************************
  // Inner classes
  // ************************************************************************

  private class RandomNearbyValueIterator implements Iterator<Object> {

    private final java.util.Random workingRandom;
    private final @Nullable Object entity;
    private final int nearbySize;
    private int count = 0;

    public RandomNearbyValueIterator(java.util.Random workingRandom, @Nullable Object entity) {
      this.workingRandom = workingRandom;
      this.entity = entity;
      this.nearbySize = (int) childValueSelector.getSize(entity);
    }

    @Override
    public boolean hasNext() {
      return count < nearbySize;
    }

    @Override
    public Object next() {
      if (nearbyRandom == null) {
        throw new IllegalStateException("nearbyRandom is null but randomSelection is true");
      }
      int nearbyIndex = nearbyRandom.nextInt(workingRandom, nearbySize);
      count++;
      // Get nearbyIndex-th value from child selector
      Iterator<Object> childIterator = childValueSelector.iterator(entity);
      Object result = null;
      for (int i = 0; i <= nearbyIndex && childIterator.hasNext(); i++) {
        result = childIterator.next();
      }
      return result;
    }
  }

  private class OriginalNearbyValueIterator implements Iterator<Object> {

    private final @Nullable Object entity;
    private final int nearbySize;
    private int index = 0;

    public OriginalNearbyValueIterator(@Nullable Object entity) {
      this.entity = entity;
      this.nearbySize = (int) childValueSelector.getSize(entity);
    }

    @Override
    public boolean hasNext() {
      return index < nearbySize;
    }

    @Override
    public Object next() {
      // For original order, just iterate through values in order
      Iterator<Object> childIterator = childValueSelector.iterator(entity);
      Object result = null;
      for (int i = 0; i <= index && childIterator.hasNext(); i++) {
        result = childIterator.next();
      }
      index++;
      return result;
    }
  }

  /**
   * Iterator for nearby selection when no entity is provided. This iterator delegates to the child
   * selector's iterator without entity context.
   */
  private class EntityDependentNearbyIterator implements Iterator<Object> {

    private final Iterator<Object> childIterator;

    public EntityDependentNearbyIterator() {
      this.childIterator = childValueSelector.iterator();
    }

    @Override
    public boolean hasNext() {
      return childIterator.hasNext();
    }

    @Override
    public Object next() {
      return childIterator.next();
    }
  }
}
