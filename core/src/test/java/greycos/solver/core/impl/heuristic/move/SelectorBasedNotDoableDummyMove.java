package greycos.solver.core.impl.heuristic.move;

import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.TestdataSolution;

public class SelectorBasedNotDoableDummyMove extends SelectorBasedDummyMove {

  public SelectorBasedNotDoableDummyMove() {}

  public SelectorBasedNotDoableDummyMove(String code) {
    super(code);
  }

  @Override
  public boolean isMoveDoable(ScoreDirector<TestdataSolution> scoreDirector) {
    return false;
  }
}
