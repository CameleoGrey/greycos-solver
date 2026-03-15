package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

import ai.greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.supply.Demand;
import ai.greycos.solver.core.impl.heuristic.selector.AbstractSelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubList;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Nearby sub-list selector based on distance between the first elements of sub-lists. */
public class NearbySubListSelector<Solution_> extends AbstractSelector<Solution_>
    implements SubListSelector<Solution_> {

  private final @NonNull RandomSubListSelector<Solution_> childSubListSelector;
  private final @NonNull SubListSelector<Solution_> originSubListSelector;
  private final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  private final @Nullable NearbyRandom nearbyRandom;
  private final boolean randomSelection;
  private final int maxNearbySortSize;
  private final boolean eagerInitialization;
  private final @NonNull ListVariableDescriptor<Solution_> listVariableDescriptor;

  private @Nullable NearbyDistanceMatrix<Object, Object> distanceMatrix;
  private @Nullable Demand<NearbyDistanceMatrix<Object, Object>> distanceMatrixDemand;
  private @Nullable ListVariableStateSupply<Solution_, Object, Object> listVariableStateSupply;
  private boolean eagerInitialized = false;

  public NearbySubListSelector(
      @NonNull RandomSubListSelector<Solution_> childSubListSelector,
      @NonNull SubListSelector<Solution_> originSubListSelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection,
      int maxNearbySortSize,
      boolean eagerInitialization) {
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
    this.childSubListSelector = childSubListSelector;
    this.originSubListSelector = originSubListSelector;
    this.nearbyDistanceMeter = nearbyDistanceMeter;
    this.nearbyRandom = nearbyRandom;
    this.randomSelection = randomSelection;
    this.maxNearbySortSize = maxNearbySortSize;
    this.eagerInitialization = eagerInitialization;
    this.listVariableDescriptor = childSubListSelector.getVariableDescriptor();
    phaseLifecycleSupport.addEventListener(childSubListSelector);
    phaseLifecycleSupport.addEventListener(originSubListSelector);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    super.solvingStarted(solverScope);
    var supplyManager = solverScope.getScoreDirector().getSupplyManager();
    listVariableStateSupply = supplyManager.demand(listVariableDescriptor.getStateDemand());

    @SuppressWarnings("unchecked")
    var castedDistanceMeter = (NearbyDistanceMeter<Object, Object>) nearbyDistanceMeter;
    var matrixDemand =
        new NearbyDistanceMatrixDemand<>(
            castedDistanceMeter,
            nearbyRandom,
            calculateEffectiveMaxNearbySortSize(),
            true,
            childSubListSelector,
            originSubListSelector,
            getClass().getSimpleName(),
            this::calculateOriginSizeEstimate,
            origin -> childSubListSelector.endingValueIterator(),
            origin -> calculateDestinationSize());
    if (supplyManager == null) {
      distanceMatrix = matrixDemand.createExternalizedSupply(null);
      distanceMatrixDemand = null;
    } else {
      @SuppressWarnings({"rawtypes", "unchecked"})
      Object supplied =
          ((ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager) supplyManager)
              .demand((Demand) matrixDemand);
      if (supplied instanceof NearbyDistanceMatrix<?, ?> suppliedMatrix) {
        @SuppressWarnings("unchecked")
        var castedMatrix = (NearbyDistanceMatrix<Object, Object>) suppliedMatrix;
        distanceMatrix = castedMatrix;
        distanceMatrixDemand = matrixDemand;
      } else {
        distanceMatrix = matrixDemand.createExternalizedSupply(supplyManager);
        distanceMatrixDemand = null;
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
    listVariableStateSupply = null;
    distanceMatrix = null;
    eagerInitialized = false;
  }

  private void initializeAllOrigins() {
    var originValueIterator = originSubListSelector.endingValueIterator();
    while (originValueIterator.hasNext()) {
      getDistanceMatrix().addAllDestinations(originValueIterator.next());
    }
  }

  @Override
  public ListVariableDescriptor<Solution_> getVariableDescriptor() {
    return childSubListSelector.getVariableDescriptor();
  }

  @Override
  public long getSize() {
    return childSubListSelector.getSize();
  }

  @Override
  public boolean isCountable() {
    return childSubListSelector.isCountable();
  }

  @Override
  public boolean isNeverEnding() {
    return randomSelection || childSubListSelector.isNeverEnding();
  }

  @Override
  public @NonNull Iterator<SubList> iterator() {
    if (randomSelection) {
      return new RandomNearbySubListIterator(workingRandom);
    }
    return new OriginalNearbySubListIterator();
  }

  @Override
  public long getValueCount() {
    return childSubListSelector.getValueCount();
  }

  @Override
  public @NonNull Iterator<Object> endingValueIterator() {
    return childSubListSelector.endingValueIterator();
  }

  private @NonNull NearbyDistanceMatrix<Object, Object> getDistanceMatrix() {
    if (distanceMatrix == null) {
      throw new IllegalStateException(
          "distanceMatrix is null. Make sure solvingStarted() was called.");
    }
    return distanceMatrix;
  }

  private @NonNull ListVariableStateSupply<Solution_, Object, Object> getListVariableStateSupply() {
    if (listVariableStateSupply == null) {
      throw new IllegalStateException(
          "listVariableStateSupply is null. Make sure solvingStarted() was called.");
    }
    return listVariableStateSupply;
  }

  private @NonNull Object firstElement(@NonNull SubList subList) {
    return listVariableDescriptor.getElement(subList.entity(), subList.fromIndex());
  }

  private @Nullable SubList buildNearbySubList(
      @NonNull Object origin, int nearbyIndex, @Nullable RandomGenerator random) {
    Object nearbyElement = getDistanceMatrix().getDestination(origin, nearbyIndex);
    var stateSupply = getListVariableStateSupply();
    Object nearbyEntity = stateSupply.getInverseSingleton(nearbyElement);
    Integer nearbyIndexInEntity = stateSupply.getIndex(nearbyElement);
    if (nearbyEntity == null || nearbyIndexInEntity == null) {
      return null;
    }
    int availableListSize = listVariableDescriptor.getListSize(nearbyEntity) - nearbyIndexInEntity;
    int minimumSubListSize = childSubListSelector.getMinimumSubListSize();
    if (availableListSize < minimumSubListSize) {
      return null;
    }
    int maximumSubListSize =
        Math.min(childSubListSelector.getMaximumSubListSize(), availableListSize);
    int subListSize = minimumSubListSize;
    if (random != null && maximumSubListSize > minimumSubListSize) {
      subListSize += random.nextInt(maximumSubListSize - minimumSubListSize + 1);
    }
    return new SubList(nearbyEntity, nearbyIndexInEntity, subListSize);
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

  private int calculateOriginSizeEstimate() {
    return safeToIntSize(originSubListSelector::getValueCount, "originSubListSelector");
  }

  private int calculateDestinationSize() {
    return toIntSize(childSubListSelector.getValueCount(), "childSubListSelector");
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
    return Math.min(maxNearbySortSize, nearbyRandom.getOverallSizeMaximum());
  }

  private class RandomNearbySubListIterator implements Iterator<SubList> {

    private final RandomGenerator random;
    private final Iterator<SubList> replayingOriginIterator;
    private final boolean hasAvailableCandidates;
    private @Nullable SubList originSubList = null;

    private RandomNearbySubListIterator(RandomGenerator random) {
      this.random = random;
      this.replayingOriginIterator = originSubListSelector.iterator();
      this.hasAvailableCandidates =
          childSubListSelector.getValueCount() > 0 && childSubListSelector.getSize() > 0;
    }

    @Override
    public boolean hasNext() {
      return (originSubList != null || replayingOriginIterator.hasNext()) && hasAvailableCandidates;
    }

    @Override
    public SubList next() {
      if (nearbyRandom == null) {
        throw new IllegalStateException("nearbyRandom is null but randomSelection is true");
      }
      if (replayingOriginIterator.hasNext()) {
        originSubList = replayingOriginIterator.next();
      }
      if (originSubList == null) {
        throw new NoSuchElementException();
      }

      Object origin = firstElement(originSubList);
      int nearbySize = getDistanceMatrix().getDestinationSize(origin);
      if (nearbySize <= 0) {
        throw new NoSuchElementException();
      }

      int startIndex = nearbyRandom.nextInt(random, nearbySize);
      for (int offset = 0; offset < nearbySize; offset++) {
        int nearbyIndex = (startIndex + offset) % nearbySize;
        SubList nearbySubList = buildNearbySubList(origin, nearbyIndex, random);
        if (nearbySubList != null) {
          return nearbySubList;
        }
      }
      throw new NoSuchElementException(
          "No valid nearby subList could be built for origin (%s).".formatted(origin));
    }
  }

  private class OriginalNearbySubListIterator implements Iterator<SubList> {

    private final Iterator<SubList> replayingOriginIterator;
    private @Nullable SubList originSubList = null;
    private int nearbySize = -1;
    private int nearbyIndex = 0;
    private boolean originSelected = false;

    private OriginalNearbySubListIterator() {
      this.replayingOriginIterator = originSubListSelector.iterator();
    }

    private void selectOrigin() {
      if (originSelected) {
        return;
      }
      if (replayingOriginIterator.hasNext()) {
        originSubList = replayingOriginIterator.next();
        Object origin = firstElement(originSubList);
        nearbySize = getDistanceMatrix().getDestinationSize(origin);
      }
      originSelected = true;
    }

    @Override
    public boolean hasNext() {
      selectOrigin();
      if (originSubList == null) {
        return false;
      }
      while (nearbyIndex < nearbySize) {
        Object origin = firstElement(originSubList);
        if (buildNearbySubList(origin, nearbyIndex, null) != null) {
          return true;
        }
        nearbyIndex++;
      }
      return false;
    }

    @Override
    public SubList next() {
      selectOrigin();
      if (originSubList == null) {
        throw new NoSuchElementException();
      }
      Object origin = firstElement(originSubList);
      while (nearbyIndex < nearbySize) {
        SubList candidate = buildNearbySubList(origin, nearbyIndex, null);
        nearbyIndex++;
        if (candidate != null) {
          return candidate;
        }
      }
      throw new NoSuchElementException();
    }
  }
}
