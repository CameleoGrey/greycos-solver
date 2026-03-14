package ai.greycos.solver.core.impl.heuristic.move;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testutil.CodeAssertable;

public class DummyMove extends AbstractMove<TestdataSolution> implements CodeAssertable {

  protected String code;

  public DummyMove() {}

  public DummyMove(String code) {
    this.code = code;
  }

  @Override
  public String getCode() {
    return code;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

  @Override
  public boolean isMoveDoable(ScoreDirector<TestdataSolution> scoreDirector) {
    return true;
  }

  @Override
  protected void doMoveOnGenuineVariables(ScoreDirector<TestdataSolution> scoreDirector) {
    // do nothing
  }

  @Override
  public Collection<?> getPlanningEntities() {
    return Collections.emptyList();
  }

  @Override
  public Collection<?> getPlanningValues() {
    return Collections.emptyList();
  }

  @Override
  public String toString() {
    return Objects.requireNonNull(code, "null");
  }
}
