package ai.greycos.solver.core.testdomain.difficultyweight;

import ai.greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionSorterWeightFactory;

public class TestdataDifficultyFactory
    implements SelectionSorterWeightFactory<
        TestdataDifficultyWeightSolution, TestdataDifficultyWeightEntity> {

  @Override
  public Comparable createSorterWeight(
      TestdataDifficultyWeightSolution testdataDifficultyWeightSolution,
      TestdataDifficultyWeightEntity selection) {
    return 0;
  }
}
