package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.random.RandomGenerator;

import ai.greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
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
  private final int minimumSubListSize;
  private final int maximumSubListSize;

  private @Nullable NearbyDistanceMatrix<Object, Object> distanceMatrix;
  private @Nullable NearbyDistanceMatrixDemand<Object, Object> distanceMatrixDemand;
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
    if (maxNearbySortSize < 1) {
      throw new IllegalArgumentException(
          "The maxNearbySortSize (%d) must be at least 1.".formatted(maxNearbySortSize));
    }
    this.maxNearbySortSize = maxNearbySortSize;
    this.eagerInitialization = eagerInitialization;
    this.listVariableDescriptor = childSubListSelector.getVariableDescriptor();
    this.minimumSubListSize = childSubListSelector.getMinimumSubListSize();
    this.maximumSubListSize = childSubListSelector.getMaximumSubListSize();
    phaseLifecycleSupport.addEventListener(childSubListSelector);
    phaseLifecycleSupport.addEventListener(originSubListSelector);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    if (distanceMatrix != null || distanceMatrixDemand != null || listVariableStateSupply != null) {
      throw new IllegalStateException("The nearby subList selector is already solving.");
    }
    super.solvingStarted(solverScope);
    var supplyManager = solverScope.getScoreDirector().getSupplyManager();
    listVariableStateSupply = supplyManager.demand(listVariableDescriptor.getStateDemand());
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
            childSubListSelector,
            originSubListSelector,
            getClass().getSimpleName(),
            this::calculateOriginSizeEstimate,
            origin -> childSubListSelector.endingValueIterator(),
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
    int nearbyIndexInEntity = stateSupply.getIndexOrElse(nearbyElement, -1);
    if (nearbyEntity == null || nearbyIndexInEntity < 0) {
      return null;
    }
    int availableListSize = listVariableDescriptor.getListSize(nearbyEntity) - nearbyIndexInEntity;
    if (availableListSize < minimumSubListSize) {
      return null;
    }
    int maximumSubListSize = Math.min(this.maximumSubListSize, availableListSize);
    int subListSize = minimumSubListSize;
    if (random != null && maximumSubListSize > minimumSubListSize) {
      subListSize += random.nextInt(maximumSubListSize - minimumSubListSize + 1);
    }
    return new SubList(nearbyEntity, nearbyIndexInEntity, subListSize);
  }

  private boolean isNearbySubListCandidateValid(@NonNull Object origin, int nearbyIndex) {
    Object nearbyElement = getDistanceMatrix().getDestination(origin, nearbyIndex);
    var stateSupply = getListVariableStateSupply();
    Object nearbyEntity = stateSupply.getInverseSingleton(nearbyElement);
    int nearbyIndexInEntity = stateSupply.getIndexOrElse(nearbyElement, -1);
    if (nearbyEntity == null || nearbyIndexInEntity < 0) {
      return false;
    }
    int availableListSize = listVariableDescriptor.getListSize(nearbyEntity) - nearbyIndexInEntity;
    return availableListSize >= minimumSubListSize;
  }

  private int[] buildNextValidNearbyIndexMap(@NonNull Object origin, int nearbySize) {
    int[] nextValidNearbyIndexMap = new int[nearbySize];
    int nextValidIndex = -1;
    for (int nearbyIndex = nearbySize - 1; nearbyIndex >= 0; nearbyIndex--) {
      if (isNearbySubListCandidateValid(origin, nearbyIndex)) {
        nextValidIndex = nearbyIndex;
      }
      nextValidNearbyIndexMap[nearbyIndex] = nextValidIndex;
    }
    if (nextValidIndex == -1) {
      return nextValidNearbyIndexMap;
    }
    int firstValidNearbyIndex = nextValidNearbyIndexMap[0];
    for (int nearbyIndex = 0; nearbyIndex < nearbySize; nearbyIndex++) {
      if (nextValidNearbyIndexMap[nearbyIndex] == -1) {
        nextValidNearbyIndexMap[nearbyIndex] = firstValidNearbyIndex;
      }
    }
    return nextValidNearbyIndexMap;
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

  private int calculateOriginSizeEstimate() {
    return toIntSize(originSubListSelector.getValueCount(), "originSubListSelector");
  }

  private int calculateDestinationSize() {
    return toIntSize(childSubListSelector.getValueCount(), "childSubListSelector");
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

    private @Nullable SubList originSubList = null;
    private @Nullable SubList cachedOriginSubList = null;
    private @Nullable Object cachedOrigin = null;
    private int cachedNearbySize = -1;
    private int[] cachedNextValidNearbyIndexMap = new int[0];

    private RandomNearbySubListIterator(RandomGenerator random) {
      this.random = random;
      this.replayingOriginIterator = originSubListSelector.iterator();
    }

    @Override
    public boolean hasNext() {
      return originSubList != null || replayingOriginIterator.hasNext();
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

      if (originSubList != cachedOriginSubList) {
        cachedOriginSubList = originSubList;
        cachedOrigin = firstElement(originSubList);
        cachedNearbySize = getDistanceMatrix().getDestinationSize(cachedOrigin);
        cachedNextValidNearbyIndexMap =
            buildNextValidNearbyIndexMap(cachedOrigin, cachedNearbySize);
      }
      if (cachedNearbySize <= 0 || cachedOrigin == null) {
        throw new NoSuchElementException();
      }

      int startIndex = nearbyRandom.nextInt(random, cachedNearbySize);
      int nearbyIndex = cachedNextValidNearbyIndexMap[startIndex];
      if (nearbyIndex >= 0) {
        SubList nearbySubList = buildNearbySubList(cachedOrigin, nearbyIndex, random);
        if (nearbySubList != null) {
          return nearbySubList;
        }
      }
      cachedNextValidNearbyIndexMap = buildNextValidNearbyIndexMap(cachedOrigin, cachedNearbySize);
      nearbyIndex = cachedNextValidNearbyIndexMap[startIndex];
      if (nearbyIndex >= 0) {
        SubList nearbySubList = buildNearbySubList(cachedOrigin, nearbyIndex, random);
        if (nearbySubList != null) {
          return nearbySubList;
        }
      }

      throw new NoSuchElementException(
          "No valid nearby subList could be built for origin (%s).".formatted(cachedOrigin));
    }
  }

  private class OriginalNearbySubListIterator implements Iterator<SubList> {

    private final Iterator<SubList> replayingOriginIterator;
    private @Nullable SubList originSubList = null;
    private @Nullable Object origin = null;
    private int nearbySize = -1;
    private int nearbyIndex = 0;
    private boolean originSelected = false;
    private @Nullable SubList upcomingSubList = null;

    private OriginalNearbySubListIterator() {
      this.replayingOriginIterator = originSubListSelector.iterator();
    }

    private void selectOrigin() {
      if (originSelected) {
        return;
      }
      if (replayingOriginIterator.hasNext()) {
        originSubList = replayingOriginIterator.next();
        origin = firstElement(originSubList);
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
      if (upcomingSubList != null) {
        return true;
      }
      while (nearbyIndex < nearbySize) {
        SubList candidate = buildNearbySubList(origin, nearbyIndex, null);
        nearbyIndex++;
        if (candidate != null) {
          upcomingSubList = candidate;
          return true;
        }
      }
      return false;
    }

    @Override
    public SubList next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      SubList selected = upcomingSubList;
      upcomingSubList = null;
      return selected;
    }
  }
}
