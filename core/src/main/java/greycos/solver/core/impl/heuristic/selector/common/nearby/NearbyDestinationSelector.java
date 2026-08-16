package greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Iterator;
import java.util.random.RandomGenerator;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.list.DestinationSelector;
import greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;

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
  private final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  private final @Nullable NearbyRandom nearbyRandom;
  private final boolean randomSelection;
  private final @NonNull ListVariableDescriptor<Solution_> listVariableDescriptor;
  private final int maxNearbySortSize;
  private final boolean eagerInitialization;

  // Distance matrix for caching sorted destinations by distance from origin
  // Initialized in phaseStarted(), after child selector caches are available.
  private @Nullable NearbyDistanceMatrix<Object, Object> distanceMatrix;
  private @Nullable NearbyDistanceMatrixDemand<Object, Object> distanceMatrixDemand;
  private boolean eagerInitialized = false;

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
    this.destinationSelector = destinationSelector;
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

    this.maxNearbySortSize =
        randomSelection
            ? NearbySelectionTuning.calculateMaxNearbySortSize(nearbySelectionConfig)
            : Integer.MAX_VALUE;

    this.eagerInitialization = NearbySelectionTuning.isEagerInitialization(nearbySelectionConfig);
    if (eagerInitialization && originSubListSelector != null) {
      throw new IllegalArgumentException(
          "Eager nearby initialization requires an enumerable entity or value origin selector; "
              + "a replaying subList origin selector can only be initialized lazily.");
    }

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
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    if (distanceMatrix != null || distanceMatrixDemand != null || listVariableStateSupply != null) {
      throw new IllegalStateException("The nearby destination selector is already solving.");
    }
    super.solvingStarted(solverScope);
    var supplyManager = solverScope.getScoreDirector().getSupplyManager();
    listVariableStateSupply = supplyManager.demand(listVariableDescriptor.getStateDemand());
    distanceMatrix = null;
    distanceMatrixDemand = null;
    eagerInitialized = false;
  }

  private void initializeDistanceMatrix(@NonNull SupplyManager supplyManager) {
    // Initialize distance matrix now that selectors are initialized
    @SuppressWarnings("unchecked")
    var castedDistanceMeter = (NearbyDistanceMeter<Object, Object>) nearbyDistanceMeter;

    distanceMatrixDemand =
        new NearbyDistanceMatrixDemand<>(
            castedDistanceMeter,
            nearbyRandom,
            calculateEffectiveMaxNearbySortSize(),
            true,
            destinationSelector,
            getOriginSelectorKey(),
            getClass().getSimpleName(),
            this::calculateOriginSizeEstimate,
            origin -> new CombinedDestinationIterator(),
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
    distanceMatrix = null; // Allow GC to free memory
    eagerInitialized = false;
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
   * Eagerly initializes all origins by pre-computing their distance matrices. This eliminates
   * latency spikes during solving.
   */
  private void initializeAllOrigins() {
    // Get all origins from the origin selector
    Iterator<?> originIterator;
    if (originEntitySelector != null) {
      originIterator = originEntitySelector.endingIterator();
    } else if (originSubListSelector != null) {
      /*
       * Sub-list origins are typically replaying selectors and can be recording-dependent or
       * effectively never-ending at this stage. They can't be enumerated safely for eager
       * initialization.
       */
      return;
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

    private final RandomGenerator random;
    private final Iterator<?> replayingOriginIterator;

    // Origin caching - origin is selected once from replaying iterator
    private Object origin = null;
    private Object cachedOrigin = null;
    private int cachedNearbySize = -1;

    public RandomNearbyDestinationIterator(RandomGenerator random) {
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
    }

    @Override
    public boolean hasNext() {
      // The replaying iterator provides a constant origin until the recording iterator advances
      return origin != null || replayingOriginIterator.hasNext();
    }

    @Override
    public ElementPosition next() {
      if (nearbyRandom == null) {
        throw new IllegalStateException("nearbyRandom is null but randomSelection is true");
      }

      // Get origin from replaying iterator (will be constant until recording iterator advances)
      if (replayingOriginIterator.hasNext()) {
        origin = replayingOriginIterator.next();
      }
      if (origin == null) {
        throw new java.util.NoSuchElementException();
      }

      if (origin != cachedOrigin) {
        cachedOrigin = origin;
        cachedNearbySize = getDistanceMatrix().getDestinationSize(origin);
      }
      if (cachedNearbySize <= 0) {
        throw new java.util.NoSuchElementException();
      }

      // Select nearby index using probability distribution
      int nearbyIndex = nearbyRandom.nextInt(random, cachedNearbySize);

      // Get the nearbyIndex-th closest destination from the distance matrix
      Object destination = getDistanceMatrix().getDestination(origin, nearbyIndex);

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
    private int nearbySize = -1;
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
        nearbySize = getDistanceMatrix().getDestinationSize(origin);
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
      selectOrigin(); // Ensure origin is selected and cached

      // Get the nextNearbyIndex-th closest destination from the distance matrix
      Object destination = getDistanceMatrix().getDestination(origin, nextNearbyIndex);
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
    return toIntSize(getSize(), "destinationSelector");
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
