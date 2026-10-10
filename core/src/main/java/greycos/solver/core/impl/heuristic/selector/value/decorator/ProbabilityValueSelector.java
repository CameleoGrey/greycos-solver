package greycos.solver.core.impl.heuristic.selector.value.decorator;

import java.util.Iterator;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import greycos.solver.core.impl.heuristic.selector.common.KnownExhaustionIterator;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptContext;
import greycos.solver.core.impl.heuristic.selector.common.SelectionCacheLifecycleBridge;
import greycos.solver.core.impl.heuristic.selector.common.SelectionCacheLifecycleListener;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionProbabilityWeightFactory;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.random.RandomUtils;
import greycos.solver.core.impl.solver.scope.SolverScope;

public final class ProbabilityValueSelector<Solution_>
    extends AbstractDemandEnabledSelector<Solution_>
    implements IterableValueSelector<Solution_>, SelectionCacheLifecycleListener<Solution_> {

  private final IterableValueSelector<Solution_> childValueSelector;
  private final SelectionCacheType cacheType;
  private final SelectionProbabilityWeightFactory<Solution_, Object> probabilityWeightFactory;

  private NavigableMap<Double, Object> cachedEntityMap = null;
  private double probabilityWeightTotal = -1.0;

  public ProbabilityValueSelector(
      IterableValueSelector<Solution_> childValueSelector,
      SelectionCacheType cacheType,
      SelectionProbabilityWeightFactory<Solution_, Object> probabilityWeightFactory) {
    this.childValueSelector = childValueSelector;
    this.cacheType = cacheType;
    this.probabilityWeightFactory = probabilityWeightFactory;
    if (childValueSelector.isNeverEnding()) {
      throw new IllegalStateException(
          "The selector ("
              + this
              + ") has a childValueSelector ("
              + childValueSelector
              + ") with neverEnding ("
              + childValueSelector.isNeverEnding()
              + ").");
    }
    phaseLifecycleSupport.addEventListener(childValueSelector);
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
    var iterator = childValueSelector.iterator();
    while (setup ? !KnownExhaustionIterator.isExhausted(iterator) : iterator.hasNext()) {
      if (setup) {
        SelectionAttemptContext.beforeSelection();
        if (!iterator.hasNext()) break;
      }
      Object value = iterator.next();
      double probabilityWeight =
          probabilityWeightFactory.createProbabilityWeight(scoreDirector, value);
      completeMap.put(probabilityWeightOffset, value);
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
  public GenuineVariableDescriptor<Solution_> getVariableDescriptor() {
    return childValueSelector.getVariableDescriptor();
  }

  @Override
  public boolean isNeverEnding() {
    // Probability sampling repeats with replacement, even though its cached population is finite.
    return true;
  }

  @Override
  public long getSize(Object entity) {
    return getSize();
  }

  @Override
  public long getSize() {
    return cachedEntityMap.size();
  }

  @Override
  public Iterator<Object> iterator(Object entity) {
    return iterator();
  }

  @Override
  public Iterator<Object> iterator() {
    return new Iterator<Object>() {
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
  public Iterator<Object> endingIterator(Object entity) {
    return childValueSelector.endingIterator(entity);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    ProbabilityValueSelector<?> that = (ProbabilityValueSelector<?>) other;
    return Objects.equals(childValueSelector, that.childValueSelector)
        && cacheType == that.cacheType
        && Objects.equals(probabilityWeightFactory, that.probabilityWeightFactory);
  }

  @Override
  public int hashCode() {
    return Objects.hash(childValueSelector, cacheType, probabilityWeightFactory);
  }

  @Override
  public String toString() {
    return "Probability(" + childValueSelector + ")";
  }
}
