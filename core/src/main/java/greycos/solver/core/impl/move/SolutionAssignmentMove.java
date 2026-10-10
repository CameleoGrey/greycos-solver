package greycos.solver.core.impl.move;

import java.util.Collections;
import java.util.Objects;
import java.util.SequencedCollection;

import greycos.solver.core.impl.heuristic.move.AbstractMove;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Restores a complete genuine-assignment snapshot using native recorded notifications. Validation
 * occurs before the first write, and shadow propagation and undo are owned by the move director.
 * This administrative transition does not replace working entities or the score director session.
 */
@NullMarked
public final class SolutionAssignmentMove<Solution_> extends AbstractMove<Solution_> {

  private final SolutionAssignments<Solution_> assignments;

  public SolutionAssignmentMove(SolutionAssignments<Solution_> assignments) {
    this.assignments = Objects.requireNonNull(assignments);
  }

  @Override
  protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector) {
    var variableAwareDirector = (VariableDescriptorAwareScoreDirector<Solution_>) scoreDirector;
    assignments.validateComplete(variableAwareDirector);
    assignments.apply(variableAwareDirector);
  }

  @Override
  public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
    var variableAwareDirector = (VariableDescriptorAwareScoreDirector<Solution_>) scoreDirector;
    assignments.validateComplete(variableAwareDirector);
    return !assignments.matchesCurrent(variableAwareDirector);
  }

  @Override
  public SolutionAssignmentMove<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    return new SolutionAssignmentMove<>(assignments.rebase(destinationScoreDirector));
  }

  @Override
  public String getSimpleMoveTypeDescription() {
    return "SolutionAssignmentMove";
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
    return "SolutionAssignmentMove{" + assignments + "}";
  }
}
