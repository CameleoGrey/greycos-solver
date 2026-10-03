package greycos.solver.core.impl.localsearch.decider.acceptor.tabu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.Set;

import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Planning value identities captured while an evaluated candidate is still applied. */
@NullMarked
public record PlanningValueSnapshot(SequencedCollection<@Nullable Object> values)
    implements MoveEvaluationPipeline.EvaluationMetadata {

  public PlanningValueSnapshot {
    // List.copyOf rejects null, which is a valid tabu value for an unassigned basic variable.
    values = Collections.unmodifiableList(new ArrayList<>(values));
  }

  /** Called after scoring and before undo; no move introspection is needed for invalid states. */
  public static <Solution_> @Nullable PlanningValueSnapshot capture(
      InnerScoreDirector<Solution_, ?> director, Move<Solution_> move) {
    var score = director.getSolutionDescriptor().getScore(director.getWorkingSolution());
    if (score == null) {
      throw new IllegalStateException(
          "Planning value metadata must be captured after the candidate has been scored.");
    }
    if (score.structuralScore() < 0) {
      return null;
    }
    return new PlanningValueSnapshot(move.getPlanningValues());
  }

  /**
   * Resolves worker identities against one coordinator solution. Recreate after a solution or
   * repository reset; captured collections never become the tabu map's worker-owned keys.
   */
  public static final class Rebaser<Solution_> {

    private final InnerScoreDirector<Solution_, ?> coordinator;
    private final Set<Object> coordinatorObjects =
        Collections.newSetFromMap(new IdentityHashMap<>());

    public Rebaser(InnerScoreDirector<Solution_, ?> coordinator) {
      this.coordinator = Objects.requireNonNull(coordinator);
      coordinator
          .getSolutionDescriptor()
          .visitAll(coordinator.getWorkingSolution(), coordinatorObjects::add);
    }

    public PlanningValueSnapshot rebase(PlanningValueSnapshot snapshot) {
      var rebasedValues = new ArrayList<@Nullable Object>(snapshot.values.size());
      for (var value : snapshot.values) {
        // Shared problem facts need not have a PlanningId. Only preserve an exact coordinator
        // instance; an unknown clone must go through normal lookup and its validation.
        rebasedValues.add(
            value == null || coordinatorObjects.contains(value)
                ? value
                : Objects.requireNonNull(coordinator.lookUpWorkingObject(value)));
      }
      return new PlanningValueSnapshot(rebasedValues);
    }
  }
}
