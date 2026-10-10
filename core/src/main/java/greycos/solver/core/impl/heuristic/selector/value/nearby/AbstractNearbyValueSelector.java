package greycos.solver.core.impl.heuristic.selector.value.nearby;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import greycos.solver.core.impl.heuristic.selector.common.ReachableValues;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptContext;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrixDemand;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory.ListValueFilteringType;
import greycos.solver.core.impl.neighborhood.stream.FilteringIterator;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Abstract base for nearby value selectors. Uses replaying selector pattern for consistent origin
 * during distance calculation.
 */
public abstract class AbstractNearbyValueSelector<
        Solution_, ReplayingSelector_ extends PhaseLifecycleListener<Solution_>>
    extends AbstractDemandEnabledSelector<Solution_> implements IterableValueSelector<Solution_> {

  protected final @NonNull IterableValueSelector<Solution_> childValueSelector;
  protected final @NonNull ReplayingSelector_ replayingSelector;
  protected final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  protected final @Nullable NearbyRandom nearbyRandom;
  protected final boolean randomSelection;
  protected @Nullable NearbyDistanceMatrix<NearbyOrigin, Object> distanceMatrix;
  protected final int maxNearbySortSize;
  protected final boolean eagerInitialization;
  private boolean eagerInitialized = false;
  private @Nullable NearbyDistanceMatrixDemand<NearbyOrigin, Object> distanceMatrixDemand;
  private @Nullable SupplyManager distanceMatrixSupplyManager;
  private @Nullable IterableValueSelector<Solution_> valueRangeOriginSelector;
  private boolean assertBothValueRangeSides;
  private ListValueFilteringType listValueFilteringType = ListValueFilteringType.NONE;
  private @Nullable ListVariableState<Solution_, Object, Object> listVariableState;
  private @Nullable ReachableValues<Object, Object> reachableValues;
  private @Nullable SelectionFilter<Solution_, Object> selectionFilter;
  private @Nullable ScoreDirector<Solution_> scoreDirector;

  public final void configureSelectionFilter(SelectionFilter<Solution_, Object> filter) {
    selectionFilter = filter;
  }

  private boolean acceptsCandidate(Object candidate) {
    return selectionFilter == null
        || selectionFilter.accept(Objects.requireNonNull(scoreDirector), candidate);
  }

  public final void configureValueRangeFiltering(
      IterableValueSelector<Solution_> originSelector, boolean assertBothSides) {
    valueRangeOriginSelector = originSelector;
    assertBothValueRangeSides = assertBothSides;
    phaseLifecycleSupport.addEventListener(originSelector);
  }

  public final void configureListValueFiltering(
      ListValueFilteringType filteringType, boolean unassignedValuesAllowedInPhase) {
    if (!(getVariableDescriptor() instanceof ListVariableDescriptor<?> descriptor)) {
      return;
    }
    if (filteringType == ListValueFilteringType.ACCEPT_ASSIGNED
        && !descriptor.allowsUnassignedValues()
        && !unassignedValuesAllowedInPhase) {
      return;
    }
    listValueFilteringType = filteringType;
  }

  private boolean hasDynamicFiltering() {
    return selectionFilter != null
        || valueRangeOriginSelector != null
        || listValueFilteringType != ListValueFilteringType.NONE;
  }

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
    var demand =
        new NearbyDistanceMatrixDemand<>(
            new OriginDistanceMeter(castedDistanceMeter),
            nearbyRandom,
            hasDynamicFiltering() ? Integer.MAX_VALUE : calculateEffectiveMaxNearbySortSize(),
            false,
            childValueSelector,
            replayingSelector,
            getClass().getSimpleName(),
            this::calculateOriginSizeEstimate,
            this::endingDestinations,
            this::calculateDestinationSize);
    distanceMatrix = supplyManager.demand(demand);
    distanceMatrixDemand = demand;
    distanceMatrixSupplyManager = supplyManager;
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    try {
      super.phaseStarted(phaseScope);
      scoreDirector = phaseScope.getScoreDirector();
      if (getVariableDescriptor() instanceof ListVariableDescriptor<Solution_> descriptor
          && (hasDynamicFiltering() || descriptor.supportsPinning())) {
        listVariableState = phaseScope.getScoreDirector().getListVariableState(descriptor);
        if (valueRangeOriginSelector != null) {
          reachableValues =
              phaseScope.getScoreDirector().getValueRangeManager().getReachableValues(descriptor);
        }
      }
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
    reachableValues = null;
    scoreDirector = null;
    eagerInitialized = false;
    if (demand != null && !Objects.requireNonNull(supplyManager).cancel(demand)) {
      throw new IllegalStateException("The nearby distance matrix demand is not active.");
    }
  }

  private void initializeAllOrigins() {
    Iterator<Object> originIterator = endingOriginIteratorForInitialization();
    while (originIterator.hasNext()) {
      Object origin = originIterator.next();
      Object entity = replayingSelector instanceof EntitySelector<?> ? origin : null;
      getDistanceMatrix().addAllDestinations(nearbyOrigin(origin, entity));
    }
  }

  protected abstract @NonNull Iterator<Object> endingOriginIteratorForInitialization();

  private @NonNull NearbyDistanceMatrix<NearbyOrigin, Object> getDistanceMatrix() {
    if (distanceMatrix == null) {
      throw new IllegalStateException(
          "The nearby value selector (%s) has no distance matrix. Start its phase before selecting values."
              .formatted(this));
    }
    return distanceMatrix;
  }

  protected boolean excludeOrigin() {
    return false;
  }

  protected abstract Iterator<Object> originIterator(@Nullable Object entity);

  private NearbyOrigin nearbyOrigin(Object origin, @Nullable Object entity) {
    // A solution-wide range does not depend on the selecting entity; share its row across entities.
    return new NearbyOrigin(
        origin, getVariableDescriptor().canExtractValueRangeFromSolution() ? null : entity);
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

  private int calculateDestinationSize(NearbyOrigin origin) {
    return toIntSize(childValueSelector.getSize(origin.entity()), "childValueSelector");
  }

  private Iterator<Object> endingDestinations(NearbyOrigin origin) {
    var iterator = childValueSelector.endingIterator(origin.entity());
    return new Iterator<>() {
      private Object next;
      private boolean ready;

      @Override
      public boolean hasNext() {
        while (!ready && iterator.hasNext()) {
          Object candidate = iterator.next();
          // Null is the explicit unassignment option and has no distance. Keep it separately.
          if (candidate != null
              && (!excludeOrigin() || candidate != origin.origin())
              && (listVariableState == null || !listVariableState.isPinned(candidate))) {
            next = candidate;
            ready = true;
          } else {
            SelectionAttemptContext.failedSelection();
          }
        }
        return ready;
      }

      @Override
      public Object next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        ready = false;
        return next;
      }
    };
  }

  private boolean hasUnassignedDestination(NearbyOrigin origin) {
    if (!(getVariableDescriptor() instanceof BasicVariableDescriptor<?> descriptor)
        || !descriptor.allowsUnassigned()) {
      return false;
    }
    var iterator = childValueSelector.endingIterator(origin.entity());
    while (iterator.hasNext()) {
      if (iterator.next() == null) {
        return acceptsCandidate(null);
      }
    }
    return false;
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
    return Math.min(maxNearbySortSize, nearbyRandom.getOverallSizeMaximum());
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
  public final @NonNull Iterator<Object> iterator() {
    return iterator(null);
  }

  @Override
  public final @NonNull Iterator<Object> iterator(@Nullable Object entity) {
    return new NearbyValueIterator(originIterator(entity), entity);
  }

  @Override
  public final @NonNull Iterator<Object> endingIterator(@Nullable Object entity) {
    // Enumeration must remain finite and must not consume a mimic recording.
    var iterator = childValueSelector.endingIterator(entity);
    return selectionFilter == null
        ? iterator
        : new FilteringIterator<>(iterator, this::acceptsCandidate);
  }

  @Override
  public final boolean isNeverEnding() {
    return randomSelection;
  }

  private class NearbyValueIterator implements Iterator<Object> {

    private final Iterator<Object> originIterator;
    private final Object entity;
    private NearbyOrigin origin;
    private boolean prepared;
    private boolean originSelected;
    private int nearbySize;
    private int populationSize;
    private int index;
    private boolean unassignedDestination;
    private boolean unassignedReturned;
    private List<Object> eligibleDestinations;
    private Object valueRangeOrigin;

    private NearbyValueIterator(Iterator<Object> originIterator, @Nullable Object entity) {
      this.originIterator = originIterator;
      this.entity = entity;
    }

    private void prepare() {
      if (prepared || (!randomSelection && originSelected)) {
        return;
      }
      if (originIterator.hasNext()) {
        Object nextOrigin = originIterator.next();
        if (origin == null || origin.origin() != nextOrigin) {
          origin = nearbyOrigin(nextOrigin, entity);
          nearbySize = getDistanceMatrix().getDestinationSize(origin);
          populationSize = getDistanceMatrix().getDestinationPopulationSize(origin);
          unassignedDestination = hasUnassignedDestination(origin);
          index = 0;
          unassignedReturned = false;
        }
      }
      if (hasDynamicFiltering() && origin != null) {
        if (valueRangeOriginSelector != null) {
          var rangeOriginIterator = valueRangeOriginSelector.iterator();
          if (rangeOriginIterator.hasNext()) {
            valueRangeOrigin = rangeOriginIterator.next();
          }
        }
        if (selectionFilter != null) {
          unassignedDestination = hasUnassignedDestination(origin);
        }
        if (randomSelection) {
          eligibleDestinations = new ArrayList<>();
          populationSize = 0;
          int limit = calculateEffectiveMaxNearbySortSize();
          int storedSize = getDistanceMatrix().getDestinationSize(origin);
          for (int destinationIndex = 0; destinationIndex < storedSize; destinationIndex++) {
            Object candidate = getDistanceMatrix().getDestination(origin, destinationIndex);
            if (isEligible(valueRangeOrigin, candidate)) {
              populationSize++;
              if (eligibleDestinations.size() < limit) {
                eligibleDestinations.add(candidate);
              }
              if (eligibleDestinations.size() == limit
                  && !Objects.requireNonNull(nearbyRandom).requiresPopulationSize()
                  && !unassignedDestination) {
                break;
              }
            } else {
              SelectionAttemptContext.failedSelection();
            }
          }
          nearbySize = eligibleDestinations.size();
        }
      }
      originSelected = true;
      prepared = true;
    }

    @Override
    public boolean hasNext() {
      prepare();
      if (origin == null) {
        return false;
      }
      if (!randomSelection && selectionFilter != null) {
        // The separate null option follows the same live filtering contract as ranked values.
        unassignedDestination = hasUnassignedDestination(origin);
      }
      if (!randomSelection && hasDynamicFiltering()) {
        while (index < nearbySize
            && !isEligible(valueRangeOrigin, getDistanceMatrix().getDestination(origin, index))) {
          index++;
          SelectionAttemptContext.failedSelection();
        }
      }
      return randomSelection
          ? nearbySize > 0 || unassignedDestination
          : index < nearbySize || (unassignedDestination && !unassignedReturned);
    }

    @Override
    public Object next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      if (randomSelection) {
        prepared = false;
        if (unassignedDestination
            && (nearbySize == 0
                || workingRandom.nextLong((long) populationSize + 1) == populationSize)) {
          return null;
        }
        int nearbyIndex =
            Objects.requireNonNull(nearbyRandom).nextInt(workingRandom, populationSize, nearbySize);
        return destination(nearbyIndex);
      }
      if (index < nearbySize) {
        return destination(index++);
      }
      unassignedReturned = true;
      return null;
    }

    private Object destination(int destinationIndex) {
      return eligibleDestinations == null
          ? getDistanceMatrix().getDestination(origin, destinationIndex)
          : eligibleDestinations.get(destinationIndex);
    }
  }

  private boolean isEligible(Object source, Object candidate) {
    if (!acceptsCandidate(candidate)) {
      return false;
    }
    if (!(getVariableDescriptor() instanceof ListVariableDescriptor<?>)) {
      return true;
    }
    var state = Objects.requireNonNull(listVariableState);
    if (listValueFilteringType != ListValueFilteringType.NONE
        && state.isAssigned(candidate)
            != (listValueFilteringType == ListValueFilteringType.ACCEPT_ASSIGNED)) {
      return false;
    }
    if (valueRangeOriginSelector == null) {
      return true;
    }
    if (source == null
        || !Objects.requireNonNull(reachableValues).isValueReachable(source, candidate)) {
      return false;
    }
    return reachableValues.isEntityReachable(source, state.getInverseSingleton(candidate))
        && (!assertBothValueRangeSides
            || reachableValues.isEntityReachable(candidate, state.getInverseSingleton(source)));
  }

  /** The same distance origin can have different legal destinations on different entities. */
  protected record NearbyOrigin(Object origin, @Nullable Object entity) {
    @Override
    public boolean equals(Object other) {
      return other instanceof NearbyOrigin that && origin == that.origin && entity == that.entity;
    }

    @Override
    public int hashCode() {
      return 31 * System.identityHashCode(origin) + System.identityHashCode(entity);
    }
  }

  private record OriginDistanceMeter(NearbyDistanceMeter<Object, Object> delegate)
      implements NearbyDistanceMeter<NearbyOrigin, Object> {
    @Override
    public double getNearbyDistance(NearbyOrigin origin, Object destination) {
      return delegate.getNearbyDistance(origin.origin(), destination);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AbstractNearbyValueSelector<?, ?> that)) {
      return false;
    }
    return Objects.equals(selectionFilter, that.selectionFilter)
        && Objects.equals(childValueSelector, that.childValueSelector)
        && Objects.equals(replayingSelector, that.replayingSelector)
        && Objects.equals(nearbyDistanceMeter, that.nearbyDistanceMeter)
        && Objects.equals(nearbyRandom, that.nearbyRandom)
        && randomSelection == that.randomSelection
        && maxNearbySortSize == that.maxNearbySortSize
        && eagerInitialization == that.eagerInitialization
        && Objects.equals(valueRangeOriginSelector, that.valueRangeOriginSelector)
        && assertBothValueRangeSides == that.assertBothValueRangeSides
        && listValueFilteringType == that.listValueFilteringType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        selectionFilter,
        childValueSelector,
        replayingSelector,
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        maxNearbySortSize,
        eagerInitialization,
        valueRangeOriginSelector,
        assertBothValueRangeSides,
        listValueFilteringType);
  }
}
