package greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import greycos.solver.core.impl.heuristic.selector.AbstractSelector;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.neighborhood.stream.FilteringIterator;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;

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
  private @Nullable SupplyManager distanceMatrixSupplyManager;
  private @Nullable ListVariableState<Solution_, Object, Object> listVariableState;
  private @Nullable InnerScoreDirector<Solution_, ?> scoreDirector;
  private boolean eagerInitialized = false;
  private @Nullable IterableValueSelector<Solution_> populationValueSelector;
  private @Nullable SelectionFilter<Solution_, Object> selectionFilter;
  private @Nullable Supplier<Set<Object>> membershipSupplier;

  public void configureSelectionSource(
      NearbySelectionSource<Solution_, IterableValueSelector<Solution_>> source) {
    populationValueSelector = source.populationSelector();
    selectionFilter = source.liveFilter();
    membershipSupplier = source.membershipSupplier();
  }

  private Iterator<Object> endingDestinations() {
    var iterator =
        populationValueSelector == null
            ? childSubListSelector.endingValueIterator()
            : populationValueSelector.endingIterator(null);
    if (!listVariableDescriptor.supportsPinning()) {
      return iterator;
    }
    return new FilteringIterator<>(iterator, value -> !getListVariableState().isPinned(value));
  }

  private boolean acceptsCandidate(Object candidate, @Nullable Set<Object> membership) {
    return (membership == null || membership.contains(candidate))
        && (selectionFilter == null
            || selectionFilter.accept(Objects.requireNonNull(scoreDirector), candidate));
  }

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
    if (distanceMatrix != null || distanceMatrixDemand != null || listVariableState != null) {
      throw new IllegalStateException("The nearby subList selector is already solving.");
    }
    super.solvingStarted(solverScope);
    distanceMatrix = null;
    distanceMatrixDemand = null;
    eagerInitialized = false;
  }

  private void initializeDistanceMatrix(@NonNull SupplyManager supplyManager) {
    @SuppressWarnings("unchecked")
    var castedDistanceMeter = (NearbyDistanceMeter<Object, Object>) nearbyDistanceMeter;
    var demand =
        new NearbyDistanceMatrixDemand<>(
            castedDistanceMeter,
            nearbyRandom,
            // Assignment and the number of following elements may change after every move.
            // Retain all distances when those conditions can remove candidates before the cap.
            requiresDynamicEligibility()
                ? Integer.MAX_VALUE
                : calculateEffectiveMaxNearbySortSize(),
            false,
            populationValueSelector == null ? childSubListSelector : populationValueSelector,
            originSubListSelector,
            getClass().getSimpleName(),
            this::calculateOriginSizeEstimate,
            origin -> endingDestinations(),
            origin -> calculateDestinationSize());
    distanceMatrix = supplyManager.demand(demand);
    distanceMatrixDemand = demand;
    distanceMatrixSupplyManager = supplyManager;
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    try {
      super.phaseStarted(phaseScope);
      scoreDirector = phaseScope.getScoreDirector();
      listVariableState = scoreDirector.getListVariableState(listVariableDescriptor);
      if (distanceMatrix == null) {
        initializeDistanceMatrix(phaseScope.getScoreDirector().getSupplyManager());
      }
      if (eagerInitialization && !eagerInitialized) {
        initializeAllOrigins();
        eagerInitialized = true;
      }
    } catch (RuntimeException | Error failure) {
      try {
        releaseDistanceMatrix();
      } catch (RuntimeException | Error cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    try {
      super.phaseEnded(phaseScope);
    } finally {
      releaseDistanceMatrix();
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    try {
      super.solvingEnded(solverScope);
    } finally {
      releaseDistanceMatrix();
    }
  }

  private void releaseDistanceMatrix() {
    var demand = distanceMatrixDemand;
    var supplyManager = distanceMatrixSupplyManager;
    // The solver may already use another director. Release through the owner and clear first
    // so repeated cleanup remains safe, including when cancellation itself fails.
    distanceMatrixDemand = null;
    distanceMatrixSupplyManager = null;
    distanceMatrix = null;
    listVariableState = null;
    scoreDirector = null;
    eagerInitialized = false;
    if (demand != null && !Objects.requireNonNull(supplyManager).cancel(demand)) {
      throw new IllegalStateException("The nearby distance matrix demand is not active.");
    }
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
    return randomSelection;
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

  private @NonNull ListVariableState<Solution_, Object, Object> getListVariableState() {
    if (listVariableState == null) {
      throw new IllegalStateException(
          "listVariableState is null. Make sure solvingStarted() was called.");
    }
    return listVariableState;
  }

  private @NonNull Object firstElement(@NonNull SubList subList) {
    return listVariableDescriptor.getElement(subList.entity(), subList.fromIndex());
  }

  private @Nullable SubList buildNearbySubList(
      @NonNull Object origin,
      int nearbyIndex,
      @Nullable RandomGenerator random,
      @NonNull SubList originSubList,
      @Nullable Set<Object> membership) {
    Object nearbyElement = getDistanceMatrix().getDestination(origin, nearbyIndex);
    if (random == null && !acceptsCandidate(nearbyElement, membership)) {
      return null;
    }
    var stateSupply = getListVariableState();
    Object nearbyEntity = stateSupply.getInverseSingleton(nearbyElement);
    int nearbyIndexInEntity = stateSupply.getIndexOrElse(nearbyElement, -1);
    if (nearbyEntity == null || nearbyIndexInEntity < 0) {
      return null;
    }
    int availableListSize = listVariableDescriptor.getListSize(nearbyEntity) - nearbyIndexInEntity;
    if (availableListSize < minimumSubListSize) {
      return null;
    }
    int maximumSubListSize =
        maximumAcceptedSubListSize(
            originSubList,
            nearbyEntity,
            nearbyIndexInEntity,
            Math.min(this.maximumSubListSize, availableListSize));
    if (maximumSubListSize < minimumSubListSize) {
      return null;
    }
    int subListSize = minimumSubListSize;
    if (random != null && maximumSubListSize > minimumSubListSize) {
      subListSize += random.nextInt(maximumSubListSize - minimumSubListSize + 1);
    }
    return new SubList(nearbyEntity, nearbyIndexInEntity, subListSize);
  }

  private boolean isNearbySubListCandidateValid(
      @NonNull Object origin,
      int nearbyIndex,
      @NonNull SubList originSubList,
      @Nullable Set<Object> membership) {
    Object nearbyElement = getDistanceMatrix().getDestination(origin, nearbyIndex);
    if (!acceptsCandidate(nearbyElement, membership)) {
      return false;
    }
    var stateSupply = getListVariableState();
    Object nearbyEntity = stateSupply.getInverseSingleton(nearbyElement);
    int nearbyIndexInEntity = stateSupply.getIndexOrElse(nearbyElement, -1);
    if (nearbyEntity == null || nearbyIndexInEntity < 0) {
      return false;
    }
    int availableListSize = listVariableDescriptor.getListSize(nearbyEntity) - nearbyIndexInEntity;
    return availableListSize >= minimumSubListSize
        && maximumAcceptedSubListSize(
                originSubList, nearbyEntity, nearbyIndexInEntity, minimumSubListSize)
            >= minimumSubListSize;
  }

  private int maximumAcceptedSubListSize(
      SubList originSubList, Object destinationEntity, int destinationIndex, int maximumSize) {
    if (listVariableDescriptor.canExtractValueRangeFromSolution()) {
      return maximumSize;
    }
    var valueRangeManager = Objects.requireNonNull(scoreDirector).getValueRangeManager();
    var destinationRange =
        valueRangeManager.getFromEntity(
            listVariableDescriptor.getValueRangeDescriptor(), destinationEntity);
    for (int index = originSubList.fromIndex(); index < originSubList.getToIndex(); index++) {
      if (!destinationRange.contains(
          listVariableDescriptor.getElement(originSubList.entity(), index))) {
        return 0;
      }
    }
    var sourceRange =
        valueRangeManager.getFromEntity(
            listVariableDescriptor.getValueRangeDescriptor(), originSubList.entity());
    // Every selected length must be legal in both directions. A value outside the source range
    // ends the eligible prefix, even if later values would individually be accepted.
    for (int length = 0; length < maximumSize; length++) {
      if (!sourceRange.contains(
          listVariableDescriptor.getElement(destinationEntity, destinationIndex + length))) {
        return length;
      }
    }
    return maximumSize;
  }

  private boolean requiresDynamicEligibility() {
    return selectionFilter != null
        || membershipSupplier != null
        || minimumSubListSize > 1
        || listVariableDescriptor.allowsUnassignedValues()
        || !listVariableDescriptor.canExtractValueRangeFromSolution();
  }

  private record NearbyCandidates(int[] indices, int populationSize) {}

  private NearbyCandidates collectEligibleNearbyIndices(Object origin, SubList originSubList) {
    var matrix = getDistanceMatrix();
    int nearbySize = matrix.getDestinationSize(origin);
    int[] indices = new int[Math.min(nearbySize, calculateEffectiveMaxNearbySortSize())];
    int size = 0;
    int populationSize = 0;
    var membership = membershipSupplier == null ? null : membershipSupplier.get();
    for (int index = 0; index < nearbySize; index++) {
      if (isNearbySubListCandidateValid(origin, index, originSubList, membership)) {
        populationSize++;
        if (size < indices.length) {
          indices[size++] = index;
        }
        if (size == indices.length
            && randomSelection
            && !Objects.requireNonNull(nearbyRandom).requiresPopulationSize()) {
          break;
        }
      }
    }
    return new NearbyCandidates(Arrays.copyOf(indices, size), populationSize);
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
    return toIntSize(
        populationValueSelector == null
            ? childSubListSelector.getValueCount()
            : populationValueSelector.getSize(),
        "childSubListSelector");
  }

  private int calculateEffectiveMaxNearbySortSize() {
    if (!randomSelection || nearbyRandom == null) {
      return maxNearbySortSize;
    }
    return Math.min(maxNearbySortSize, nearbyRandom.getOverallSizeMaximum());
  }

  private class RandomNearbySubListIterator extends UpcomingSelectionIterator<SubList> {

    private final RandomGenerator random;
    private final Iterator<SubList> replayingOriginIterator;
    private @Nullable SubList originSubList;

    private RandomNearbySubListIterator(RandomGenerator random) {
      this.random = random;
      this.replayingOriginIterator = originSubListSelector.iterator();
    }

    @Override
    protected SubList createUpcomingSelection() {
      if (replayingOriginIterator.hasNext()) {
        originSubList = replayingOriginIterator.next();
      }
      if (originSubList == null) {
        return noUpcomingSelection();
      }
      Object origin = firstElement(originSubList);
      var matrix = getDistanceMatrix();
      int nearbyIndex;
      if (requiresDynamicEligibility()) {
        // Candidate validity depends on current assignment and list length, not only distance.
        // Re-evaluate it before sampling instead of caching a stale successor map.
        var candidates = collectEligibleNearbyIndices(origin, originSubList);
        if (candidates.indices().length == 0) {
          return noUpcomingSelection();
        }
        int selectedIndex =
            Objects.requireNonNull(nearbyRandom)
                .nextInt(random, candidates.populationSize(), candidates.indices().length);
        nearbyIndex = candidates.indices()[selectedIndex];
      } else {
        int nearbySize = matrix.getDestinationSize(origin);
        if (nearbySize == 0) {
          return noUpcomingSelection();
        }
        nearbyIndex =
            Objects.requireNonNull(nearbyRandom)
                .nextInt(random, matrix.getDestinationPopulationSize(origin), nearbySize);
      }
      SubList selected = buildNearbySubList(origin, nearbyIndex, random, originSubList, null);
      return selected == null ? noUpcomingSelection() : selected;
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
      var membership = membershipSupplier == null ? null : membershipSupplier.get();
      while (nearbyIndex < nearbySize) {
        SubList candidate =
            buildNearbySubList(origin, nearbyIndex, null, originSubList, membership);
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
