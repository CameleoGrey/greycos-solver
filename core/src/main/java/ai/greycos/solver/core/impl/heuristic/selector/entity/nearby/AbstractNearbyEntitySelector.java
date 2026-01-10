package ai.greycos.solver.core.impl.heuristic.selector.entity.nearby;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.Objects;

import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import ai.greycos.solver.core.impl.heuristic.selector.common.iterator.ListIterable;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;

import org.jspecify.annotations.NonNull;

/**
 * Abstract base for nearby entity selectors.
 */
abstract class AbstractNearbyEntitySelector<Solution_>
    extends AbstractDemandEnabledSelector<Solution_> implements EntitySelector<Solution_> {

  protected final @NonNull EntitySelector<Solution_> childEntitySelector;
  protected final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  protected final @NonNull NearbyRandom nearbyRandom;
  protected final boolean randomSelection;

  protected AbstractNearbyEntitySelector(
      @NonNull EntitySelector<Solution_> childEntitySelector,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @NonNull NearbyRandom nearbyRandom,
      boolean randomSelection) {
    this.childEntitySelector = childEntitySelector;
    this.nearbyDistanceMeter = nearbyDistanceMeter;
    this.nearbyRandom = nearbyRandom;
    this.randomSelection = randomSelection;
    phaseLifecycleSupport.addEventListener(childEntitySelector);
  }

  @Override
  public EntityDescriptor<Solution_> getEntityDescriptor() {
    return childEntitySelector.getEntityDescriptor();
  }

  @Override
  public boolean isCountable() {
    return true;
  }

  @Override
  public long getSize() {
    return childEntitySelector.getSize();
  }

  @Override
  public boolean isNeverEnding() {
    return childEntitySelector.isNeverEnding();
  }

  @Override
  public Iterator<Object> endingIterator() {
    return childEntitySelector.endingIterator();
  }

  @Override
  public ListIterator<Object> listIterator() {
    if (childEntitySelector instanceof ListIterable) {
      return ((ListIterable<Object>) childEntitySelector).listIterator();
    }
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " does not support listIterator()");
  }

  @Override
  public ListIterator<Object> listIterator(int index) {
    if (childEntitySelector instanceof ListIterable) {
      return ((ListIterable<Object>) childEntitySelector).listIterator(index);
    }
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " does not support listIterator(int)");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AbstractNearbyEntitySelector<?> that)) {
      return false;
    }
    return Objects.equals(childEntitySelector, that.childEntitySelector)
        && Objects.equals(nearbyDistanceMeter, that.nearbyDistanceMeter)
        && Objects.equals(nearbyRandom, that.nearbyRandom)
        && randomSelection == that.randomSelection;
  }

  @Override
  public int hashCode() {
    return Objects.hash(childEntitySelector, nearbyDistanceMeter, nearbyRandom, randomSelection);
  }
}
