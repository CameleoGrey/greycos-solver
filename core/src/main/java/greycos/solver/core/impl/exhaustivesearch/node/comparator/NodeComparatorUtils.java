package greycos.solver.core.impl.exhaustivesearch.node.comparator;

import greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchNode;

final class NodeComparatorUtils {

  private NodeComparatorUtils() {}

  @SuppressWarnings({"rawtypes", "unchecked"})
  static int compareOptimisticBounds(ExhaustiveSearchNode a, ExhaustiveSearchNode b) {
    var aBound = a.getOptimisticBound();
    var bBound = b.getOptimisticBound();
    // A missing bound is unknown, and therefore may be better than every finite bound.
    if (aBound == null) {
      return bBound == null ? 0 : 1;
    }
    return bBound == null ? -1 : aBound.compareTo(bBound);
  }
}
