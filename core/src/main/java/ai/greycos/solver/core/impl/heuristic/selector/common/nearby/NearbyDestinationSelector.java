package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Iterator;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.ListVariableStateSupply;
import ai.greycos.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.DestinationSelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.preview.api.domain.metamodel.ElementPosition;

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
  private final EntitySelector<Solution_> originEntitySelector;
  private final SubListSelector<Solution_> originSubListSelector;
  private final IterableValueSelector<Solution_> originValueSelector;
  private final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  private final @Nullable NearbyRandom nearbyRandom;
  private final boolean randomSelection;
  private final @NonNull ListVariableDescriptor<Solution_> listVariableDescriptor;
  private final int maxNearbySortSize;
  private final boolean eagerInitialization;

  // Distance matrix for caching sorted destinations by distance from origin
  // Initialized lazily in solvingStarted()
  private @Nullable NearbyDistanceMatrix<Object, Object> distanceMatrix;

  private @Nullable ListVariableStateSupply<Solution_, Object, Object> listVariableStateSupply;

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
    this.entitySelector = entitySelector;
    this.valueSelector = valueSelector;
    this.originEntitySelector = originEntitySelector;
    this.originSubListSelector = originSubListSelector;
    this.originValueSelector = originValueSelector;

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

    var instanceCache = configPolicy.getClassInstanceCache();
    this.nearbyDistanceMeter =
        instanceCache.newInstance(
            config,
            "nearbyDistanceMeterClass",
            nearbySelectionConfig.getNearbyDistanceMeterClass());

    this.nearbyRandom =
        NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(randomSelection);

    // Calculate maxNearbySortSize with auto-configuration
    this.maxNearbySortSize = calculateMaxNearbySortSize(nearbySelectionConfig);

    // Eager initialization flag
    this.eagerInitialization = Boolean.TRUE.equals(nearbySelectionConfig.getEagerInitialization());

    // Distance matrix will be initialized lazily in solvingStarted()
    // when selectors are fully initialized (their cachedEntityList won't be null)
    this.distanceMatrix = null;

    phaseLifecycleSupport.addEventListener(destinationSelector);
    phaseLifecycleSupport.addEventListener(entitySelector);
    phaseLifecycleSupport.addEventListener(valueSelector);
    if (originEntitySelector != null) {
      phaseLifecycleSupport.addEventListener(originEntitySelector);
    }
    if (originSubListSelector != null) {
      phaseLifecycleSupport.addEventListener(originSubListSelector);
    }
    if (originValueSelector != null) {
      phaseLifecycleSupport.addEventListener(originValueSelector);
    }
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    super.solvingStarted(solverScope);
    var supplyManager = solverScope.getScoreDirector().getSupplyManager();
    listVariableStateSupply = supplyManager.demand(listVariableDescriptor.getStateDemand());

    // Initialize distance matrix now that selectors are initialized
    @SuppressWarnings("unchecked")
    var castedDistanceMeter = (NearbyDistanceMeter<Object, Object>) nearbyDistanceMeter;

    this.distanceMatrix =
        new NearbyDistanceMatrix<>(
            castedDistanceMeter,
            100, // Initial capacity estimate
            origin -> new CombinedDestinationIterator(),
            origin -> (int) (entitySelector.getSize() + valueSelector.getSize()),
            maxNearbySortSize);

    // Eager initialization: pre-compute all distance matrices
    if (eagerInitialization) {
      initializeAllOrigins();
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    super.solvingEnded(solverScope);
    listVariableStateSupply = null;
    distanceMatrix = null; // Allow GC to free memory
  }

  @Override
  public long getSize() {
    return entitySelector.getSize() + valueSelector.getSize();
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
  public boolean isCountable() {
    return entitySelector.isCountable() && valueSelector.isCountable();
  }

  @Override
  public boolean isNeverEnding() {
    return randomSelection || entitySelector.isNeverEnding() || valueSelector.isNeverEnding();
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
        && nearbyDistanceMeter.equals(that.nearbyDistanceMeter)
        && (nearbyRandom == null
            ? that.nearbyRandom == null
            : nearbyRandom.equals(that.nearbyRandom))
        && randomSelection == that.randomSelection;
  }

  @Override
  public int hashCode() {
    int result = entitySelector.hashCode();
    result = 31 * result + valueSelector.hashCode();
    result = 31 * result + (originEntitySelector == null ? 0 : originEntitySelector.hashCode());
    result = 31 * result + (originSubListSelector == null ? 0 : originSubListSelector.hashCode());
    result = 31 * result + (originValueSelector == null ? 0 : originValueSelector.hashCode());
    result = 31 * result + nearbyDistanceMeter.hashCode();
    result = 31 * result + (nearbyRandom == null ? 0 : nearbyRandom.hashCode());
    result = 31 * result + Boolean.hashCode(randomSelection);
    return result;
  }

  @Override
  public String toString() {
    return "NearbyDestinationSelector("
        + getEntityDescriptor().getEntityClass().getSimpleName()
        + ")";
  }

  /**
   * Calculates the maxNearbySortSize with auto-configuration. If user specified a value, use it.
   * Otherwise, auto-calculate based on distribution size.
   *
   * @param config the nearby selection config
   * @return the max nearby sort size to use
   */
  private int calculateMaxNearbySortSize(@NonNull NearbySelectionConfig config) {
    Integer userSpecified = config.getMaxNearbySortSize();
    if (userSpecified != null && userSpecified > 0) {
      return userSpecified;
    }

    // Auto-calculate: 10x the distribution size (heuristic)
    int distributionSize = getDistributionSize(config);
    return Math.max(1000, distributionSize * 10);
  }

  /**
   * Gets the distribution size from the config based on the distribution type.
   *
   * @param config the nearby selection config
   * @return the distribution size
   */
  private int getDistributionSize(@NonNull NearbySelectionConfig config) {
    var distributionType = config.getNearbySelectionDistributionType();
    if (distributionType == null) {
      return 40; // Default for PARABOLIC
    }

    return switch (distributionType) {
      case PARABOLIC_DISTRIBUTION -> {
        Integer size = config.getParabolicDistributionSizeMaximum();
        yield size != null ? size : 40;
      }
      case LINEAR_DISTRIBUTION -> {
        Integer size = config.getLinearDistributionSizeMaximum();
        yield size != null ? size : 40;
      }
      case BLOCK_DISTRIBUTION -> {
        Integer size = config.getBlockDistributionSizeMaximum();
        yield size != null ? size : 40;
      }
      case BETA_DISTRIBUTION -> 40; // Beta distribution doesn't have a size parameter
    };
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
      // SubListSelector doesn't have endingIterator(), use iterator() instead
      originIterator = originSubListSelector.iterator();
    } else if (originValueSelector != null) {
      // IterableValueSelector doesn't have endingIterator(), use iterator() instead
      originIterator = originValueSelector.iterator(null);
    } else {
      return;
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
  private class CombinedDestinationIterator implements Iterator<Object> {

    private final Iterator<Object> entityIterator;
    private final Iterator<Object> valueIterator;
    private boolean inEntityPhase = true;

    public CombinedDestinationIterator() {
      // Use endingIterator() to get finite iterators, not never-ending random iterators
      this.entityIterator = entitySelector.endingIterator();
      this.valueIterator =
          valueSelector.endingIterator(null); // null = all values across all entities
    }

    @Override
    public boolean hasNext() {
      if (inEntityPhase) {
        if (entityIterator.hasNext()) {
          return true;
        }
        inEntityPhase = false;
      }
      return valueIterator.hasNext();
    }

    @Override
    public Object next() {
      if (inEntityPhase) {
        if (entityIterator.hasNext()) {
          return entityIterator.next();
        }
        inEntityPhase = false;
      }
      return valueIterator.next();
    }
  }

  // ************************************************************************
  // Iterator implementations
  // ************************************************************************

  /**
   * Random nearby destination iterator. Uses probability distribution to select destinations sorted
   * by distance from the origin.
   */
  private class RandomNearbyDestinationIterator implements Iterator<ElementPosition> {

    private final java.util.Random random;
    private final Iterator<?> replayingOriginIterator;
    private final int nearbySize;

    // Origin caching - origin is selected once from replaying iterator
    private Object origin = null;

    public RandomNearbyDestinationIterator(java.util.Random random) {
      this.random = random;
      if (originEntitySelector != null) {
        this.replayingOriginIterator = originEntitySelector.iterator();
      } else if (originSubListSelector != null) {
        this.replayingOriginIterator = originSubListSelector.iterator();
      } else if (originValueSelector != null) {
        this.replayingOriginIterator = originValueSelector.iterator();
      } else {
        throw new IllegalStateException("No origin selector is configured");
      }
      this.nearbySize = (int) (entitySelector.getSize() + valueSelector.getSize());
    }

    @Override
    public boolean hasNext() {
      // The replaying iterator provides a constant origin until the recording iterator advances
      return (origin != null || replayingOriginIterator.hasNext()) && nearbySize > 0;
    }

    @Override
    public ElementPosition next() {
      if (nearbyRandom == null) {
        throw new IllegalStateException("nearbyRandom is null but randomSelection is true");
      }
      if (distanceMatrix == null) {
        throw new IllegalStateException(
            "distanceMatrix is null. Make sure solvingStarted() was called.");
      }

      // Get origin from replaying iterator (will be constant until recording iterator advances)
      if (replayingOriginIterator.hasNext()) {
        origin = replayingOriginIterator.next();
      }

      // Select nearby index using probability distribution
      int nearbyIndex = nearbyRandom.nextInt(random, nearbySize);

      // Get the nearbyIndex-th closest destination from the distance matrix
      Object destination = distanceMatrix.getDestination(origin, nearbyIndex);

      // Convert destination (entity or value) to ElementPosition
      return convertToElementPosition(destination);
    }
  }

  /**
   * Deterministic nearby destination iterator. Iterates through destinations in sorted distance
   * order from the origin.
   */
  private class OriginalNearbyDestinationIterator implements Iterator<ElementPosition> {

    private final Iterator<?> replayingOriginIterator;
    private final long nearbySize;
    private int nextNearbyIndex = 0;

    // Origin caching state
    private boolean originSelected = false;
    private boolean originIsNotEmpty;
    private Object origin = null;

    public OriginalNearbyDestinationIterator() {
      if (originEntitySelector != null) {
        this.replayingOriginIterator = originEntitySelector.iterator();
      } else if (originSubListSelector != null) {
        this.replayingOriginIterator = originSubListSelector.iterator();
      } else if (originValueSelector != null) {
        this.replayingOriginIterator = originValueSelector.iterator();
      } else {
        throw new IllegalStateException("No origin selector is configured");
      }
      this.nearbySize = entitySelector.getSize() + valueSelector.getSize();
    }

    /**
     * Selects the origin from the replaying iterator. Called once on first access, then the origin
     * is cached for all subsequent calls until the recording iterator advances.
     */
    private void selectOrigin() {
      if (originSelected) {
        return; // Already selected, use cached origin
      }
      /*
       * The origin iterator is guaranteed to be a replaying iterator.
       * Therefore next() will point to whatever the related recording iterator was pointing to
       * at the time when its next() was called.
       * As a result, origin here will be constant unless next() on the original recording
       * iterator is called first.
       */
      originIsNotEmpty = replayingOriginIterator.hasNext();
      if (originIsNotEmpty) {
        origin = replayingOriginIterator.next();
      }
      originSelected = true;
    }

    @Override
    public boolean hasNext() {
      selectOrigin();
      return originIsNotEmpty && nextNearbyIndex < nearbySize;
    }

    @Override
    public ElementPosition next() {
      if (distanceMatrix == null) {
        throw new IllegalStateException(
            "distanceMatrix is null. Make sure solvingStarted() was called.");
      }

      selectOrigin(); // Ensure origin is selected and cached

      // Get the nextNearbyIndex-th closest destination from the distance matrix
      Object destination = distanceMatrix.getDestination(origin, nextNearbyIndex);
      nextNearbyIndex++;

      // Convert destination (entity or value) to ElementPosition
      return convertToElementPosition(destination);
    }
  }

  /**
   * Converts a destination object (either entity or value) to an ElementPosition.
   *
   * @param destination the destination object (entity or value)
   * @return ElementPosition for the destination
   */
  private ElementPosition convertToElementPosition(Object destination) {
    // Check if destination is an entity
    if (getEntityDescriptor().matchesEntity(destination)) {
      // Entity-based destination: position at first unpinned index
      return ElementPosition.of(
          destination, listVariableDescriptor.getFirstUnpinnedIndex(destination));
    } else {
      // Value-based destination: position after the value's current position
      if (listVariableStateSupply == null) {
        throw new IllegalStateException(
            "listVariableStateSupply is null. Make sure solvingStarted() was called.");
      }
      var positionInList = listVariableStateSupply.getElementPosition(destination).ensureAssigned();
      return ElementPosition.of(positionInList.entity(), positionInList.index() + 1);
    }
  }
}
