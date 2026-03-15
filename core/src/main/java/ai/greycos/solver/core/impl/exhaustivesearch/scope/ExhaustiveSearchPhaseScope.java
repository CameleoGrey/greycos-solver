package ai.greycos.solver.core.impl.exhaustivesearch.scope;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.SortedSet;
import java.util.TreeMap;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchLayer;
import ai.greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchNode;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public final class ExhaustiveSearchPhaseScope<Solution_> extends AbstractPhaseScope<Solution_> {

  private List<ExhaustiveSearchLayer> layerList;
  private SortedSet<ExhaustiveSearchNode> expandableNodeQueue;
  private final NavigableMap<InnerScore<?>, List<ExhaustiveSearchNode>> optimisticBoundBuckets;
  private InnerScore<?> bestPessimisticBound;

  private ExhaustiveSearchStepScope<Solution_> lastCompletedStepScope;

  public ExhaustiveSearchPhaseScope(SolverScope<Solution_> solverScope, int phaseIndex) {
    super(solverScope, phaseIndex, false);
    optimisticBoundBuckets = new TreeMap<>(ExhaustiveSearchPhaseScope::compareInnerScore);
    lastCompletedStepScope = new ExhaustiveSearchStepScope<>(this, -1);
  }

  public List<ExhaustiveSearchLayer> getLayerList() {
    return layerList;
  }

  public void setLayerList(List<ExhaustiveSearchLayer> layerList) {
    this.layerList = layerList;
  }

  public SortedSet<ExhaustiveSearchNode> getExpandableNodeQueue() {
    return expandableNodeQueue;
  }

  public void setExpandableNodeQueue(SortedSet<ExhaustiveSearchNode> expandableNodeQueue) {
    this.expandableNodeQueue = expandableNodeQueue;
    optimisticBoundBuckets.clear();
  }

  @SuppressWarnings("unchecked")
  public <Score_ extends Score<Score_>> InnerScore<Score_> getBestPessimisticBound() {
    return (InnerScore<Score_>) bestPessimisticBound;
  }

  public void setBestPessimisticBound(InnerScore<?> bestPessimisticBound) {
    this.bestPessimisticBound = bestPessimisticBound;
  }

  @Override
  public ExhaustiveSearchStepScope<Solution_> getLastCompletedStepScope() {
    return lastCompletedStepScope;
  }

  public void setLastCompletedStepScope(
      ExhaustiveSearchStepScope<Solution_> lastCompletedStepScope) {
    this.lastCompletedStepScope = lastCompletedStepScope;
  }

  // ************************************************************************
  // Calculated methods
  // ************************************************************************

  public int getDepthSize() {
    return layerList.size();
  }

  public <Score_ extends Score<Score_>> void registerPessimisticBound(
      InnerScore<Score_> pessimisticBound) {
    var castBestPessimisticBound = this.<Score_>getBestPessimisticBound();
    if (pessimisticBound.compareTo(castBestPessimisticBound) > 0) {
      bestPessimisticBound = pessimisticBound;
      pruneDominatedBuckets(pessimisticBound);
    }
  }

  public void addExpandableNode(ExhaustiveSearchNode moveNode) {
    if (!expandableNodeQueue.add(moveNode)) {
      return;
    }
    moveNode.setExpandable(true);
    addToBoundBucket(moveNode);
  }

  public ExhaustiveSearchNode pollExpandableNode() {
    while (!expandableNodeQueue.isEmpty()) {
      var node = expandableNodeQueue.last();
      expandableNodeQueue.remove(node);
      removeFromBoundBucket(node);

      if (isDominatedByBestBound(node)) {
        node.setExpandable(false);
        continue;
      }
      node.setExpandable(false);
      return node;
    }
    return null;
  }

  private void addToBoundBucket(ExhaustiveSearchNode node) {
    var optimisticBound = node.getOptimisticBound();
    if (optimisticBound == null) {
      return;
    }
    optimisticBoundBuckets.computeIfAbsent(optimisticBound, ignored -> new ArrayList<>()).add(node);
  }

  private void removeFromBoundBucket(ExhaustiveSearchNode node) {
    var optimisticBound = node.getOptimisticBound();
    if (optimisticBound == null) {
      return;
    }
    var bucket = optimisticBoundBuckets.get(optimisticBound);
    if (bucket == null) {
      return;
    }
    bucket.remove(node);
    if (bucket.isEmpty()) {
      optimisticBoundBuckets.remove(optimisticBound);
    }
  }

  @SuppressWarnings("unchecked")
  private <Score_ extends Score<Score_>> void pruneDominatedBuckets(
      InnerScore<Score_> pessimisticBound) {
    var dominatedBuckets = optimisticBoundBuckets.headMap(pessimisticBound, true);
    if (dominatedBuckets.isEmpty()) {
      return;
    }
    var doomedNodes = new ArrayList<ExhaustiveSearchNode>();
    for (var bucket : dominatedBuckets.values()) {
      doomedNodes.addAll(bucket);
    }
    dominatedBuckets.clear();
    for (var doomedNode : doomedNodes) {
      if (expandableNodeQueue.remove(doomedNode)) {
        doomedNode.setExpandable(false);
      }
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareInnerScore(InnerScore<?> left, InnerScore<?> right) {
    return ((Comparable) left).compareTo(right);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private boolean isDominatedByBestBound(ExhaustiveSearchNode node) {
    if (bestPessimisticBound == null) {
      return false;
    }
    var optimisticBound = node.getOptimisticBound();
    if (optimisticBound == null) {
      return false;
    }
    return ((Comparable) optimisticBound).compareTo(bestPessimisticBound) <= 0;
  }
}
