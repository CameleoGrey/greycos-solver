package greycos.solver.core.impl.islandmodel;

import java.util.Collections;
import java.util.SequencedCollection;

import greycos.solver.core.impl.heuristic.move.AbstractMove;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

import org.jspecify.annotations.NullMarked;

/** Applies an island snapshot without replacing working entities. */
@NullMarked
final class SolutionSyncMove<Solution_> extends AbstractMove<Solution_> {

  private final SolutionAssignments<Solution_> assignments;

  private SolutionSyncMove(SolutionAssignments<Solution_> assignments) {
    this.assignments = assignments;
  }

  static <Solution_> SolutionSyncMove<Solution_> createMove(
      InnerScoreDirector<Solution_, ?> scoreDirector, Solution_ sourceSolution) {
    return new SolutionSyncMove<>(
        SolutionAssignments.capture(scoreDirector.getSolutionDescriptor(), sourceSolution)
            .rebase(scoreDirector));
  }

  @Override
  protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector) {
    assignments.apply((VariableDescriptorAwareScoreDirector<Solution_>) scoreDirector);
  }

  @Override
  public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
    return true;
  }

  @Override
  public SolutionSyncMove<Solution_> rebase(ScoreDirector<Solution_> destinationScoreDirector) {
    return new SolutionSyncMove<>(assignments.rebase(destinationScoreDirector));
  }

  @Override
  public String getSimpleMoveTypeDescription() {
    return "SolutionSyncMove";
  }

  @Override
  public SequencedCollection<Object> getPlanningEntities() {
    return assignments.getPlanningEntities();
  }

  @Override
  public SequencedCollection<Object> getPlanningValues() {
    return Collections.emptyList();
  }

  @Override
  public String toString() {
    return "SolutionSyncMove{" + assignments + "}";
  }
}
