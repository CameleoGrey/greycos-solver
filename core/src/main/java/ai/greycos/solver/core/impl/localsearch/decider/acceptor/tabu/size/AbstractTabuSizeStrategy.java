package ai.greycos.solver.core.impl.localsearch.decider.acceptor.tabu.size;

public abstract sealed class AbstractTabuSizeStrategy<Solution_>
    implements TabuSizeStrategy<Solution_>
    permits EntityRatioTabuSizeStrategy, FixedTabuSizeStrategy {

  protected int protectTabuSizeCornerCases(int totalSize, int tabuSize) {
    if (tabuSize < 1) {
      // At least one object should be tabu, even if totalSize is 0
      tabuSize = 1;
    } else if (tabuSize > totalSize - 1) {
      // At least one object should not be tabu
      tabuSize = totalSize - 1;
    }
    return tabuSize;
  }
}
