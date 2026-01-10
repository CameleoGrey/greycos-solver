package ai.greycos.solver.core.impl.heuristic.selector.value.nearby;

import java.util.Iterator;
import java.util.Objects;

import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicReplayingEntitySelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Nearby value selector using an entity as origin. Filters and reorders destination values by
 * distance from an origin entity.
 */
public final class NearEntityNearbyValueSelector<Solution_>
    extends AbstractNearbyValueSelector<Solution_, EntitySelector<Solution_>> {

  private final boolean discardNearbyIndexZero;

  public NearEntityNearbyValueSelector(
      @NonNull IterableValueSelector<Solution_> childValueSelector,
      @NonNull EntitySelector<Solution_> originEntitySelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection) {
    super(
        childValueSelector,
        castToMimicReplayingEntitySelector(originEntitySelector),
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection);
    // Compute discardNearbyIndexZero: if value type is assignable from entity type,
    // the origin entity may appear in the value list, so we should discard it
    this.discardNearbyIndexZero =
        childValueSelector
            .getVariableDescriptor()
            .getVariablePropertyType()
            .isAssignableFrom(originEntitySelector.getEntityDescriptor().getEntityClass());
  }

  private static <Solution_> EntitySelector<Solution_> castToMimicReplayingEntitySelector(
      EntitySelector<Solution_> originEntitySelector) {
    if (!(originEntitySelector instanceof MimicReplayingEntitySelector)) {
      throw new IllegalStateException(
          "Nearby value selector requires a replaying entity selector. "
              + "The originEntitySelector ("
              + originEntitySelector
              + ") is not a MimicReplayingEntitySelector.");
    }
    return originEntitySelector;
  }

  @Override
  public boolean isNeverEnding() {
    return randomSelection || childValueSelector.isNeverEnding();
  }

  @Override
  public long getSize(Object entity) {
    long size = childValueSelector.getSize(entity);
    if (discardNearbyIndexZero) {
      size--;
    }
    return size;
  }

  @Override
  public @NonNull Iterator<Object> iterator() {
    return new EntityDependentNearbyIterator();
  }

  @Override
  public @NonNull Iterator<Object> iterator(@NonNull Object entity) {
    // Get replaying iterator from the replaying selector
    Iterator<Object> replayingOriginEntityIterator = replayingSelector.iterator();
    long childSize = childValueSelector.getSize(entity);

    if (randomSelection) {
      return new RandomNearbyValueIterator(workingRandom, replayingOriginEntityIterator, childSize);
    } else {
      return new OriginalNearbyValueIterator(replayingOriginEntityIterator, childSize);
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
    return super.equals(o) && discardNearbyIndexZero == that.discardNearbyIndexZero;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), discardNearbyIndexZero);
  }

  @Override
  public String toString() {
    return "NearEntityNearbyValueSelector(" + getVariableDescriptor().getVariableName() + ")";
  }

  // ************************************************************************
  // Inner classes
  // ************************************************************************

  /**
   * Iterator for random nearby value selection. Caches the origin entity from the replaying
   * iterator and uses it for all nearby value selections.
   */
  private class RandomNearbyValueIterator implements Iterator<Object> {

    private final java.util.Random random;
    private final Iterator<Object> replayingOriginIterator;
    private final int nearbySize;

    // Origin caching - origin is selected once and reused
    private Object origin = null;

    public RandomNearbyValueIterator(
        java.util.Random random, Iterator<Object> replayingOriginIterator, long childSize) {
      this.random = random;
      this.replayingOriginIterator = replayingOriginIterator;
      if (childSize > Integer.MAX_VALUE) {
        throw new IllegalStateException(
            "The childValueSelector ("
                + childValueSelector
                + ") has a valueSize ("
                + childSize
                + ") which is higher than Integer.MAX_VALUE.");
      }
      // Adjust size for discardNearbyIndexZero
      this.nearbySize = (int) childSize - (discardNearbyIndexZero ? 1 : 0);
    }

    @Override
    public boolean hasNext() {
      // Update origin if replaying iterator has advanced
      // The replaying iterator will return the same value until the recording iterator advances
      if (origin != null || replayingOriginIterator.hasNext()) {
        return nearbySize > 0;
      }
      return false;
    }

    @Override
    public Object next() {
      if (nearbyRandom == null) {
        throw new IllegalStateException("nearbyRandom is null but randomSelection is true");
      }

      // Update origin from replaying iterator if it has advanced
      // The replaying iterator returns the same value repeatedly until recording advances
      if (replayingOriginIterator.hasNext()) {
        origin = replayingOriginIterator.next();
      }

      // Select nearby index using probability distribution
      int nearbyIndex = nearbyRandom.nextInt(random, nearbySize);

      // If discarding index 0 (origin itself), shift to start at index 1
      if (discardNearbyIndexZero) {
        nearbyIndex++;
      }

      // Use distance matrix for sorted destinations
      return distanceMatrix.getDestination(origin, nearbyIndex);
    }
  }

  /**
   * Iterator for deterministic nearby value selection in distance order. Caches the origin entity
   * from the replaying iterator and uses it for all nearby value selections.
   */
  private class OriginalNearbyValueIterator implements Iterator<Object> {

    private final Iterator<Object> replayingOriginIterator;
    private final long childSize;
    private int nextNearbyIndex;

    // Origin caching state
    private boolean originSelected = false;
    private boolean originIsNotEmpty;
    private Object origin = null;

    public OriginalNearbyValueIterator(Iterator<Object> replayingOriginIterator, long childSize) {
      this.replayingOriginIterator = replayingOriginIterator;
      this.childSize = childSize;
      // Start at index 1 if discarding index 0 (the origin itself)
      this.nextNearbyIndex = discardNearbyIndexZero ? 1 : 0;
    }

    /**
     * Selects the origin from the replaying iterator. Called once on first access, then the origin
     * is cached for all subsequent calls.
     */
    private void selectOrigin() {
      if (originSelected) {
        return; // Already selected, use cached origin
      }
      /*
       * The origin iterator is guaranteed to be a replaying iterator.
       * Therefore next() will point to whatever the related recording iterator was pointing to
       * at the time when its next() was called.
       * As a result, origin here will be constant unless next() on the original recording
       * iterator is called first.
       */
      originIsNotEmpty = replayingOriginIterator.hasNext();
      if (originIsNotEmpty) {
        origin = replayingOriginIterator.next();
      }
      originSelected = true;
    }

    @Override
    public boolean hasNext() {
      selectOrigin();
      return originIsNotEmpty && nextNearbyIndex < childSize;
    }

    @Override
    public Object next() {
      selectOrigin(); // Ensure origin is selected and cached
      Object result = distanceMatrix.getDestination(origin, nextNearbyIndex);
      nextNearbyIndex++;
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
