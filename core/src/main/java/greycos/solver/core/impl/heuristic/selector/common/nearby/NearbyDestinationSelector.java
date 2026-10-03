package greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.common.iterator.ConcatenatingIterator;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.list.DestinationSelector;
import greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.preview.api.cotwin.metamodel.PositionInList;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Selects element positions for list variables based on proximity to an origin. Filters and
 * reorders destinations (entities or values) by distance using a cached distance matrix. Supports
 * probability distributions for random selection.
 */
public class NearbyDestinationSelector<Solution_> extends AbstractDemandEnabledSelector<Solution_>
    implements DestinationSelector<Solution_> {

  private final @NonNull EntitySelector<Solution_> entitySelector;
  private final @NonNull IterableValueSelector<Solution_> valueSelector;
  private final @NonNull ElementDestinationSelector<Solution_> destinationSelector;
  private final EntitySelector<Solution_> originEntitySelector;
  private final SubListSelector<Solution_> originSubListSelector;
  private final IterableValueSelector<Solution_> originValueSelector;
  private final @Nullable IterableValueSelector<Solution_> sourceValueSelector;
  private final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  private final @Nullable NearbyRandom nearbyRandom;
  private final boolean randomSelection;
  private final @NonNull ListVariableDescriptor<Solution_> listVariableDescriptor;
  private final int maxNearbySortSize;
  private final boolean eagerInitialization;
  private final boolean dynamicDestinationEligibility;
  private EntitySelector<Solution_> entityPopulationSelector;
  private IterableValueSelector<Solution_> valuePopulationSelector;
  private @Nullable SelectionFilter<Solution_, Object> entitySelectionFilter;
  private @Nullable SelectionFilter<Solution_, Object> valueSelectionFilter;
  private @Nullable Supplier<Set<Object>> entityMembershipSupplier;
  private @Nullable Supplier<Set<Object>> valueMembershipSupplier;

  public void configureSelectionSources(
      NearbySelectionSource<Solution_, EntitySelector<Solution_>> entitySource,
      NearbySelectionSource<Solution_, IterableValueSelector<Solution_>> valueSource) {
    if (entitySource.selector() != entitySelector || valueSource.selector() != valueSelector) {
      throw new IllegalArgumentException(
          "The nearby destination sources must belong to its child selectors.");
    }
    entityPopulationSelector = entitySource.populationSelector();
    valuePopulationSelector = valueSource.populationSelector();
    entitySelectionFilter = entitySource.liveFilter();
    valueSelectionFilter = valueSource.liveFilter();
    entityMembershipSupplier = entitySource.membershipSupplier();
    valueMembershipSupplier = valueSource.membershipSupplier();
  }

  private boolean requiresDynamicEligibility() {
    return dynamicDestinationEligibility
        || entitySelectionFilter != null
        || valueSelectionFilter != null
        || entityMembershipSupplier != null
        || valueMembershipSupplier != null;
  }

  private record DestinationPopulation(Object entitySelector, Object valueSelector) {}

  // Distance matrix for caching sorted destinations by distance from origin
  // Initialized in phaseStarted(), after child selector caches are available.
  private @Nullable NearbyDistanceMatrix<Object, Object> distanceMatrix;
  private @Nullable NearbyDistanceMatrixDemand<Object, Object> distanceMatrixDemand;
  private @Nullable SupplyManager distanceMatrixSupplyManager;
  private boolean eagerInitialized = false;

  private @Nullable ListVariableState<Solution_, Object, Object> listVariableState;
  private @Nullable InnerScoreDirector<Solution_, ?> scoreDirector;

  public NearbyDestinationSelector(
      @NonNull DestinationSelectorConfig config,
      @NonNull HeuristicConfigPolicy<Solution_> configPolicy,
      @NonNull NearbySelectionConfig nearbySelectionConfig,
      @NonNull SelectionCacheType minimumCacheType,
      @NonNull SelectionOrder resolvedSelectionOrder,
      @NonNull ElementDestinationSelector<Solution_> destinationSelector,
      @NonNull EntitySelector<Solution_> entitySelector,
      @NonNull IterableValueSelector<Solution_> valueSelector,
      EntitySelector<Solution_> originEntitySelector,
      SubListSelector<Solution_> originSubListSelector,
      IterableValueSelector<Solution_> originValueSelector) {
    this(
        config,
        configPolicy,
        nearbySelectionConfig,
        minimumCacheType,
        resolvedSelectionOrder,
        destinationSelector,
        entitySelector,
        valueSelector,
        originEntitySelector,
        originSubListSelector,
        originValueSelector,
        null);
  }

  public NearbyDestinationSelector(
      @NonNull DestinationSelectorConfig config,
      @NonNull HeuristicConfigPolicy<Solution_> configPolicy,
      @NonNull NearbySelectionConfig nearbySelectionConfig,
      @NonNull SelectionCacheType minimumCacheType,
      @NonNull SelectionOrder resolvedSelectionOrder,
      @NonNull ElementDestinationSelector<Solution_> destinationSelector,
      @NonNull EntitySelector<Solution_> entitySelector,
      @NonNull IterableValueSelector<Solution_> valueSelector,
      EntitySelector<Solution_> originEntitySelector,
      SubListSelector<Solution_> originSubListSelector,
      IterableValueSelector<Solution_> originValueSelector,
      @Nullable IterableValueSelector<Solution_> sourceValueSelector) {
    this.entitySelector = entitySelector;
    this.valueSelector = valueSelector;
    this.entityPopulationSelector = entitySelector;
    this.valuePopulationSelector = valueSelector;
    this.destinationSelector = destinationSelector;
    this.originEntitySelector = originEntitySelector;
    this.originSubListSelector = originSubListSelector;
    this.originValueSelector = originValueSelector;
    this.sourceValueSelector = sourceValueSelector;

    // Validate that exactly one origin selector is provided
    int originSelectorCount = 0;
    if (originEntitySelector != null) originSelectorCount++;
    if (originSubListSelector != null) originSelectorCount++;
    if (originValueSelector != null) originSelectorCount++;
    if (originSelectorCount != 1) {
      throw new IllegalArgumentException(
          "NearbyDestinationSelector requires exactly one origin selector, but got "
              + originSelectorCount
              + " (originEntitySelector="
              + originEntitySelector
              + ", originSubListSelector="
              + originSubListSelector
              + ", originValueSelector="
              + originValueSelector
              + ")");
    }

    this.listVariableDescriptor =
        (ListVariableDescriptor<Solution_>) valueSelector.getVariableDescriptor();
    this.randomSelection = resolvedSelectionOrder.toRandomSelectionBoolean();
    this.dynamicDestinationEligibility =
        listVariableDescriptor.allowsUnassignedValues()
            || configPolicy.isUnassignedValuesAllowed()
            || !listVariableDescriptor.canExtractValueRangeFromSolution();
    if (!listVariableDescriptor.canExtractValueRangeFromSolution()
        && originEntitySelector != null
        && sourceValueSelector == null) {
      throw new IllegalArgumentException(
          "The nearby destination selector for entity-provided value range (%s) uses an entity origin (%s) without a source value recorder. Configure a list change source value recorder or a value/subList nearby origin."
              .formatted(listVariableDescriptor, originEntitySelector));
    }

    var instanceCache = configPolicy.getClassInstanceCache();
    this.nearbyDistanceMeter =
        instanceCache.newInstance(
            config,
            "nearbyDistanceMeterClass",
            nearbySelectionConfig.getNearbyDistanceMeterClass());

    this.nearbyRandom =
        NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(randomSelection);

    this.maxNearbySortSize =
        randomSelection
            ? NearbySelectionTuning.calculateMaxNearbySortSize(nearbySelectionConfig)
            : Integer.MAX_VALUE;

    this.eagerInitialization = NearbySelectionTuning.isEagerInitialization(nearbySelectionConfig);

    // Distance matrix will be initialized in phaseStarted(), when child selector caches exist.
    this.distanceMatrix = null;

    phaseLifecycleSupport.addEventListener(destinationSelector);
    if (originEntitySelector != null) {
      phaseLifecycleSupport.addEventListener(originEntitySelector);
    }
    if (originSubListSelector != null) {
      phaseLifecycleSupport.addEventListener(originSubListSelector);
    }
    if (originValueSelector != null) {
      phaseLifecycleSupport.addEventListener(originValueSelector);
    }
    if (sourceValueSelector != null && sourceValueSelector != originValueSelector) {
      phaseLifecycleSupport.addEventListener(sourceValueSelector);
    }
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    if (distanceMatrix != null || distanceMatrixDemand != null || listVariableState != null) {
      throw new IllegalStateException("The nearby destination selector is already solving.");
    }
    super.solvingStarted(solverScope);
    distanceMatrix = null;
    distanceMatrixDemand = null;
    eagerInitialized = false;
  }

  private void initializeDistanceMatrix(@NonNull SupplyManager supplyManager) {
    // Initialize distance matrix now that selectors are initialized
    @SuppressWarnings("unchecked")
    var castedDistanceMeter = (NearbyDistanceMeter<Object, Object>) nearbyDistanceMeter;

    var demand =
        new NearbyDistanceMatrixDemand<>(
            castedDistanceMeter,
            nearbyRandom,
            // Assignment and entity-range eligibility may change during a phase. Keep all
            // distances for those models, then apply the configured cap to eligible positions.
            requiresDynamicEligibility()
                ? Integer.MAX_VALUE
                : calculateEffectiveMaxNearbySortSize(),
            false,
            new DestinationPopulation(entityPopulationSelector, valuePopulationSelector),
            getOriginSelectorKey(),
            getClass().getSimpleName(),
            this::calculateOriginSizeEstimate,
            origin -> new CombinedDestinationIterator(),
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

  @Override
  public long getSize() {
    return entitySelector.getSize()
        + valueSelector.getSize()
        + (listVariableDescriptor.allowsUnassignedValues() ? 1 : 0);
  }

  @Override
  public @NonNull Iterator<ElementPosition> iterator() {
    if (randomSelection) {
      return new RandomNearbyDestinationIterator(workingRandom);
    } else {
      return new OriginalNearbyDestinationIterator();
    }
  }

  @Override
  public boolean isNeverEnding() {
    return randomSelection;
  }

  public EntityDescriptor<Solution_> getEntityDescriptor() {
    return entitySelector.getEntityDescriptor();
  }

  public ListVariableDescriptor<Solution_> getVariableDescriptor() {
    return listVariableDescriptor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NearbyDestinationSelector<?> that)) {
      return false;
    }
    return entitySelector.equals(that.entitySelector)
        && valueSelector.equals(that.valueSelector)
        && (originEntitySelector == null
            ? that.originEntitySelector == null
            : originEntitySelector.equals(that.originEntitySelector))
        && (originSubListSelector == null
            ? that.originSubListSelector == null
            : originSubListSelector.equals(that.originSubListSelector))
        && (originValueSelector == null
            ? that.originValueSelector == null
            : originValueSelector.equals(that.originValueSelector))
        && Objects.equals(sourceValueSelector, that.sourceValueSelector)
        && Objects.equals(entityPopulationSelector, that.entityPopulationSelector)
        && Objects.equals(valuePopulationSelector, that.valuePopulationSelector)
        && Objects.equals(entitySelectionFilter, that.entitySelectionFilter)
        && Objects.equals(valueSelectionFilter, that.valueSelectionFilter)
        && Objects.equals(entityMembershipSupplier, that.entityMembershipSupplier)
        && Objects.equals(valueMembershipSupplier, that.valueMembershipSupplier)
        && nearbyDistanceMeter.equals(that.nearbyDistanceMeter)
        && (nearbyRandom == null
            ? that.nearbyRandom == null
            : nearbyRandom.equals(that.nearbyRandom))
        && randomSelection == that.randomSelection
        && maxNearbySortSize == that.maxNearbySortSize
        && eagerInitialization == that.eagerInitialization;
  }

  @Override
  public int hashCode() {
    int result = entitySelector.hashCode();
    result = 31 * result + valueSelector.hashCode();
    result = 31 * result + (originEntitySelector == null ? 0 : originEntitySelector.hashCode());
    result = 31 * result + (originSubListSelector == null ? 0 : originSubListSelector.hashCode());
    result = 31 * result + (originValueSelector == null ? 0 : originValueSelector.hashCode());
    result = 31 * result + Objects.hashCode(sourceValueSelector);
    result =
        31 * result
            + Objects.hash(
                entityPopulationSelector,
                valuePopulationSelector,
                entitySelectionFilter,
                valueSelectionFilter,
                entityMembershipSupplier,
                valueMembershipSupplier);
    result = 31 * result + nearbyDistanceMeter.hashCode();
    result = 31 * result + (nearbyRandom == null ? 0 : nearbyRandom.hashCode());
    result = 31 * result + Boolean.hashCode(randomSelection);
    result = 31 * result + maxNearbySortSize;
    result = 31 * result + Boolean.hashCode(eagerInitialization);
    return result;
  }

  @Override
  public String toString() {
    return "NearbyDestinationSelector("
        + getEntityDescriptor().getEntityClass().getSimpleName()
        + ")";
  }

  /**
   * Eagerly initializes all origins by pre-computing their distance matrices. This eliminates
   * latency spikes during solving.
   */
  private void initializeAllOrigins() {
    // Get all origins from the origin selector
    Iterator<?> originIterator;
    if (originEntitySelector != null) {
      originIterator = originEntitySelector.endingIterator();
    } else if (originSubListSelector != null) {
      originIterator = originSubListSelector.endingValueIterator();
    } else if (originValueSelector != null) {
      originIterator = originValueSelector.endingIterator(null);
    } else {
      throw new IllegalStateException("No origin selector is configured.");
    }

    // Pre-compute distance matrices for all origins
    while (originIterator.hasNext()) {
      Object origin = originIterator.next();
      distanceMatrix.addAllDestinations(origin);
    }
  }

  // ************************************************************************
  // Helper classes
  // ************************************************************************

  /**
   * Iterator that combines entity and value destinations into a single stream. Used by the distance
   * matrix to build the list of all possible destinations.
   *
   * <p>IMPORTANT: Uses endingIterator() methods instead of iterator() to avoid never-ending
   * iteration when selectors use random selection order.
   */
  private class CombinedDestinationIterator extends UpcomingSelectionIterator<Object> {

    private final Iterator<Object> destinationIterator =
        new ConcatenatingIterator<>(
            entityPopulationSelector.endingIterator(),
            valuePopulationSelector.endingIterator(null));

    @Override
    protected Object createUpcomingSelection() {
      while (destinationIterator.hasNext()) {
        Object destination = destinationIterator.next();
        // Pinning is stable throughout a phase. Filter before truncating the sorted row:
        // otherwise a capped row containing only pinned values would have no valid destination.
        if (!listVariableDescriptor.supportsPinning()
            || (getEntityDescriptor().matchesEntity(destination)
                ? scoreDirector
                    .getSolutionDescriptor()
                    .findEntityDescriptorOrFail(destination.getClass())
                    .isMovable(scoreDirector.getWorkingSolution(), destination)
                : !listVariableState.isPinned(destination))) {
          return destination;
        }
      }
      return noUpcomingSelection();
    }
  }

  private Iterator<?> originIterator() {
    if (originEntitySelector != null) {
      return originEntitySelector.iterator();
    } else if (originSubListSelector != null) {
      return originSubListSelector.iterator();
    }
    return Objects.requireNonNull(originValueSelector).iterator();
  }

  private Object distanceOrigin(Object originSelection) {
    if (originSubListSelector != null) {
      SubList subList = (SubList) originSelection;
      // A SubList identifies mutable positions, not an immutable distance origin. The value
      // currently at its start is both the public meter argument and the distance cache key.
      return listVariableDescriptor.getElement(subList.entity(), subList.fromIndex());
    }
    return originSelection;
  }

  private class RandomNearbyDestinationIterator extends UpcomingSelectionIterator<ElementPosition> {

    private final RandomGenerator random;
    private final Iterator<?> replayingOriginIterator = originIterator();
    private final Iterator<Object> replayingSourceIterator =
        sourceValueSelector == null ? null : sourceValueSelector.iterator();
    private Object sourceValue;
    private Object originSelection;

    private RandomNearbyDestinationIterator(RandomGenerator random) {
      this.random = random;
    }

    @Override
    protected ElementPosition createUpcomingSelection() {
      if (replayingOriginIterator.hasNext()) {
        originSelection = replayingOriginIterator.next();
      }
      if (originSelection == null) {
        return noUpcomingSelection();
      }
      if (replayingSourceIterator != null && replayingSourceIterator.hasNext()) {
        sourceValue = replayingSourceIterator.next();
      }
      Object origin = distanceOrigin(originSelection);
      var matrix = getDistanceMatrix();
      if (!requiresDynamicEligibility()) {
        int nearbySize = matrix.getDestinationSize(origin);
        if (nearbySize == 0) {
          return noUpcomingSelection();
        }
        int nearbyIndex =
            Objects.requireNonNull(nearbyRandom)
                .nextInt(random, matrix.getDestinationPopulationSize(origin), nearbySize);
        return Objects.requireNonNull(
            convertToElementPosition(matrix.getDestination(origin, nearbyIndex)));
      }

      var candidates =
          collectEligibleDestinations(
              replayingSourceIterator == null
                  ? originSelection
                  : Objects.requireNonNull(sourceValue),
              origin);
      int populationSize = candidates.populationSize();
      if (listVariableDescriptor.allowsUnassignedValues()
          && (populationSize == 0 || random.nextLong((long) populationSize + 1) == 0)) {
        // Unassignment is a single independent option, regardless of how many values are
        // currently unassigned; it has no geographic distance and must not occupy a nearby rank.
        return ElementPosition.unassigned();
      }
      if (candidates.positions().isEmpty()) {
        return noUpcomingSelection();
      }
      int nearbyIndex =
          Objects.requireNonNull(nearbyRandom)
              .nextInt(random, populationSize, candidates.positions().size());
      return candidates.positions().get(nearbyIndex);
    }
  }

  private class OriginalNearbyDestinationIterator
      extends UpcomingSelectionIterator<ElementPosition> {

    private final Iterator<?> replayingOriginIterator = originIterator();
    private final Iterator<Object> replayingSourceIterator =
        sourceValueSelector == null ? null : sourceValueSelector.iterator();
    private Object sourceValue;
    private Object originSelection;
    private Object origin;
    private boolean originSelected;
    private int nearbyIndex;
    private boolean unassignedReturned;

    @Override
    protected ElementPosition createUpcomingSelection() {
      if (!originSelected) {
        originSelected = true;
        if (!replayingOriginIterator.hasNext()) {
          return noUpcomingSelection();
        }
        originSelection = replayingOriginIterator.next();
        origin = distanceOrigin(originSelection);
        if (replayingSourceIterator != null && replayingSourceIterator.hasNext()) {
          sourceValue = replayingSourceIterator.next();
        }
      }
      if (origin == null) {
        return noUpcomingSelection();
      }
      var matrix = getDistanceMatrix();
      var entityMembership =
          entityMembershipSupplier == null ? null : entityMembershipSupplier.get();
      var valueMembership = valueMembershipSupplier == null ? null : valueMembershipSupplier.get();
      while (nearbyIndex < matrix.getDestinationSize(origin)) {
        var candidate = matrix.getDestination(origin, nearbyIndex++);
        if (!acceptsCandidate(candidate, entityMembership, valueMembership)) {
          continue;
        }
        var position = convertToElementPosition(candidate);
        if (position != null
            && acceptsDestination(
                replayingSourceIterator == null
                    ? originSelection
                    : Objects.requireNonNull(sourceValue),
                position.entity())) {
          return position;
        }
      }
      if (!unassignedReturned && listVariableDescriptor.allowsUnassignedValues()) {
        unassignedReturned = true;
        return ElementPosition.unassigned();
      }
      return noUpcomingSelection();
    }
  }

  private record DestinationCandidates(List<PositionInList> positions, int populationSize) {}

  private DestinationCandidates collectEligibleDestinations(Object originSelection, Object origin) {
    var matrix = getDistanceMatrix();
    var positions = new ArrayList<PositionInList>();
    int populationSize = 0;
    int maximumSize = calculateEffectiveMaxNearbySortSize();
    var entityMembership = entityMembershipSupplier == null ? null : entityMembershipSupplier.get();
    var valueMembership = valueMembershipSupplier == null ? null : valueMembershipSupplier.get();
    // Resolve positions on each selection. Values can be assigned, unassigned, or moved to an
    // entity with a different value range without changing their cached geographic distances.
    for (int index = 0; index < matrix.getDestinationSize(origin); index++) {
      var candidate = matrix.getDestination(origin, index);
      if (!acceptsCandidate(candidate, entityMembership, valueMembership)) {
        continue;
      }
      var position = convertToElementPosition(candidate);
      if (position != null && acceptsDestination(originSelection, position.entity())) {
        populationSize++;
        if (positions.size() < maximumSize) {
          positions.add(position);
        }
        if (positions.size() == maximumSize
            && randomSelection
            && !Objects.requireNonNull(nearbyRandom).requiresPopulationSize()
            && !listVariableDescriptor.allowsUnassignedValues()) {
          break;
        }
      }
    }
    return new DestinationCandidates(positions, populationSize);
  }

  private boolean acceptsCandidate(
      Object candidate,
      @Nullable Set<Object> entityMembership,
      @Nullable Set<Object> valueMembership) {
    boolean entityCandidate = getEntityDescriptor().matchesEntity(candidate);
    var membership = entityCandidate ? entityMembership : valueMembership;
    if (membership != null && !membership.contains(candidate)) {
      return false;
    }
    var filter = entityCandidate ? entitySelectionFilter : valueSelectionFilter;
    return filter == null || filter.accept(Objects.requireNonNull(scoreDirector), candidate);
  }

  private boolean acceptsDestination(Object originSelection, Object destinationEntity) {
    if (listVariableDescriptor.canExtractValueRangeFromSolution()) {
      return true;
    }
    var range =
        scoreDirector
            .getValueRangeManager()
            .getFromEntity(listVariableDescriptor.getValueRangeDescriptor(), destinationEntity);
    if (originSubListSelector != null) {
      SubList subList = (SubList) originSelection;
      for (int index = subList.fromIndex(); index < subList.getToIndex(); index++) {
        if (!range.contains(listVariableDescriptor.getElement(subList.entity(), index))) {
          return false;
        }
      }
      return true;
    }
    return range.contains(originSelection);
  }

  private @Nullable PositionInList convertToElementPosition(Object destination) {
    if (getEntityDescriptor().matchesEntity(destination)) {
      return ElementPosition.of(
          destination, listVariableDescriptor.getFirstUnpinnedIndex(destination));
    }
    var position = Objects.requireNonNull(listVariableState).getElementPosition(destination);
    if (position instanceof PositionInList positionInList) {
      return ElementPosition.of(positionInList.entity(), positionInList.index() + 1);
    }
    return null;
  }

  private @NonNull NearbyDistanceMatrix<Object, Object> getDistanceMatrix() {
    if (distanceMatrix == null) {
      throw new IllegalStateException(
          "distanceMatrix is null. Make sure solvingStarted() was called.");
    }
    return distanceMatrix;
  }

  private @NonNull Object getOriginSelectorKey() {
    if (originEntitySelector != null) {
      return originEntitySelector;
    }
    if (originSubListSelector != null) {
      return originSubListSelector;
    }
    if (originValueSelector != null) {
      return originValueSelector;
    }
    throw new IllegalStateException("No origin selector is configured");
  }

  private int calculateOriginSizeEstimate() {
    if (originEntitySelector != null) {
      return toIntSize(originEntitySelector.getSize(), "originEntitySelector");
    }
    if (originSubListSelector != null) {
      return toIntSize(originSubListSelector.getValueCount(), "originSubListSelector");
    }
    return toIntSize(originValueSelector.getSize(), "originValueSelector");
  }

  private int calculateDestinationSize() {
    return toIntSize(
        entityPopulationSelector.getSize() + valuePopulationSelector.getSize(),
        "destinationSelector");
  }

  private int calculateEffectiveMaxNearbySortSize() {
    if (!randomSelection || nearbyRandom == null) {
      return maxNearbySortSize;
    }
    return Math.min(maxNearbySortSize, nearbyRandom.getOverallSizeMaximum());
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
}
