package ai.greycos.solver.core.impl.heuristic.selector.value.nearby;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.LongSupplier;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.supply.Demand;
import ai.greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrixDemand;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

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
  protected @Nullable NearbyDistanceMatrix<Object, Object> distanceMatrix;
  protected final int maxNearbySortSize;
  protected final boolean eagerInitialization;
  private boolean eagerInitialized = false;
  private @Nullable Demand<NearbyDistanceMatrix<Object, Object>> distanceMatrixDemand;

  protected AbstractNearbyValueSelector(
      @NonNull IterableValueSelector<Solution_> childValueSelector,
      @NonNull ReplayingSelector_ replayingSelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection) {
    this(
        childValueSelector,
        replayingSelector,
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        Integer.MAX_VALUE,
        false);
  }

  protected AbstractNearbyValueSelector(
      @NonNull IterableValueSelector<Solution_> childValueSelector,
      @NonNull ReplayingSelector_ replayingSelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection,
      int maxNearbySortSize,
      boolean eagerInitialization) {
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
    this.maxNearbySortSize = maxNearbySortSize;
    this.eagerInitialization = eagerInitialization;
    phaseLifecycleSupport.addEventListener(childValueSelector);
    phaseLifecycleSupport.addEventListener(replayingSelector);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    super.solvingStarted(solverScope);
    var supplyManager = solverScope.getScoreDirector().getSupplyManager();
    @SuppressWarnings("unchecked")
    var castedDistanceMeter = (NearbyDistanceMeter<Object, Object>) nearbyDistanceMeter;
    var variableDescriptor = childValueSelector.getVariableDescriptor();
    var matrixDemand =
        new NearbyDistanceMatrixDemand<>(
            castedDistanceMeter,
            nearbyRandom,
            calculateEffectiveMaxNearbySortSize(),
            false,
            childValueSelector,
            replayingSelector,
            getClass().getSimpleName(),
            this::calculateOriginSizeEstimate,
            origin -> filterAnchors(childValueSelector.iterator(origin), variableDescriptor),
            this::calculateDestinationSize);
    if (supplyManager == null) {
      this.distanceMatrix = matrixDemand.createExternalizedSupply(null);
      this.distanceMatrixDemand = null;
    } else {
      @SuppressWarnings({"rawtypes", "unchecked"})
      Object supplied =
          ((ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager) supplyManager)
              .demand((Demand) matrixDemand);
      if (supplied instanceof NearbyDistanceMatrix<?, ?> suppliedMatrix) {
        @SuppressWarnings("unchecked")
        var castedMatrix = (NearbyDistanceMatrix<Object, Object>) suppliedMatrix;
        this.distanceMatrix = castedMatrix;
        this.distanceMatrixDemand = matrixDemand;
      } else {
        this.distanceMatrix = matrixDemand.createExternalizedSupply(supplyManager);
        this.distanceMatrixDemand = null;
      }
    }
    eagerInitialized = false;
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    if (eagerInitialization && !eagerInitialized) {
      initializeAllOrigins();
      eagerInitialized = true;
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    super.solvingEnded(solverScope);
    var supplyManager = solverScope.getScoreDirector().getSupplyManager();
    if (distanceMatrixDemand != null && supplyManager != null) {
      supplyManager.cancel(distanceMatrixDemand);
      distanceMatrixDemand = null;
    }
    distanceMatrix = null;
    eagerInitialized = false;
  }

  private void initializeAllOrigins() {
    Iterator<Object> originIterator = endingOriginIteratorForInitialization();
    if (originIterator == null) {
      return;
    }
    while (originIterator.hasNext()) {
      getDistanceMatrix().addAllDestinations(originIterator.next());
    }
  }

  protected @Nullable Iterator<Object> endingOriginIteratorForInitialization() {
    return null;
  }

  protected final int getNearbySize(@NonNull Object origin) {
    return getDistanceMatrix().getDestinationSize(origin);
  }

  protected final @NonNull NearbyDistanceMatrix<Object, Object> getDistanceMatrix() {
    if (distanceMatrix == null) {
      throw new IllegalStateException(
          "distanceMatrix is null. Make sure solvingStarted() was called.");
    }
    return distanceMatrix;
  }

  protected int getDestinationSizeMaximumAdjustment() {
    return 0;
  }

  private int calculateOriginSizeEstimate() {
    if (replayingSelector instanceof EntitySelector<?> entitySelector) {
      return safeToIntSize(entitySelector::getSize, "originEntitySelector");
    }
    if (replayingSelector instanceof IterableValueSelector<?> valueSelector) {
      return safeToIntSize(valueSelector::getSize, "originValueSelector");
    }
    return 100;
  }

  private int calculateDestinationSize(@NonNull Object origin) {
    return toIntSize(childValueSelector.getSize(origin), "childValueSelector");
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

  private static int toIntSize(long size, String selectorLabel) {
    if (size > Integer.MAX_VALUE) {
      throw new IllegalStateException(
          "The "
              + selectorLabel
              + " has a size ("
              + size
              + ") which is higher than Integer.MAX_VALUE.");
    }
    return (int) size;
  }

  private static int safeToIntSize(LongSupplier sizeSupplier, String selectorLabel) {
    try {
      return toIntSize(sizeSupplier.getAsLong(), selectorLabel);
    } catch (NullPointerException ignored) {
      // Some selectors initialize their size caches in phaseStarted().
      // During solvingStarted() this estimate is best-effort only.
      return 100;
    }
  }

  private int calculateEffectiveMaxNearbySortSize() {
    if (!randomSelection || nearbyRandom == null) {
      return maxNearbySortSize;
    }
    int distributionMaximum = nearbyRandom.getOverallSizeMaximum();
    int adjustment = getDestinationSizeMaximumAdjustment();
    if (adjustment > 0 && distributionMaximum < Integer.MAX_VALUE) {
      if (distributionMaximum > Integer.MAX_VALUE - adjustment) {
        distributionMaximum = Integer.MAX_VALUE;
      } else {
        distributionMaximum += adjustment;
      }
    }
    return Math.min(maxNearbySortSize, distributionMaximum);
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
        && randomSelection == that.randomSelection
        && maxNearbySortSize == that.maxNearbySortSize
        && eagerInitialization == that.eagerInitialization;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        childValueSelector,
        replayingSelector,
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        maxNearbySortSize,
        eagerInitialization);
  }
}
