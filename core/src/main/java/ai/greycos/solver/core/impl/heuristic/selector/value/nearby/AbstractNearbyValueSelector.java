package ai.greycos.solver.core.impl.heuristic.selector.value.nearby;

import java.util.Iterator;
import java.util.Objects;

import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.spatial.SpatialNearbyDistanceMatrix;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Abstract base class for nearby value selectors.
 *
 * <p>This class provides the foundation for nearby selection with support for both standard
 * distance matrix and spatial-indexed distance matrix for improved performance.
 *
 * <p><b>Spatial Indexing:</b> When configured with a spatial-indexed distance matrix, the selector
 * can efficiently find nearby destinations using KD-tree queries instead of linear scans, providing
 * O(log n) performance instead of O(n) for large datasets.
 *
 * @param <Solution_> the solution type
 */
abstract class AbstractNearbyValueSelector<Solution_>
    extends AbstractDemandEnabledSelector<Solution_> implements IterableValueSelector<Solution_> {

  protected final @NonNull IterableValueSelector<Solution_> childValueSelector;
  protected final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  protected final @Nullable NearbyRandom nearbyRandom;
  protected final boolean randomSelection;
  protected final @Nullable SpatialNearbyDistanceMatrix<?, ?> spatialDistanceMatrix;

  protected AbstractNearbyValueSelector(
      @NonNull IterableValueSelector<Solution_> childValueSelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection) {
    this(childValueSelector, nearbyDistanceMeter, nearbyRandom, randomSelection, null);
  }

  protected AbstractNearbyValueSelector(
      @NonNull IterableValueSelector<Solution_> childValueSelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection,
      @Nullable SpatialNearbyDistanceMatrix<?, ?> spatialDistanceMatrix) {
    this.childValueSelector = childValueSelector;
    this.nearbyDistanceMeter = nearbyDistanceMeter;
    this.nearbyRandom = nearbyRandom;
    this.randomSelection = randomSelection;
    this.spatialDistanceMatrix = spatialDistanceMatrix;
    phaseLifecycleSupport.addEventListener(childValueSelector);
  }

  /**
   * Checks if this selector uses spatial indexing.
   *
   * @return true if spatial indexing is enabled, false otherwise
   */
  protected boolean isSpatialIndexingEnabled() {
    return spatialDistanceMatrix != null;
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
    if (!(o instanceof AbstractNearbyValueSelector<?> that)) {
      return false;
    }
    return Objects.equals(childValueSelector, that.childValueSelector)
        && Objects.equals(nearbyDistanceMeter, that.nearbyDistanceMeter)
        && Objects.equals(nearbyRandom, that.nearbyRandom)
        && randomSelection == that.randomSelection
        && Objects.equals(spatialDistanceMatrix, that.spatialDistanceMatrix);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        childValueSelector,
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        spatialDistanceMatrix);
  }
}
