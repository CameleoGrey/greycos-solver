package greycos.solver.core.impl.heuristic.selector.common.decorator;

import greycos.solver.core.impl.heuristic.selector.IterableSelector;
import greycos.solver.core.impl.score.director.ScoreDirector;

public class FairSelectorProbabilityWeightFactory<Solution_>
    implements SelectionProbabilityWeightFactory<Solution_, IterableSelector> {

  @Override
  public double createProbabilityWeight(
      ScoreDirector<Solution_> scoreDirector, IterableSelector selector) {
    return selector.getSize();
  }
}
