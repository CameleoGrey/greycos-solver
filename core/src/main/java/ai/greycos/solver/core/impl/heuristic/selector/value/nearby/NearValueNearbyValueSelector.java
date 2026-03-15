package ai.greycos.solver.core.impl.heuristic.selector.value.nearby;

import java.util.Iterator;
import java.util.random.RandomGenerator;

import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.mimic.MimicReplayingValueSelector;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Nearby value selector using a value as origin. Filters and reorders destination values by
 * distance from an origin value. Requires MimicReplayingValueSelector for consistent origin.
 */
public final class NearValueNearbyValueSelector<Solution_>
    extends AbstractNearbyValueSelector<Solution_, IterableValueSelector<Solution_>> {

  public NearValueNearbyValueSelector(
      @NonNull IterableValueSelector<Solution_> childValueSelector,
      @NonNull IterableValueSelector<Solution_> originValueSelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection) {
    this(
        childValueSelector,
        originValueSelector,
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        Integer.MAX_VALUE,
        false);
  }

  public NearValueNearbyValueSelector(
      @NonNull IterableValueSelector<Solution_> childValueSelector,
      @NonNull IterableValueSelector<Solution_> originValueSelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection,
      int maxNearbySortSize,
      boolean eagerInitialization) {
    super(
        childValueSelector,
        castToMimicReplayingValueSelector(originValueSelector),
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        maxNearbySortSize,
        eagerInitialization);
  }

  private static <Solution_> IterableValueSelector<Solution_> castToMimicReplayingValueSelector(
      IterableValueSelector<Solution_> originValueSelector) {
    if (!(originValueSelector instanceof MimicReplayingValueSelector)) {
      throw new IllegalStateException(
          "Nearby value selector requires a replaying value selector. "
              + "The originValueSelector ("
              + originValueSelector
              + ") is not a MimicReplayingValueSelector.");
    }
    return originValueSelector;
  }

  @Override
  public boolean isNeverEnding() {
    return randomSelection || childValueSelector.isNeverEnding();
  }

  @Override
  protected @NonNull Iterator<Object> endingOriginIteratorForInitialization() {
    return replayingSelector.endingIterator(null);
  }

  @Override
  public @NonNull Iterator<Object> iterator() {
    return new EntityDependentNearbyIterator();
  }

  @Override
  public @NonNull Iterator<Object> iterator(@NonNull Object entity) {
    // Get replaying iterator from the replaying value selector
    Iterator<Object> replayingOriginValueIterator = replayingSelector.iterator(entity);
    long childSize = childValueSelector.getSize(entity);

    if (randomSelection) {
      return new RandomNearbyValueIterator(workingRandom, replayingOriginValueIterator, childSize);
    } else {
      return new OriginalNearbyValueIterator(replayingOriginValueIterator);
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
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return "NearValueNearbyValueSelector(" + getVariableDescriptor().getVariableName() + ")";
  }

  // ************************************************************************
  // Inner classes
  // ************************************************************************

  /**
   * Iterator for random nearby value selection. Caches the origin value from the replaying iterator
   * and uses it for all nearby value selections.
   */
  private class RandomNearbyValueIterator implements Iterator<Object> {

    private final RandomGenerator workingRandom;
    private final Iterator<Object> replayingOriginIterator;
    private final int nearbySize;

    // Origin caching - origin is selected once and reused
    private Object origin = null;
    private Object cachedOrigin = null;
    private int cachedNearbySize = -1;

    public RandomNearbyValueIterator(
        RandomGenerator workingRandom, Iterator<Object> replayingOriginIterator, long childSize) {
      this.workingRandom = workingRandom;
      this.replayingOriginIterator = replayingOriginIterator;
      if (childSize > Integer.MAX_VALUE) {
        throw new IllegalStateException(
            "The childValueSelector ("
                + childValueSelector
                + ") has a valueSize ("
                + childSize
                + ") which is higher than Integer.MAX_VALUE.");
      }
      this.nearbySize = (int) childSize;
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
      if (origin == null) {
        throw new java.util.NoSuchElementException();
      }

      // Select nearby index using probability distribution
      if (origin != cachedOrigin) {
        cachedOrigin = origin;
        cachedNearbySize = getNearbySize(origin);
      }
      if (cachedNearbySize <= 0) {
        throw new java.util.NoSuchElementException();
      }
      int nearbyIndex = nearbyRandom.nextInt(workingRandom, cachedNearbySize);

      // Use standard distance matrix for sorted destinations
      return getDistanceMatrix().getDestination(origin, nearbyIndex);
    }
  }

  /**
   * Iterator for deterministic nearby value selection in distance order. Caches the origin value
   * from the replaying iterator and uses it for all nearby value selections.
   */
  private class OriginalNearbyValueIterator implements Iterator<Object> {

    private final Iterator<Object> replayingOriginIterator;
    private int nearbySize = -1;
    private int nextNearbyIndex = 0;

    // Origin caching state
    private boolean originSelected = false;
    private boolean originIsNotEmpty;
    private Object origin = null;

    public OriginalNearbyValueIterator(Iterator<Object> replayingOriginIterator) {
      this.replayingOriginIterator = replayingOriginIterator;
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
        nearbySize = getNearbySize(origin);
      }
      originSelected = true;
    }

    @Override
    public boolean hasNext() {
      selectOrigin();
      return originIsNotEmpty && nextNearbyIndex < nearbySize;
    }

    @Override
    public Object next() {
      selectOrigin(); // Ensure origin is selected and cached
      Object result = getDistanceMatrix().getDestination(origin, nextNearbyIndex);
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
