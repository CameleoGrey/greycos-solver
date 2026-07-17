package ai.greycos.solver.core.impl.heuristic.move;

import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

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
