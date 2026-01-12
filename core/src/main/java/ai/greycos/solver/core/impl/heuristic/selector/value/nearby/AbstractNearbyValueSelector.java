package ai.greycos.solver.core.impl.heuristic.selector.value.nearby;

import java.util.Iterator;
import java.util.Objects;

import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListener;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Abstract base for nearby value selectors. Uses replaying selector pattern for consistent origin
 * during distance calculation.
 */
abstract class AbstractNearbyValueSelector<
        Solution_, ReplayingSelector_ extends PhaseLifecycleListener<Solution_>>
    extends AbstractDemandEnabledSelector<Solution_> implements IterableValueSelector<Solution_> {

  protected final @NonNull IterableValueSelector<Solution_> childValueSelector;
  protected final @NonNull ReplayingSelector_ replayingSelector;
  protected final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  protected final @Nullable NearbyRandom nearbyRandom;
  protected final boolean randomSelection;
  protected final @NonNull NearbyDistanceMatrix<Object, Object> distanceMatrix;

  protected AbstractNearbyValueSelector(
      @NonNull IterableValueSelector<Solution_> childValueSelector,
      @NonNull ReplayingSelector_ replayingSelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection) {
    this.childValueSelector = childValueSelector;
    this.replayingSelector = replayingSelector;
    this.nearbyDistanceMeter = nearbyDistanceMeter;
    if (randomSelection && nearbyRandom == null) {
      throw new IllegalArgumentException(
          "The selector ("
              + this
              + ") with randomSelection ("
              + randomSelection
              + ") has no nearbyRandom ("
              + nearbyRandom
              + ").");
    }
    this.nearbyRandom = nearbyRandom;
    this.randomSelection = randomSelection;
    // Create distance matrix for caching sorted destinations
    // Excludes anchors from distance-based sorting (they will be appended at the end)
    @SuppressWarnings("unchecked")
    var castedDistanceMeter = (NearbyDistanceMeter<Object, Object>) nearbyDistanceMeter;
    var variableDescriptor = childValueSelector.getVariableDescriptor();
    this.distanceMatrix =
        new NearbyDistanceMatrix<>(
            castedDistanceMeter,
            100, // Initial capacity estimate
            origin -> filterAnchors(childValueSelector.iterator(origin), variableDescriptor),
            origin -> countNonAnchors(childValueSelector.iterator(origin), variableDescriptor));
    phaseLifecycleSupport.addEventListener(childValueSelector);
    phaseLifecycleSupport.addEventListener(replayingSelector);
  }

  private static Iterator<Object> filterAnchors(
      Iterator<Object> iterator, GenuineVariableDescriptor<?> variableDescriptor) {
    return new Iterator<Object>() {
      private Object next = null;
      private boolean hasNext = false;

      private void advance() {
        while (iterator.hasNext()) {
          Object candidate = iterator.next();
          if (!variableDescriptor.isValuePotentialAnchor(candidate)) {
            next = candidate;
            hasNext = true;
            return;
          }
        }
        hasNext = false;
      }

      @Override
      public boolean hasNext() {
        if (!hasNext) {
          advance();
        }
        return hasNext;
      }

      @Override
      public Object next() {
        if (!hasNext()) {
          throw new java.util.NoSuchElementException();
        }
        hasNext = false;
        return next;
      }
    };
  }

  private static int countNonAnchors(
      Iterator<Object> iterator, GenuineVariableDescriptor<?> variableDescriptor) {
    int count = 0;
    while (iterator.hasNext()) {
      Object value = iterator.next();
      if (!variableDescriptor.isValuePotentialAnchor(value)) {
        count++;
      }
    }
    return count;
  }

  @Override
  public @NonNull GenuineVariableDescriptor<Solution_> getVariableDescriptor() {
    return childValueSelector.getVariableDescriptor();
  }

  @Override
  public boolean isCountable() {
    return true;
  }

  @Override
  public long getSize(Object entity) {
    return childValueSelector.getSize(entity);
  }

  @Override
  public long getSize() {
    return childValueSelector.getSize();
  }

  @Override
  public abstract @NonNull Iterator<Object> iterator(@NonNull Object entity);

  @Override
  public abstract @NonNull Iterator<Object> endingIterator(@NonNull Object entity);

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AbstractNearbyValueSelector<?, ?> that)) {
      return false;
    }
    return Objects.equals(childValueSelector, that.childValueSelector)
        && Objects.equals(replayingSelector, that.replayingSelector)
        && Objects.equals(nearbyDistanceMeter, that.nearbyDistanceMeter)
        && Objects.equals(nearbyRandom, that.nearbyRandom)
        && randomSelection == that.randomSelection;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        childValueSelector, replayingSelector, nearbyDistanceMeter, nearbyRandom, randomSelection);
  }
}
