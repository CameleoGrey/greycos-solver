package greycos.solver.core.impl.heuristic.move;

import java.util.Collections;
import java.util.Objects;
import java.util.SequencedCollection;

import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.CodeAssertable;

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
  public SequencedCollection<Object> getPlanningEntities() {
    return Collections.emptyList();
  }

  @Override
  public SequencedCollection<Object> getPlanningValues() {
    return Collections.emptyList();
  }

  @Override
  public String toString() {
    return Objects.requireNonNull(code, "null");
  }
}
