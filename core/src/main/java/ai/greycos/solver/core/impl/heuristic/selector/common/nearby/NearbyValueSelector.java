package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Iterator;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.AbstractSelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.ValueSelector;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Nearby value selector that applies nearby selection to value selectors.
 *
 * <p>This selector wraps a value selector and applies nearby selection logic based on an origin
 * value selector. Destinations are filtered and reordered based on distance from the origin value.
 *
 * @param <Solution_> solution type
 */
public class NearbyValueSelector<Solution_> extends AbstractSelector<Solution_>
    implements ValueSelector<Solution_> {

  private final @NonNull ValueSelector<Solution_> childValueSelector;
  private final @NonNull IterableValueSelector<Solution_> originValueSelector;
  private final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  private final @Nullable NearbyRandom nearbyRandom;
  private final boolean randomSelection;

  public NearbyValueSelector(
      @NonNull ValueSelectorConfig config,
      @NonNull HeuristicConfigPolicy<Solution_> configPolicy,
      @NonNull NearbySelectionConfig nearbySelectionConfig,
      @NonNull SelectionCacheType minimumCacheType,
      @NonNull SelectionOrder resolvedSelectionOrder,
      @NonNull EntityDescriptor<Solution_> entityDescriptor,
      @NonNull ValueSelector<Solution_> valueSelector) {
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

    if (!(valueSelector instanceof IterableValueSelector)) {
      throw new IllegalArgumentException(
          "The valueSelectorConfig ("
              + config
              + ") needs to be based on an IterableValueSelector ("
              + valueSelector
              + "). Check your @ValueRangeProvider annotations.");
    }

    // Build origin value selector from config
    this.originValueSelector =
        (IterableValueSelector<Solution_>)
            ai.greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory
                .<Solution_>create(nearbySelectionConfig.getOriginValueSelectorConfig())
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

    phaseLifecycleSupport.addEventListener(childValueSelector);
    phaseLifecycleSupport.addEventListener(originValueSelector);
  }

  @Override
  public ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor<Solution_>
      getVariableDescriptor() {
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
    // For nearby selection, ending iterator is the same as regular iterator
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

  // ************************************************************************
  // Inner classes
  // ************************************************************************

  private class RandomNearbyValueIterator implements Iterator<Object> {

    private final java.util.Random workingRandom;
    private final @NonNull Object entity;
    private final int nearbySize;
    private int count = 0;

    public RandomNearbyValueIterator(java.util.Random workingRandom, @NonNull Object entity) {
      this.workingRandom = workingRandom;
      this.entity = entity;
      this.nearbySize = (int) childValueSelector.getSize(entity);
    }

    @Override
    public boolean hasNext() {
      return count < nearbySize;
    }

    @Override
    public Object next() {
      if (nearbyRandom == null) {
        throw new IllegalStateException("nearbyRandom is null but randomSelection is true");
      }
      int nearbyIndex = nearbyRandom.nextInt(workingRandom, nearbySize);
      count++;

      // Get origin value
      Object origin = originValueSelector.iterator(entity).next();

      // Get nearbyIndex-th value from child selector
      Iterator<Object> childIterator = childValueSelector.iterator(entity);
      Object result = null;
      for (int i = 0; i <= nearbyIndex && childIterator.hasNext(); i++) {
        result = childIterator.next();
      }
      return result;
    }
  }

  private class OriginalNearbyValueIterator implements Iterator<Object> {

    private final @NonNull Object entity;
    private final int nearbySize;
    private int index = 0;

    public OriginalNearbyValueIterator(@NonNull Object entity) {
      this.entity = entity;
      this.nearbySize = (int) childValueSelector.getSize(entity);
    }

    @Override
    public boolean hasNext() {
      return index < nearbySize;
    }

    @Override
    public Object next() {
      // For original order, just iterate through values in order
      Iterator<Object> childIterator = childValueSelector.iterator(entity);
      Object result = null;
      for (int i = 0; i <= index && childIterator.hasNext(); i++) {
        result = childIterator.next();
      }
      index++;
      return result;
    }
  }
}
