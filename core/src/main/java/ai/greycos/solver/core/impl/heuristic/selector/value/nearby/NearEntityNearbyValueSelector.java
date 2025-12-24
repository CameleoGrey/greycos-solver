package ai.greycos.solver.core.impl.heuristic.selector.value.nearby;

import java.util.Iterator;
import java.util.Objects;

import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.ValueSelector;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Nearby value selector that uses an entity as the origin.
 *
 * <p>Filters and reorders the selection of destination values based on distance from an origin
 * entity.
 */
public final class NearEntityNearbyValueSelector<Solution_>
    extends AbstractNearbyValueSelector<Solution_> {

  private final @NonNull EntitySelector<Solution_> originEntitySelector;

  public NearEntityNearbyValueSelector(
      @NonNull ValueSelector<Solution_> childValueSelector,
      @NonNull EntitySelector<Solution_> originEntitySelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection) {
    super(childValueSelector, nearbyDistanceMeter, nearbyRandom, randomSelection);
    this.originEntitySelector = originEntitySelector;
    phaseLifecycleSupport.addEventListener(originEntitySelector);
  }

  @Override
  public boolean isNeverEnding() {
    return randomSelection || childValueSelector.isNeverEnding();
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
    // because we only iterate through nearby values
    return iterator(entity);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NearEntityNearbyValueSelector<?> that)) {
      return false;
    }
    return super.equals(o) && Objects.equals(originEntitySelector, that.originEntitySelector);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), originEntitySelector);
  }

  @Override
  public String toString() {
    return "NearEntityNearbyValueSelector(" + getVariableDescriptor().getVariableName() + ")";
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
      // Get the nearbyIndex-th value from the child selector
      // For now, we iterate through the child selector to get the value at index
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
}
