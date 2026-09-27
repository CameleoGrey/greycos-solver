package greycos.solver.core.impl.partitionedsearch.scope;

import java.util.SequencedCollection;

import greycos.solver.core.impl.heuristic.move.AbstractMove;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

/** Immutable genuine assignments published by one partition. */
public final class PartitionChangeMove<Solution_> extends AbstractMove<Solution_> {

  private final SolutionAssignments<Solution_> assignments;
  private final int partIndex;

  private PartitionChangeMove(SolutionAssignments<Solution_> assignments, int partIndex) {
    this.assignments = assignments;
    this.partIndex = partIndex;
  }

  public static <Solution_> PartitionChangeMove<Solution_> createMove(
      InnerScoreDirector<Solution_, ?> scoreDirector, int partIndex) {
    return createMove(scoreDirector, scoreDirector.getWorkingSolution(), partIndex);
  }

  public static <Solution_> PartitionChangeMove<Solution_> createMove(
      InnerScoreDirector<Solution_, ?> scoreDirector, Solution_ bestSolution, int partIndex) {
    return new PartitionChangeMove<>(
        SolutionAssignments.capture(scoreDirector.getSolutionDescriptor(), bestSolution),
        partIndex);
  }

  public int getPartIndex() {
    return partIndex;
  }

  public SolutionAssignments<Solution_> getAssignments() {
    return assignments;
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
  public PartitionChangeMove<Solution_> rebase(ScoreDirector<Solution_> destinationScoreDirector) {
    return new PartitionChangeMove<>(assignments.rebase(destinationScoreDirector), partIndex);
  }

  @Override
  public SequencedCollection<Object> getPlanningEntities() {
    return assignments.getPlanningEntities();
  }

  @Override
  public SequencedCollection<Object> getPlanningValues() {
    throw new UnsupportedOperationException(
        "PartitionChangeMove communicates assignments between a partition and its parent solver.");
  }

  @Override
  public String toString() {
    return "part-" + partIndex + " {" + assignments + "}";
  }
}
