package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Iterator;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.AbstractSelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.ValueSelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Selects values based on proximity to an origin value. Wraps a value selector and filters/reorders
 * values by distance using a cached distance matrix. Supports probability distributions (parabolic,
 * linear, block, beta) for random selection.
 */
public class NearbyValueSelector<Solution_> extends AbstractSelector<Solution_>
    implements ValueSelector<Solution_> {

  private final @NonNull IterableValueSelector<Solution_> childValueSelector;
  private final @NonNull IterableValueSelector<Solution_> originValueSelector;
  private final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  private final @Nullable NearbyRandom nearbyRandom;
  private final boolean randomSelection;
  private final int maxNearbySortSize;
  private final boolean eagerInitialization;

  // Distance matrix for caching sorted values by distance from origin
  private final @NonNull NearbyDistanceMatrix<Object, Object> distanceMatrix;

  public NearbyValueSelector(
      @NonNull ValueSelectorConfig config,
      @NonNull HeuristicConfigPolicy<Solution_> configPolicy,
      @NonNull NearbySelectionConfig nearbySelectionConfig,
      @NonNull SelectionCacheType minimumCacheType,
      @NonNull SelectionOrder resolvedSelectionOrder,
      @NonNull EntityDescriptor<Solution_> entityDescriptor,
      @NonNull IterableValueSelector<Solution_> valueSelector) {
    this.childValueSelector = valueSelector;
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

    // Build origin value selector from config
    this.originValueSelector =
        (IterableValueSelector<Solution_>)
            ValueSelectorFactory.<Solution_>create(
                    nearbySelectionConfig.getOriginValueSelectorConfig())
                .buildValueSelector(
                    configPolicy, entityDescriptor, minimumCacheType, resolvedSelectionOrder);

    if (!(originValueSelector instanceof IterableValueSelector)) {
      throw new IllegalArgumentException(
          "The originValueSelectorConfig ("
              + nearbySelectionConfig.getOriginValueSelectorConfig()
              + ") needs to be based on an IterableValueSelector ("
              + originValueSelector
              + "). Check your @ValueRangeProvider annotations.");
    }

    // Create distance matrix for caching sorted values
    @SuppressWarnings("unchecked")
    var castedDistanceMeter = (NearbyDistanceMeter<Object, Object>) nearbyDistanceMeter;

    this.distanceMatrix =
        new NearbyDistanceMatrix<>(
            castedDistanceMeter,
            100, // Initial capacity estimate
            origin -> childValueSelector.iterator(origin),
            origin -> (int) childValueSelector.getSize(origin),
            maxNearbySortSize);

    phaseLifecycleSupport.addEventListener(childValueSelector);
    phaseLifecycleSupport.addEventListener(originValueSelector);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    super.solvingStarted(solverScope);

    // Eager initialization: pre-compute all distance matrices
    if (eagerInitialization) {
      initializeAllOrigins(solverScope);
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    super.solvingEnded(solverScope);
  }

  @Override
  public GenuineVariableDescriptor<Solution_> getVariableDescriptor() {
    return childValueSelector.getVariableDescriptor();
  }

  @Override
  public long getSize(Object entity) {
    return childValueSelector.getSize(entity);
  }

  @Override
  public @NonNull Iterator<Object> iterator(@NonNull Object entity) {
    if (randomSelection) {
      return new RandomNearbyValueIterator(workingRandom, entity);
    } else {
      return new OriginalNearbyValueIterator(entity);
    }
  }

  @Override
  public @NonNull Iterator<Object> endingIterator(@NonNull Object entity) {
    // For nearby selection, ending iterator is same as regular iterator
    // because we only iterate through nearby values
    return iterator(entity);
  }

  @Override
  public boolean isCountable() {
    return childValueSelector.isCountable();
  }

  @Override
  public boolean isNeverEnding() {
    return randomSelection || childValueSelector.isNeverEnding();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NearbyValueSelector<?> that)) {
      return false;
    }
    return childValueSelector.equals(that.childValueSelector)
        && originValueSelector.equals(that.originValueSelector)
        && nearbyDistanceMeter.equals(that.nearbyDistanceMeter)
        && (nearbyRandom == null
            ? that.nearbyRandom == null
            : nearbyRandom.equals(that.nearbyRandom))
        && randomSelection == that.randomSelection;
  }

  @Override
  public int hashCode() {
    int result = childValueSelector.hashCode();
    result = 31 * result + originValueSelector.hashCode();
    result = 31 * result + nearbyDistanceMeter.hashCode();
    result = 31 * result + (nearbyRandom == null ? 0 : nearbyRandom.hashCode());
    result = 31 * result + Boolean.hashCode(randomSelection);
    return result;
  }

  @Override
  public String toString() {
    return "NearbyValueSelector(" + getVariableDescriptor().getVariableName() + ")";
  }

  /**
   * Calculates maxNearbySortSize with auto-configuration. If user specified a value, use it.
   * Otherwise, auto-calculate based on distribution size.
   *
   * @param config nearby selection config
   * @return max nearby sort size to use
   */
  private int calculateMaxNearbySortSize(@NonNull NearbySelectionConfig config) {
    Integer userSpecified = config.getMaxNearbySortSize();
    if (userSpecified != null && userSpecified > 0) {
      return userSpecified;
    }

    // Auto-calculate: 10x distribution size (heuristic)
    int distributionSize = getDistributionSize(config);
    return Math.max(1000, distributionSize * 10);
  }

  /**
   * Gets the distribution size from config based on distribution type.
   *
   * @param config nearby selection config
   * @return distribution size
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
  private void initializeAllOrigins(SolverScope<Solution_> solverScope) {
    // Get all entities from the child value selector
    var entityDescriptor = getVariableDescriptor().getEntityDescriptor();

    // Iterate through all entities and pre-compute their distance matrices
    entityDescriptor.visitAllEntities(
        solverScope.getWorkingSolution(),
        entity -> {
          distanceMatrix.addAllDestinations(entity);
        });
  }

  // ************************************************************************
  // Iterator implementations
  // ************************************************************************

  /**
   * Random nearby value iterator. Uses probability distribution to select values sorted by distance
   * from origin.
   */
  private class RandomNearbyValueIterator implements Iterator<Object> {

    private final java.util.Random random;
    private final @NonNull Object entity;
    private final Iterator<Object> replayingOriginIterator;
    private final int nearbySize;

    // Origin caching - origin is selected once from replaying iterator
    private Object origin = null;

    public RandomNearbyValueIterator(java.util.Random random, @NonNull Object entity) {
      this.random = random;
      this.entity = entity;
      this.replayingOriginIterator = originValueSelector.iterator(entity);
      this.nearbySize = (int) childValueSelector.getSize(entity);
    }

    @Override
    public boolean hasNext() {
      // The replaying iterator provides a constant origin until recording iterator advances
      return (origin != null || replayingOriginIterator.hasNext()) && nearbySize > 0;
    }

    @Override
    public Object next() {
      if (nearbyRandom == null) {
        throw new IllegalStateException("nearbyRandom is null but randomSelection is true");
      }

      // Get origin from replaying iterator (will be constant until recording iterator advances)
      if (replayingOriginIterator.hasNext()) {
        origin = replayingOriginIterator.next();
      }

      // Select nearby index using probability distribution
      int nearbyIndex = nearbyRandom.nextInt(random, nearbySize);

      // Get the nearbyIndex-th closest value from the distance matrix
      return distanceMatrix.getDestination(origin, nearbyIndex);
    }
  }

  /**
   * Deterministic nearby value iterator. Iterates through values in sorted distance order from
   * origin.
   */
  private class OriginalNearbyValueIterator implements Iterator<Object> {

    private final @NonNull Object entity;
    private final Iterator<Object> replayingOriginIterator;
    private final int nearbySize;
    private int nextNearbyIndex = 0;

    // Origin caching state
    private boolean originSelected = false;
    private boolean originIsNotEmpty;
    private Object origin = null;

    public OriginalNearbyValueIterator(@NonNull Object entity) {
      this.entity = entity;
      this.replayingOriginIterator = originValueSelector.iterator(entity);
      this.nearbySize = (int) childValueSelector.getSize(entity);
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
    public Object next() {
      selectOrigin(); // Ensure origin is selected and cached

      // Get the nextNearbyIndex-th closest value from the distance matrix
      Object result = distanceMatrix.getDestination(origin, nextNearbyIndex);
      nextNearbyIndex++;
      return result;
    }
  }
}
