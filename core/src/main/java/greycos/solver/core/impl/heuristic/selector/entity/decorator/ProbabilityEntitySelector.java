package greycos.solver.core.impl.heuristic.selector.entity.decorator;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import greycos.solver.core.impl.heuristic.selector.common.KnownExhaustionIterator;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptContext;
import greycos.solver.core.impl.heuristic.selector.common.SelectionCacheLifecycleBridge;
import greycos.solver.core.impl.heuristic.selector.common.SelectionCacheLifecycleListener;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionProbabilityWeightFactory;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.random.RandomUtils;
import greycos.solver.core.impl.solver.scope.SolverScope;

public final class ProbabilityEntitySelector<Solution_>
    extends AbstractDemandEnabledSelector<Solution_>
    implements SelectionCacheLifecycleListener<Solution_>, EntitySelector<Solution_> {

  private final EntitySelector<Solution_> childEntitySelector;
  private final SelectionCacheType cacheType;
  private final SelectionProbabilityWeightFactory<Solution_, Object> probabilityWeightFactory;

  private NavigableMap<Double, Object> cachedEntityMap = null;
  private double probabilityWeightTotal = -1.0;

  public ProbabilityEntitySelector(
      EntitySelector<Solution_> childEntitySelector,
      SelectionCacheType cacheType,
      SelectionProbabilityWeightFactory<Solution_, Object> probabilityWeightFactory) {
    this.childEntitySelector = childEntitySelector;
    this.cacheType = cacheType;
    this.probabilityWeightFactory = probabilityWeightFactory;
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

  @Override
  public SelectionCacheType getCacheType() {
    return cacheType;
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  public void constructCache(SolverScope<Solution_> solverScope) {
    var completeMap = new TreeMap<Double, Object>();
    ScoreDirector<Solution_> scoreDirector = solverScope.getScoreDirector();
    double probabilityWeightOffset = 0L;
    boolean setup = SelectionAttemptContext.isSetup();
    var iterator = childEntitySelector.iterator();
    while (setup ? !KnownExhaustionIterator.isExhausted(iterator) : iterator.hasNext()) {
      if (setup) {
        SelectionAttemptContext.beforeSelection();
        if (!iterator.hasNext()) break;
      }
      Object entity = iterator.next();
      double probabilityWeight =
          probabilityWeightFactory.createProbabilityWeight(scoreDirector, entity);
      completeMap.put(probabilityWeightOffset, entity);
      probabilityWeightOffset += probabilityWeight;
      if (setup) SelectionAttemptContext.recordSetupProposal();
    }
    cachedEntityMap = completeMap;
    probabilityWeightTotal = probabilityWeightOffset;
  }

  @Override
  public void disposeCache(SolverScope<Solution_> solverScope) {
    cachedEntityMap = null;
    probabilityWeightTotal = -1.0;
  }

  @Override
  public EntityDescriptor<Solution_> getEntityDescriptor() {
    return childEntitySelector.getEntityDescriptor();
  }

  @Override
  public boolean isNeverEnding() {
    return true;
  }

  @Override
  public long getSize() {
    return cachedEntityMap.size();
  }

  @Override
  public Iterator<Object> iterator() {
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public Object next() {
        double randomOffset = RandomUtils.nextDouble(workingRandom, probabilityWeightTotal);
        // entry is never null because randomOffset < probabilityWeightTotal
        return cachedEntityMap.floorEntry(randomOffset).getValue();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException(
            "The optional operation remove() is not supported.");
      }
    };
  }

  @Override
  public ListIterator<Object> listIterator() {
    throw new IllegalStateException(
        "The selector (" + this + ") does not support a ListIterator with randomSelection (true).");
  }

  @Override
  public ListIterator<Object> listIterator(int index) {
    throw new IllegalStateException(
        "The selector (" + this + ") does not support a ListIterator with randomSelection (true).");
  }

  @Override
  public Iterator<Object> endingIterator() {
    return childEntitySelector.endingIterator();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    ProbabilityEntitySelector<?> that = (ProbabilityEntitySelector<?>) other;
    return Objects.equals(childEntitySelector, that.childEntitySelector)
        && cacheType == that.cacheType
        && Objects.equals(probabilityWeightFactory, that.probabilityWeightFactory);
  }

  @Override
  public int hashCode() {
    return Objects.hash(childEntitySelector, cacheType, probabilityWeightFactory);
  }

  @Override
  public String toString() {
    return "Probability(" + childEntitySelector + ")";
  }
}
