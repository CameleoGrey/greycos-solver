package greycos.solver.quarkus.testcotwin.iteratedlocalsearch;

import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

public final class TestdataQuarkusIteratedLocalSearchFilters {
  private TestdataQuarkusIteratedLocalSearchFilters() {}

  public static final class Inner implements SelectionFilter<TestdataQuarkusSolution, Object> {
    @Override
    public boolean accept(ScoreDirector<TestdataQuarkusSolution> scoreDirector, Object selection) {
      return true;
    }
  }

  public static final class Perturbation
      implements SelectionFilter<TestdataQuarkusSolution, Object> {
    @Override
    public boolean accept(ScoreDirector<TestdataQuarkusSolution> scoreDirector, Object selection) {
      return true;
    }
  }
}
