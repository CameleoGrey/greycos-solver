package ai.greycos.solver.core.impl.heuristic.move;

import java.util.Collections;
import java.util.SequencedCollection;

import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import ai.greycos.solver.core.preview.api.move.MutableSolutionView;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testutil.CodeAssertable;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class SelectorBasedDummyMove extends AbstractSelectorBasedMove<TestdataSolution>
    implements CodeAssertable {

  protected @Nullable String code;

  public SelectorBasedDummyMove() {}

  public SelectorBasedDummyMove(String code) {
    this.code = code;
  }

  @Override
  public @Nullable String getCode() {
    return code;
  }

  @Override
  protected void execute(
      MutableSolutionView<TestdataSolution> solutionView,
      VariableDescriptorAwareScoreDirector<TestdataSolution> scoreDirector) {
    // Do nothing.
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
    return code;
  }
}
