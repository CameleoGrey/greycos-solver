package greycos.solver.core.impl.heuristic.selector.entity.nearby;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.Objects;

import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import greycos.solver.core.impl.heuristic.selector.common.iterator.ListIterable;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrixDemand;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Abstract base for nearby entity selectors. */
abstract class AbstractNearbyEntitySelector<Solution_>
    extends AbstractDemandEnabledSelector<Solution_> implements EntitySelector<Solution_> {

  protected final @NonNull EntitySelector<Solution_> childEntitySelector;
  protected final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  protected final @Nullable NearbyRandom nearbyRandom;
  protected final boolean randomSelection;
  protected final int maxNearbySortSize;
  protected final boolean eagerInitialization;
  private final @NonNull Object originSelectorKey;
  private final @NonNull String demandType;
  private boolean eagerInitialized = false;

  protected @Nullable NearbyDistanceMatrix<Object, Object> distanceMatrix;
  private @Nullable NearbyDistanceMatrixDemand<Object, Object> distanceMatrixDemand;

  protected AbstractNearbyEntitySelector(
      @NonNull EntitySelector<Solution_> childEntitySelector,
      @NonNull Object originSelectorKey,
      @NonNull String demandType,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection) {
    this(
        childEntitySelector,
        originSelectorKey,
        demandType,
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        Integer.MAX_VALUE,
        false);
  }

  protected AbstractNearbyEntitySelector(
      @NonNull EntitySelector<Solution_> childEntitySelector,
      @NonNull Object originSelectorKey,
      @NonNull String demandType,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection,
      int maxNearbySortSize,
      boolean eagerInitialization) {
    this.childEntitySelector = childEntitySelector;
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
    this.originSelectorKey = originSelectorKey;
    this.demandType = demandType;
    phaseLifecycleSupport.addEventListener(childEntitySelector);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    if (distanceMatrix != null || distanceMatrixDemand != null) {
      throw new IllegalStateException("The nearby entity selector is already solving.");
    }
    super.solvingStarted(solverScope);
    distanceMatrix = null;
    distanceMatrixDemand = null;
    eagerInitialized = false;
  }

  private void initializeDistanceMatrix(@NonNull SupplyManager supplyManager) {
    @SuppressWarnings("unchecked")
    var castedDistanceMeter = (NearbyDistanceMeter<Object, Object>) nearbyDistanceMeter;
    distanceMatrixDemand =
        new NearbyDistanceMatrixDemand<>(
            castedDistanceMeter,
            nearbyRandom,
            calculateEffectiveMaxNearbySortSize(),
            true,
            childEntitySelector,
            originSelectorKey,
            demandType,
            this::calculateOriginSizeEstimate,
            origin -> childEntitySelector.endingIterator(),
            origin -> calculateDestinationSize());
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

  protected abstract @NonNull Iterator<?> endingOriginIterator();

  protected final @NonNull NearbyDistanceMatrix<Object, Object> getDistanceMatrix() {
    if (distanceMatrix == null) {
      throw new IllegalStateException(
          "distanceMatrix is null. Make sure solvingStarted() was called.");
    }
    return distanceMatrix;
  }

  protected final int getNearbySize(@NonNull Object origin) {
    return getDistanceMatrix().getDestinationSize(origin);
  }

  protected final Object getNearbyDestination(@NonNull Object origin, int nearbyIndex) {
    return getDistanceMatrix().getDestination(origin, nearbyIndex);
  }

  protected int getDestinationSizeMaximumAdjustment() {
    return 0;
  }

  private void initializeAllOrigins() {
    var originIterator = endingOriginIterator();
    while (originIterator.hasNext()) {
      getDistanceMatrix().addAllDestinations(originIterator.next());
    }
  }

  private int calculateOriginSizeEstimate() {
    if (originSelectorKey instanceof EntitySelector<?> originEntitySelector) {
      return toIntSize(originEntitySelector.getSize(), "originEntitySelector");
    }
    if (originSelectorKey instanceof IterableValueSelector<?> originValueSelector) {
      return toIntSize(originValueSelector.getSize(), "originValueSelector");
    }
    throw new IllegalStateException(
        "The originSelectorKey (%s) is neither an EntitySelector nor an IterableValueSelector."
            .formatted(originSelectorKey));
  }

  private int calculateDestinationSize() {
    return toIntSize(childEntitySelector.getSize(), "childEntitySelector");
  }

  protected static int toIntSize(long size, String selectorLabel) {
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
  public EntityDescriptor<Solution_> getEntityDescriptor() {
    return childEntitySelector.getEntityDescriptor();
  }

  @Override
  public long getSize() {
    return childEntitySelector.getSize();
  }

  @Override
  public boolean isNeverEnding() {
    return childEntitySelector.isNeverEnding();
  }

  @Override
  public Iterator<Object> endingIterator() {
    return childEntitySelector.endingIterator();
  }

  @Override
  public ListIterator<Object> listIterator() {
    if (childEntitySelector instanceof ListIterable) {
      return ((ListIterable<Object>) childEntitySelector).listIterator();
    }
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " does not support listIterator()");
  }

  @Override
  public ListIterator<Object> listIterator(int index) {
    if (childEntitySelector instanceof ListIterable) {
      return ((ListIterable<Object>) childEntitySelector).listIterator(index);
    }
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " does not support listIterator(int)");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AbstractNearbyEntitySelector<?> that)) {
      return false;
    }
    return Objects.equals(childEntitySelector, that.childEntitySelector)
        && Objects.equals(nearbyDistanceMeter, that.nearbyDistanceMeter)
        && Objects.equals(nearbyRandom, that.nearbyRandom)
        && randomSelection == that.randomSelection
        && maxNearbySortSize == that.maxNearbySortSize
        && eagerInitialization == that.eagerInitialization;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        childEntitySelector,
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        maxNearbySortSize,
        eagerInitialization);
  }
}
