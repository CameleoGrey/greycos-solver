package greycos.solver.core.impl.heuristic.selector.entity.decorator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import greycos.solver.core.impl.heuristic.selector.common.KnownExhaustionIterator;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptContext;
import greycos.solver.core.impl.heuristic.selector.common.SelectionCacheLifecycleBridge;
import greycos.solver.core.impl.heuristic.selector.common.SelectionCacheLifecycleListener;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.solver.scope.SolverScope;

public abstract class AbstractCachingEntitySelector<Solution_>
    extends AbstractDemandEnabledSelector<Solution_>
    implements SelectionCacheLifecycleListener<Solution_>, EntitySelector<Solution_> {

  protected final EntitySelector<Solution_> childEntitySelector;
  protected final SelectionCacheType cacheType;

  protected List<Object> cachedEntityList = null;

  public AbstractCachingEntitySelector(
      EntitySelector<Solution_> childEntitySelector, SelectionCacheType cacheType) {
    this.childEntitySelector = childEntitySelector;
    this.cacheType = cacheType;
    if (childEntitySelector.isNeverEnding()) {
      throw new IllegalStateException(
          "The selector ("
              + this
              + ") has a childEntitySelector ("
              + childEntitySelector
              + ") with neverEnding ("
              + childEntitySelector.isNeverEnding()
              + ").");
    }
    phaseLifecycleSupport.addEventListener(childEntitySelector);
    if (cacheType.isNotCached()) {
      throw new IllegalArgumentException(
          "The selector (" + this + ") does not support the cacheType (" + cacheType + ").");
    }
    phaseLifecycleSupport.addEventListener(new SelectionCacheLifecycleBridge<>(cacheType, this));
  }

  public EntitySelector<Solution_> getChildEntitySelector() {
    return childEntitySelector;
  }

  @Override
  public SelectionCacheType getCacheType() {
    return cacheType;
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  public void constructCache(SolverScope<Solution_> solverScope) {
    long childSize = childEntitySelector.getSize();
    if (childSize > Integer.MAX_VALUE) {
      throw new IllegalStateException(
          "The selector ("
              + this
              + ") has a childEntitySelector ("
              + childEntitySelector
              + ") with childSize ("
              + childSize
              + ") which is higher than Integer.MAX_VALUE.");
    }
    boolean setup = SelectionAttemptContext.isSetup();
    var completeEntityList = new ArrayList<Object>(setup ? 0 : (int) childSize);
    if (!setup) {
      childEntitySelector.iterator().forEachRemaining(completeEntityList::add);
    } else {
      var iterator = childEntitySelector.iterator();
      while (!KnownExhaustionIterator.isExhausted(iterator)) {
        SelectionAttemptContext.beforeSelection();
        if (!iterator.hasNext()) break;
        var selection = iterator.next();
        SelectionAttemptContext.recordSetupProposal();
        completeEntityList.add(selection);
      }
    }
    cachedEntityList = completeEntityList;
    logger.trace(
        "    Created cachedEntityList: size ({}), entitySelector ({}).",
        cachedEntityList.size(),
        this);
  }

  @Override
  public void disposeCache(SolverScope<Solution_> solverScope) {
    cachedEntityList = null;
  }

  @Override
  public EntityDescriptor<Solution_> getEntityDescriptor() {
    return childEntitySelector.getEntityDescriptor();
  }

  @Override
  public long getSize() {
    return cachedEntityList.size();
  }

  @Override
  public Iterator<Object> endingIterator() {
    return SelectionAttemptContext.iterator(cachedEntityList);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    AbstractCachingEntitySelector<?> that = (AbstractCachingEntitySelector<?>) other;
    return Objects.equals(childEntitySelector, that.childEntitySelector)
        && cacheType == that.cacheType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(childEntitySelector, cacheType);
  }
}
