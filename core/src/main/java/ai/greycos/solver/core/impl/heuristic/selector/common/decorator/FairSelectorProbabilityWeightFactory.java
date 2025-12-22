package ai.greycos.solver.core.impl.heuristic.selector.common.decorator;

import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.heuristic.selector.IterableSelector;

public class FairSelectorProbabilityWeightFactory<Solution_>
    implements SelectionProbabilityWeightFactory<Solution_, IterableSelector> {

  @Override
  public double createProbabilityWeight(
      ScoreDirector<Solution_> scoreDirector, IterableSelector selector) {
    return selector.getSize();
  }
}
