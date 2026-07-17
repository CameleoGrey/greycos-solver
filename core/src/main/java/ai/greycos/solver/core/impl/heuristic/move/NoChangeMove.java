package ai.greycos.solver.core.impl.heuristic.move;

import java.util.Collections;
import java.util.SequencedCollection;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

/**
 * Makes no changes.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class NoChangeMove<Solution_> extends AbstractMove<Solution_> {

  public static final NoChangeMove<?> INSTANCE = new NoChangeMove<>();

  public static <Solution_> NoChangeMove<Solution_> getInstance() {
    return (NoChangeMove<Solution_>) INSTANCE;
  }

  NoChangeMove() {
    // No external instances allowed.
  }

  @Override
  public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
    return false;
  }

  @Override
  protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector) {
    // Do nothing.
  }

  @Override
  public Move<Solution_> rebase(ScoreDirector<Solution_> destinationScoreDirector) {
    return (Move<Solution_>) INSTANCE;
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
  public String getSimpleMoveTypeDescription() {
    return "No change";
  }
}
