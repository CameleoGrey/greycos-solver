package greycos.solver.core.impl.heuristic.selector.move.decorator;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.heuristic.selector.common.KnownExhaustionIterator;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptContext;
import greycos.solver.core.impl.heuristic.selector.common.SelectionCacheLifecycleBridge;
import greycos.solver.core.impl.heuristic.selector.common.SelectionCacheLifecycleListener;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

public abstract class AbstractCachingMoveSelector<Solution_> extends AbstractMoveSelector<Solution_>
    implements SelectionCacheLifecycleListener<Solution_> {

  protected final MoveSelector<Solution_> childMoveSelector;
  protected final SelectionCacheType cacheType;

  protected List<Move<Solution_>> cachedMoveList = null;

  public AbstractCachingMoveSelector(
      MoveSelector<Solution_> childMoveSelector, SelectionCacheType cacheType) {
    this.childMoveSelector = childMoveSelector;
    this.cacheType = cacheType;
    if (childMoveSelector.isNeverEnding()) {
      throw new IllegalStateException(
          "The selector ("
              + this
              + ") has a childMoveSelector ("
              + childMoveSelector
              + ") with neverEnding ("
              + childMoveSelector.isNeverEnding()
              + ").");
    }
    phaseLifecycleSupport.addEventListener(childMoveSelector);
    if (cacheType.isNotCached()) {
      throw new IllegalArgumentException(
          "The selector (" + this + ") does not support the cacheType (" + cacheType + ").");
    }
    phaseLifecycleSupport.addEventListener(new SelectionCacheLifecycleBridge<>(cacheType, this));
  }

  public MoveSelector<Solution_> getChildMoveSelector() {
    return childMoveSelector;
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
    long childSize = childMoveSelector.getSize();
    if (childSize > Integer.MAX_VALUE) {
      throw new IllegalStateException(
          "The selector ("
              + this
              + ") has a childMoveSelector ("
              + childMoveSelector
              + ") with childSize ("
              + childSize
              + ") which is higher than Integer.MAX_VALUE.");
    }
    boolean setup = SelectionAttemptContext.isSetup();
    var completeMoveList = new ArrayList<Move<Solution_>>(setup ? 0 : (int) childSize);
    if (!setup) {
      childMoveSelector.iterator().forEachRemaining(completeMoveList::add);
    } else {
      var iterator = childMoveSelector.iterator();
      while (!KnownExhaustionIterator.isExhausted(iterator)) {
        SelectionAttemptContext.beforeSelection();
        if (!iterator.hasNext()) break;
        var move = iterator.next();
        SelectionAttemptContext.recordSetupProposal();
        completeMoveList.add(move);
      }
    }
    cachedMoveList = completeMoveList;
    logger.trace(
        "    Created cachedMoveList: size ({}), moveSelector ({}).", cachedMoveList.size(), this);
  }

  @Override
  public void disposeCache(SolverScope<Solution_> solverScope) {
    cachedMoveList = null;
  }

  @Override
  public long getSize() {
    return cachedMoveList.size();
  }
}
