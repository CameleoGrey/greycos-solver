package greycos.solver.core.config.exhaustivesearch;

import java.util.Comparator;

import jakarta.xml.bind.annotation.XmlEnum;

import greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchNode;
import greycos.solver.core.impl.exhaustivesearch.node.comparator.BreadthFirstNodeComparator;
import greycos.solver.core.impl.exhaustivesearch.node.comparator.DepthFirstNodeComparator;
import greycos.solver.core.impl.exhaustivesearch.node.comparator.OptimisticBoundFirstNodeComparator;
import greycos.solver.core.impl.exhaustivesearch.node.comparator.OriginalOrderNodeComparator;
import greycos.solver.core.impl.exhaustivesearch.node.comparator.ScoreFirstNodeComparator;

import org.jspecify.annotations.NonNull;

@XmlEnum
public enum NodeExplorationType {
  ORIGINAL_ORDER,
  DEPTH_FIRST,
  BREADTH_FIRST,
  SCORE_FIRST,
  OPTIMISTIC_BOUND_FIRST;

  public @NonNull Comparator<ExhaustiveSearchNode> buildNodeComparator(
      boolean scoreBounderEnabled) {
    return switch (this) {
      case ORIGINAL_ORDER -> new OriginalOrderNodeComparator();
      case DEPTH_FIRST -> new DepthFirstNodeComparator(scoreBounderEnabled);
      case BREADTH_FIRST -> new BreadthFirstNodeComparator(scoreBounderEnabled);
      case SCORE_FIRST -> new ScoreFirstNodeComparator(scoreBounderEnabled);
      case OPTIMISTIC_BOUND_FIRST -> new OptimisticBoundFirstNodeComparator(scoreBounderEnabled);
    };
  }
}
