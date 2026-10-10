package greycos.solver.core.impl.heuristic.selector.move.decorator;

import java.util.Iterator;
import java.util.NavigableMap;
import java.util.TreeMap;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.heuristic.selector.common.KnownExhaustionIterator;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptContext;
import greycos.solver.core.impl.heuristic.selector.common.SelectionCacheLifecycleBridge;
import greycos.solver.core.impl.heuristic.selector.common.SelectionCacheLifecycleListener;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionProbabilityWeightFactory;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.random.RandomUtils;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

public class ProbabilityMoveSelector<Solution_> extends AbstractMoveSelector<Solution_>
    implements SelectionCacheLifecycleListener<Solution_> {

  protected final MoveSelector<Solution_> childMoveSelector;
  protected final SelectionCacheType cacheType;
  protected final SelectionProbabilityWeightFactory<Solution_, Move<Solution_>>
      probabilityWeightFactory;

  protected NavigableMap<Double, Move<Solution_>> cachedMoveMap = null;
  protected double probabilityWeightTotal = -1.0;

  public ProbabilityMoveSelector(
      MoveSelector<Solution_> childMoveSelector,
      SelectionCacheType cacheType,
      SelectionProbabilityWeightFactory<Solution_, ? extends Move<Solution_>>
          probabilityWeightFactory) {
    this.childMoveSelector = childMoveSelector;
    this.cacheType = cacheType;
    this.probabilityWeightFactory =
        (SelectionProbabilityWeightFactory<Solution_, Move<Solution_>>) probabilityWeightFactory;
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
    phaseLifecycleSupport.addEventListener(new SelectionCacheLifecycleBridge(cacheType, this));
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
    var completeMoveMap = new TreeMap<Double, Move<Solution_>>();
    ScoreDirector<Solution_> scoreDirector = solverScope.getScoreDirector();
    double probabilityWeightOffset = 0L;
    boolean setup = SelectionAttemptContext.isSetup();
    var iterator = childMoveSelector.iterator();
    while (setup ? !KnownExhaustionIterator.isExhausted(iterator) : iterator.hasNext()) {
      if (setup) {
        SelectionAttemptContext.beforeSelection();
        if (!iterator.hasNext()) break;
      }
      Move<Solution_> entity = iterator.next();
      double probabilityWeight =
          probabilityWeightFactory.createProbabilityWeight(scoreDirector, entity);
      completeMoveMap.put(probabilityWeightOffset, entity);
      probabilityWeightOffset += probabilityWeight;
      if (setup) SelectionAttemptContext.recordSetupProposal();
    }
    cachedMoveMap = completeMoveMap;
    probabilityWeightTotal = probabilityWeightOffset;
  }

  @Override
  public void disposeCache(SolverScope<Solution_> solverScope) {
    probabilityWeightTotal = -1.0;
  }

  @Override
  public boolean isNeverEnding() {
    return true;
  }

  @Override
  public long getSize() {
    return cachedMoveMap.size();
  }

  @Override
  public Iterator<Move<Solution_>> iterator() {
    return new Iterator<Move<Solution_>>() {
      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public Move<Solution_> next() {
        double randomOffset = RandomUtils.nextDouble(workingRandom, probabilityWeightTotal);
        // entry is never null because randomOffset < probabilityWeightTotal
        return cachedMoveMap.floorEntry(randomOffset).getValue();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException(
            "The optional operation remove() is not supported.");
      }
    };
  }

  @Override
  public String toString() {
    return "Probability(" + childMoveSelector + ")";
  }
}
