package greycos.solver.core.api.solver.alns;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.Score;

/**
 * An island-local operator context. The framework creates an independent context for each island.
 * Contexts, mutation views and working objects must not be retained beyond an operator callback or
 * shared with another thread. All mutations must use this interface; direct entity, shadow-variable
 * and problem-fact changes are unsupported and cannot be rolled back safely.
 *
 * <p>Destroy selection must leave the incumbent unchanged. A repair may mutate only the bindings
 * selected for that trial. The framework evaluates one complete candidate and either retains that
 * state or restores the incumbent; it never re-executes the operator to commit its result.
 */
public interface AlnsContext<Solution_, Score_ extends Score<Score_>>
    extends AlnsMutableSolutionView<Solution_> {
  /** Read-only business/model data. Direct writes are unsupported. */
  Solution_ workingSolution();

  List<AlnsVariable<Solution_>> variables();

  /** Movable, currently assigned bindings. Pinned entities and list prefixes are excluded. */
  List<AlnsTarget<Solution_>> targets();

  /**
   * Unassigned basic bindings and list bindings reachable by a movable destination; the framework
   * selects recovery targets.
   */
  List<AlnsTarget<Solution_>> unassignedTargets();

  /**
   * Unresolved bindings in the current repair. Assigning an optional unassigned alternative
   * resolves it.
   */
  List<AlnsTarget<Solution_>> pendingTargets();

  /** All legal assignments at the current state, including unassignment exactly when allowed. */
  List<AlnsAssignment<Solution_>> assignments(AlnsTarget<Solution_> target);

  /** Current placement, regardless of the original placement stored on the target handle. */
  AlnsAssignment<Solution_> currentAssignment(AlnsTarget<Solution_> target);

  /** Calculates a fresh incremental score; incomplete mandatory assignments remain explicit. */
  AlnsEvaluation<Score_> score();

  /** Score of the unchanged incumbent at the start of this trial. */
  Score_ incumbentScore();

  /**
   * Applies a scratch change, scores it, then restores the exact enclosing state and pending set.
   * Nested evaluations use LIFO savepoints. A scoring probe is not an outer ALNS trial and receives
   * no operator reward. Cancellation and exceptions also unwind the scratch change. A failure
   * inside an incomplete primitive may abort the entire trial to restore the incumbent.
   */
  AlnsEvaluation<Score_> evaluate(AlnsChange<Solution_> change);

  default AlnsEvaluation<Score_> evaluate(AlnsAssignment<Solution_> assignment) {
    return evaluate(view -> view.assign(assignment));
  }

  /**
   * Evaluates independent assignments against the current state, returning one result in input
   * order for each assignment. Every probe restores the enclosing state. Implementations may score
   * these framework-owned assignments concurrently; callbacks and working objects remain confined
   * to the calling thread. The default implementation evaluates them sequentially.
   */
  default List<AlnsEvaluation<Score_>> evaluateAssignments(
      List<AlnsAssignment<Solution_>> assignments) {
    var evaluations = new ArrayList<AlnsEvaluation<Score_>>(assignments.size());
    for (var assignment : assignments) {
      checkTermination();
      evaluations.add(evaluate(assignment));
    }
    return List.copyOf(evaluations);
  }

  /**
   * Evaluates independent removals against the current state, returning results in input order.
   * Each target is removed in its own scratch probe; the removals are not cumulative. The enclosing
   * state and pending targets are restored after every probe. The default implementation evaluates
   * them sequentially.
   */
  default List<AlnsEvaluation<Score_>> evaluateRemovals(List<AlnsTarget<Solution_>> targets) {
    var evaluations = new ArrayList<AlnsEvaluation<Score_>>(targets.size());
    for (var target : targets) {
      checkTermination();
      evaluations.add(evaluate(view -> view.destroy(target)));
    }
    return List.copyOf(evaluations);
  }

  /** Applies a change to the current candidate without making an acceptance decision. */
  void execute(AlnsChange<Solution_> change);

  default void destroy(List<AlnsTarget<Solution_>> targets) {
    targets.forEach(this::destroy);
  }

  /**
   * The solver-owned generator for this callback; do not retain it or create an independent RNG.
   */
  RandomGenerator random();

  /**
   * Cooperatively aborts at a balanced mutation boundary when a budget or termination is reached.
   */
  void checkTermination();
}
