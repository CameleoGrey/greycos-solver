package ai.greycos.solver.core.impl.heuristic.move;

import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.testdomain.TestdataSolution;

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
