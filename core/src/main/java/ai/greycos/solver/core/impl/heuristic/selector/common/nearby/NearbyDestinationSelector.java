package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Iterator;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import ai.greycos.solver.core.preview.api.domain.metamodel.ElementPosition;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Nearby destination selector that applies nearby selection to destination selectors.
 *
 * <p>This selector filters and reorders element positions based on distance from an origin entity
 * or value. It supports both random and original selection orders.
 *
 * @param <Solution_> solution type
 */
public class NearbyDestinationSelector<Solution_> extends ElementDestinationSelector<Solution_> {

  private final @NonNull EntitySelector<Solution_> entitySelector;
  private final @NonNull IterableValueSelector<Solution_> valueSelector;
  private final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  private final @Nullable NearbyRandom nearbyRandom;
  private final boolean randomSelection;
  private final ai.greycos.solver.core.impl.domain.variable.descriptor.@NonNull
          ListVariableDescriptor<
          Solution_>
      listVariableDescriptor;

  private ai.greycos.solver.core.impl.domain.variable.@Nullable ListVariableStateSupply<
          Solution_, Object, Object>
      listVariableStateSupply;

  public NearbyDestinationSelector(
      @NonNull DestinationSelectorConfig config,
      @NonNull HeuristicConfigPolicy<Solution_> configPolicy,
      @NonNull NearbySelectionConfig nearbySelectionConfig,
      @NonNull SelectionCacheType minimumCacheType,
      @NonNull SelectionOrder resolvedSelectionOrder,
      @NonNull ElementDestinationSelector<Solution_> destinationSelector,
      @NonNull EntitySelector<Solution_> entitySelector,
      @NonNull IterableValueSelector<Solution_> valueSelector) {
    super(entitySelector, valueSelector, resolvedSelectionOrder == SelectionOrder.RANDOM);
    this.entitySelector = entitySelector;
    this.valueSelector = valueSelector;
    this.listVariableDescriptor =
        (ai.greycos.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor<Solution_>)
            valueSelector.getVariableDescriptor();
    this.randomSelection = resolvedSelectionOrder.toRandomSelectionBoolean();

    var instanceCache = configPolicy.getClassInstanceCache();
    this.nearbyDistanceMeter =
        instanceCache.newInstance(
            config,
            "nearbyDistanceMeterClass",
            nearbySelectionConfig.getNearbyDistanceMeterClass());

    this.nearbyRandom =
        NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(randomSelection);

    phaseLifecycleSupport.addEventListener(destinationSelector);
  }

  @Override
  public void solvingStarted(
      ai.greycos.solver.core.impl.solver.scope.SolverScope<Solution_> solverScope) {
    super.solvingStarted(solverScope);
    var supplyManager = solverScope.getScoreDirector().getSupplyManager();
    listVariableStateSupply = supplyManager.demand(listVariableDescriptor.getStateDemand());
  }

  @Override
  public void solvingEnded(
      ai.greycos.solver.core.impl.solver.scope.SolverScope<Solution_> solverScope) {
    super.solvingEnded(solverScope);
    listVariableStateSupply = null;
  }

  @Override
  public long getSize() {
    // ElementDestinationSelector size is entitySelector.getSize() + valueSelector.getSize()
    // For nearby selection, we use the same calculation
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

  @Override
  public EntityDescriptor<Solution_> getEntityDescriptor() {
    return entitySelector.getEntityDescriptor();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NearbyDestinationSelector<?> that)) {
      return false;
    }
    return super.equals(o)
        && entitySelector.equals(that.entitySelector)
        && valueSelector.equals(that.valueSelector)
        && nearbyDistanceMeter.equals(that.nearbyDistanceMeter)
        && (nearbyRandom == null
            ? that.nearbyRandom == null
            : nearbyRandom.equals(that.nearbyRandom))
        && randomSelection == that.randomSelection;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + entitySelector.hashCode();
    result = 31 * result + valueSelector.hashCode();
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

  // ************************************************************************
  // Inner classes
  // ************************************************************************

  private class RandomNearbyDestinationIterator implements Iterator<ElementPosition> {

    private final java.util.Random workingRandom;
    private final int nearbySize;
    private int count = 0;

    public RandomNearbyDestinationIterator(java.util.Random workingRandom) {
      this.workingRandom = workingRandom;
      this.nearbySize = (int) (entitySelector.getSize() + valueSelector.getSize());
    }

    @Override
    public boolean hasNext() {
      return count < nearbySize;
    }

    @Override
    public ElementPosition next() {
      if (nearbyRandom == null) {
        throw new IllegalStateException("nearbyRandom is null but randomSelection is true");
      }
      int nearbyIndex = nearbyRandom.nextInt(workingRandom, nearbySize);
      count++;

      // For random selection, randomly choose between entity-based and value-based destinations
      double entityProbability = (double) entitySelector.getSize() / nearbySize;
      if (workingRandom.nextDouble() < entityProbability) {
        // Select entity-based destination
        Object entity = entitySelector.iterator().next();
        return ElementPosition.of(entity, listVariableDescriptor.getFirstUnpinnedIndex(entity));
      } else {
        // Select value-based destination
        Object value = valueSelector.iterator().next();
        var positionInList = listVariableStateSupply.getElementPosition(value).ensureAssigned();
        return ElementPosition.of(positionInList.entity(), positionInList.index() + 1);
      }
    }
  }

  private class OriginalNearbyDestinationIterator implements Iterator<ElementPosition> {

    private final java.util.Iterator<Object> entityIterator;
    private final java.util.Iterator<Object> valueIterator;
    private final long entitySize;
    private final long valueSize;
    private final long totalSize;
    private int index = 0;
    private boolean inEntityPhase = true;

    public OriginalNearbyDestinationIterator() {
      this.entityIterator = entitySelector.iterator();
      this.valueIterator = valueSelector.iterator();
      this.entitySize = entitySelector.getSize();
      this.valueSize = valueSelector.getSize();
      this.totalSize = entitySize + valueSize;
    }

    @Override
    public boolean hasNext() {
      return index < totalSize;
    }

    @Override
    public ElementPosition next() {
      if (index < entitySize) {
        // Entity-based destination: entity at first unpinned index
        var entity = entityIterator.next();
        index++;
        return ElementPosition.of(entity, listVariableDescriptor.getFirstUnpinnedIndex(entity));
      } else {
        // Value-based destination
        var value = valueIterator.next();
        index++;
        var positionInList = listVariableStateSupply.getElementPosition(value).ensureAssigned();
        return ElementPosition.of(positionInList.entity(), positionInList.index() + 1);
      }
    }
  }
}
