package greycos.solver.core.impl.heuristic.selector.value.nearby;

import java.util.Iterator;

import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicReplayingEntitySelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Nearby value selector using an entity as origin. Filters and reorders destination values by
 * distance from an origin entity.
 */
public final class NearEntityNearbyValueSelector<Solution_>
    extends AbstractNearbyValueSelector<Solution_, EntitySelector<Solution_>> {

  public NearEntityNearbyValueSelector(
      @NonNull IterableValueSelector<Solution_> childValueSelector,
      @NonNull EntitySelector<Solution_> originEntitySelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection) {
    this(
        childValueSelector,
        originEntitySelector,
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        Integer.MAX_VALUE,
        false);
  }

  public NearEntityNearbyValueSelector(
      @NonNull IterableValueSelector<Solution_> childValueSelector,
      @NonNull EntitySelector<Solution_> originEntitySelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection,
      int maxNearbySortSize,
      boolean eagerInitialization) {
    super(
        childValueSelector,
        castToMimicReplayingEntitySelector(originEntitySelector),
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        maxNearbySortSize,
        eagerInitialization);
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
  protected @NonNull Iterator<Object> endingOriginIteratorForInitialization() {
    return replayingSelector.endingIterator();
  }

  @Override
  protected Iterator<Object> originIterator(@Nullable Object entity) {
    return replayingSelector.iterator();
  }

  @Override
  protected boolean excludeOrigin() {
    return true;
  }

  @Override
  public String toString() {
    return "NearEntityNearbyValueSelector(" + getVariableDescriptor().getVariableName() + ")";
  }
}
