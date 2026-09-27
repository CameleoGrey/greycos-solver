package greycos.solver.core.impl.heuristic.selector.value.nearby;

import java.util.Iterator;

import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.mimic.MimicReplayingValueSelector;

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
  protected @NonNull Iterator<Object> endingOriginIteratorForInitialization() {
    return replayingSelector.endingIterator(null);
  }

  @Override
  protected Iterator<Object> originIterator(@Nullable Object entity) {
    return entity == null ? replayingSelector.iterator() : replayingSelector.iterator(entity);
  }

  @Override
  public String toString() {
    return "NearValueNearbyValueSelector(" + getVariableDescriptor().getVariableName() + ")";
  }
}
