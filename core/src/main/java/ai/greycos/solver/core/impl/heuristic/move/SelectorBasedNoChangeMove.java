package ai.greycos.solver.core.impl.heuristic.move;

import java.util.Collections;
import java.util.SequencedCollection;

import ai.greycos.solver.core.api.cotwin.lookup.Lookup;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import ai.greycos.solver.core.preview.api.move.Move;
import ai.greycos.solver.core.preview.api.move.MutableSolutionView;

import org.jspecify.annotations.NullMarked;

/**
 * Makes no changes.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public final class SelectorBasedNoChangeMove<Solution_>
    extends AbstractSelectorBasedMove<Solution_> {

  public static final SelectorBasedNoChangeMove<?> INSTANCE = new SelectorBasedNoChangeMove<>();

  @SuppressWarnings("unchecked")
  public static <Solution_> SelectorBasedNoChangeMove<Solution_> getInstance() {
    return (SelectorBasedNoChangeMove<Solution_>) INSTANCE;
  }

  private SelectorBasedNoChangeMove() {
    // No external instances allowed.
  }

  @Override
  public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
    return false;
  }

  @Override
  protected void execute(
      MutableSolutionView<Solution_> solutionView,
      VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    // Do nothing.
  }

  @Override
  public Move<Solution_> rebase(Lookup lookup) {
    return getInstance();
  }

  @Override
  public SequencedCollection<Object> getPlanningEntities() {
    return Collections.emptyList();
  }

  @Override
  public SequencedCollection<Object> getPlanningValues() {
    return Collections.emptyList();
  }
}
