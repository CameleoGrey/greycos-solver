package ai.greycos.solver.core.impl.heuristic.move;

import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

public class NotDoableDummyMove extends DummyMove {

  public NotDoableDummyMove() {}

  public NotDoableDummyMove(String code) {
    super(code);
  }

  @Override
  public boolean isMoveDoable(ScoreDirector<TestdataSolution> scoreDirector) {
    return false;
  }
}
