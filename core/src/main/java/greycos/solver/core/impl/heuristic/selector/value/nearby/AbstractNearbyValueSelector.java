package greycos.solver.core.impl.heuristic.selector.value.nearby;

import java.util.Iterator;
import java.util.Objects;

import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrixDemand;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

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
  private @Nullable NearbyDistanceMatrixDemand<Object, Object> distanceMatrixDemand;

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
    if (maxNearbySortSize < 1) {
      throw new IllegalArgumentException(
          "The maxNearbySortSize (%d) must be at least 1.".formatted(maxNearbySortSize));
    }
    this.maxNearbySortSize = maxNearbySortSize;
    this.eagerInitialization = eagerInitialization;
    phaseLifecycleSupport.addEventListener(childValueSelector);
    phaseLifecycleSupport.addEventListener(replayingSelector);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    if (distanceMatrix != null || distanceMatrixDemand != null) {
      throw new IllegalStateException("The nearby value selector is already solving.");
    }
    super.solvingStarted(solverScope);
    distanceMatrix = null;
    distanceMatrixDemand = null;
    eagerInitialized = false;
  }

  private void initializeDistanceMatrix(@NonNull SupplyManager supplyManager) {
    @SuppressWarnings("unchecked")
    var castedDistanceMeter = (NearbyDistanceMeter<Object, Object>) nearbyDistanceMeter;
    var variableDescriptor = childValueSelector.getVariableDescriptor();
    distanceMatrixDemand =
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
    distanceMatrix = supplyManager.demand(distanceMatrixDemand);
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    if (distanceMatrix == null) {
      if (distanceMatrixDemand != null) {
        throw new IllegalStateException(
            "The nearby distance matrix demand exists without its supply.");
      }
      initializeDistanceMatrix(phaseScope.getScoreDirector().getSupplyManager());
    }
    if (eagerInitialization && !eagerInitialized) {
      initializeAllOrigins();
      eagerInitialized = true;
    }
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    eagerInitialized = false;
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    super.solvingEnded(solverScope);
    var supplyManager = solverScope.getScoreDirector().getSupplyManager();
    if (distanceMatrixDemand != null) {
      if (!supplyManager.cancel(distanceMatrixDemand)) {
        throw new IllegalStateException("The nearby distance matrix demand is not active.");
      }
      distanceMatrixDemand = null;
    }
    distanceMatrix = null;
    eagerInitialized = false;
  }

  private void initializeAllOrigins() {
    Iterator<Object> originIterator = endingOriginIteratorForInitialization();
    while (originIterator.hasNext()) {
      getDistanceMatrix().addAllDestinations(originIterator.next());
    }
  }

  protected abstract @NonNull Iterator<Object> endingOriginIteratorForInitialization();

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
      return toIntSize(entitySelector.getSize(), "originEntitySelector");
    }
    if (replayingSelector instanceof IterableValueSelector<?> valueSelector) {
      return toIntSize(valueSelector.getSize(), "originValueSelector");
    }
    throw new IllegalStateException(
        "The replayingSelector (%s) is neither an EntitySelector nor an IterableValueSelector."
            .formatted(replayingSelector));
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
    if (size < 0 || size > Integer.MAX_VALUE) {
      throw new IllegalStateException(
          "The "
              + selectorLabel
              + " has a size ("
              + size
              + ") outside the supported range [0, Integer.MAX_VALUE].");
    }
    return (int) size;
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
