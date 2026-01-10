package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Iterator;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import ai.greycos.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.AbstractSelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubList;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubListSelector;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Nearby sub-list selector filtering and reordering sub-lists by distance from origin. */
public class NearbySubListSelector<Solution_> extends AbstractSelector<Solution_>
    implements SubListSelector<Solution_> {

  private final @NonNull RandomSubListSelector<Solution_> childSubListSelector;
  private final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  private final @Nullable NearbyRandom nearbyRandom;
  private final boolean randomSelection;

  public NearbySubListSelector(
      @NonNull SubListSelectorConfig config,
      @NonNull HeuristicConfigPolicy<Solution_> configPolicy,
      @NonNull NearbySelectionConfig nearbySelectionConfig,
      @NonNull SelectionCacheType minimumCacheType,
      @NonNull SelectionOrder resolvedSelectionOrder,
      @NonNull ListVariableDescriptor<Solution_> variableDescriptor,
      @NonNull RandomSubListSelector<Solution_> subListSelector) {
    this.childSubListSelector = subListSelector;
    this.randomSelection = resolvedSelectionOrder.toRandomSelectionBoolean();

    var instanceCache = configPolicy.getClassInstanceCache();
    this.nearbyDistanceMeter =
        instanceCache.newInstance(
            config,
            "nearbyDistanceMeterClass",
            nearbySelectionConfig.getNearbyDistanceMeterClass());

    this.nearbyRandom =
        NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(randomSelection);

    phaseLifecycleSupport.addEventListener(childSubListSelector);
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
    } else {
      return new OriginalNearbySubListIterator();
    }
  }

  @Override
  public long getValueCount() {
    return childSubListSelector.getValueCount();
  }

  @Override
  public @NonNull Iterator<Object> endingValueIterator() {
    // For nearby selection, ending value iterator delegates to child selector
    return childSubListSelector.endingValueIterator();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NearbySubListSelector<?> that)) {
      return false;
    }
    return childSubListSelector.equals(that.childSubListSelector)
        && nearbyDistanceMeter.equals(that.nearbyDistanceMeter)
        && (nearbyRandom == null
            ? that.nearbyRandom == null
            : nearbyRandom.equals(that.nearbyRandom))
        && randomSelection == that.randomSelection;
  }

  @Override
  public int hashCode() {
    int result = childSubListSelector.hashCode();
    result = 31 * result + nearbyDistanceMeter.hashCode();
    result = 31 * result + (nearbyRandom == null ? 0 : nearbyRandom.hashCode());
    result = 31 * result + Boolean.hashCode(randomSelection);
    return result;
  }

  @Override
  public String toString() {
    return "NearbySubListSelector(" + getVariableDescriptor().getVariableName() + ")";
  }

  // ************************************************************************
  // Inner classes
  // ************************************************************************

  private class RandomNearbySubListIterator implements Iterator<SubList> {

    private final java.util.Random workingRandom;
    private final int nearbySize;
    private int count = 0;

    public RandomNearbySubListIterator(java.util.Random workingRandom) {
      this.workingRandom = workingRandom;
      this.nearbySize = (int) childSubListSelector.getSize();
    }

    @Override
    public boolean hasNext() {
      return count < nearbySize;
    }

    @Override
    public SubList next() {
      if (nearbyRandom == null) {
        throw new IllegalStateException("nearbyRandom is null but randomSelection is true");
      }
      int nearbyIndex = nearbyRandom.nextInt(workingRandom, nearbySize);
      count++;

      // Get nearbyIndex-th sub-list from child selector
      Iterator<SubList> childIterator = childSubListSelector.iterator();
      SubList result = null;
      for (int i = 0; i <= nearbyIndex && childIterator.hasNext(); i++) {
        result = childIterator.next();
      }
      return result;
    }
  }

  private class OriginalNearbySubListIterator implements Iterator<SubList> {

    private final int nearbySize;
    private int index = 0;

    public OriginalNearbySubListIterator() {
      this.nearbySize = (int) childSubListSelector.getSize();
    }

    @Override
    public boolean hasNext() {
      return index < nearbySize;
    }

    @Override
    public SubList next() {
      // For original order, just iterate through sub-lists in order
      Iterator<SubList> childIterator = childSubListSelector.iterator();
      SubList result = null;
      for (int i = 0; i <= index && childIterator.hasNext(); i++) {
        result = childIterator.next();
      }
      index++;
      return result;
    }
  }
}
