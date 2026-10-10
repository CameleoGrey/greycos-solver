package greycos.solver.core.impl.islandmodel;

import java.util.Collections;
import java.util.SequencedCollection;

import greycos.solver.core.impl.heuristic.move.AbstractMove;
import greycos.solver.core.impl.move.SolutionAssignmentMove;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

import org.jspecify.annotations.NullMarked;

/** Applies an island snapshot without replacing working entities. */
@NullMarked
public final class SolutionSyncMove<Solution_> extends AbstractMove<Solution_> {

  private final SolutionAssignments<Solution_> assignments;
  private final SolutionAssignments<Solution_> completeSourceAssignments;

  private SolutionSyncMove(
      SolutionAssignments<Solution_> assignments,
      SolutionAssignments<Solution_> completeSourceAssignments) {
    this.assignments = assignments;
    this.completeSourceAssignments = completeSourceAssignments;
  }

  static <Solution_> SolutionSyncMove<Solution_> createMove(
      InnerScoreDirector<Solution_, ?> scoreDirector, Solution_ sourceSolution) {
    return new SolutionSyncMove<>(
        SolutionAssignments.capture(scoreDirector.getSolutionDescriptor(), sourceSolution)
            .rebase(scoreDirector),
        SolutionAssignments.captureComplete(scoreDirector.getSolutionDescriptor(), sourceSolution));
  }

  /**
   * Upgrades a migration queued by an earlier phase to the complete validation used by ILS.
   * Captured pinned bindings are retained even though legacy synchronization omits them.
   */
  public SolutionAssignmentMove<Solution_> toStrictMove(ScoreDirector<Solution_> scoreDirector) {
    return new SolutionAssignmentMove<>(completeSourceAssignments.rebase(scoreDirector));
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
    return new SolutionSyncMove<>(
        assignments.rebase(destinationScoreDirector), completeSourceAssignments);
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
