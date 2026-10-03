package greycos.solver.core.api.solver.multistage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.score.Score;

/**
 * Evaluates alternatives against the current stage's state. A successful probe restores genuine
 * variables, shadow variables and the stored score to that stage's baseline before returning.
 *
 * <p>Operation validation may reject an alternative with {@link IllegalArgumentException}. When its
 * recorded changes roll back normally, the stage baseline is restored and the callback may try
 * another alternative.
 *
 * <p>Failures during mutation, shadow updates, scoring or undo abort the entire candidate and
 * require recovery of its original baseline, possibly by rebuilding state from snapshots. The
 * evaluator becomes unusable. Cancellation and exhaustion of the probe budget also abort the
 * candidate. Let these failures propagate; catching them does not allow evaluation to resume.
 *
 * <p>The evaluator is valid only inside its stage callback. Direct mutation of the working
 * solution, its entities, values or problem facts is unsupported. All operations must come from
 * this stage's evaluator or its typed variable views. The solver owns scoring, recording, rollback
 * and replay. In long custom loops that only read data or construct alternatives, call {@link
 * #checkTermination()} regularly; these individual reads and construction calls do not poll the
 * full termination policy.
 */
public interface MultistageMoveEvaluator<Solution_, Score_ extends Score<Score_>> {

  /** Returns the current working solution for read-only inspection. */
  Solution_ workingSolution();

  /** Evaluates an operation without retaining its changes. */
  MultistageEvaluation<Score_> evaluate(MultistageOperation<Solution_> operation);

  /**
   * Evaluates independent alternatives against the same state, in input order. The operations are
   * not cumulative. Every probe counts toward the configured per-candidate probe limit.
   */
  default List<MultistageEvaluation<Score_>> evaluateAll(
      List<? extends MultistageOperation<Solution_>> operations) {
    Objects.requireNonNull(operations);
    var evaluations = new ArrayList<MultistageEvaluation<Score_>>(operations.size());
    for (var operation : operations) {
      checkTermination();
      evaluations.add(evaluate(Objects.requireNonNull(operation)));
    }
    return List.copyOf(evaluations);
  }

  /**
   * Selects the alternative with the greatest exact evaluation, retaining the first on a tie. An
   * empty iterable aborts the candidate. The iterable must be finite; termination and probe budgets
   * are checked while evaluating it. Selecting the best alternative does not apply it.
   */
  default MultistageStageResult<Solution_> bestFit(
      Iterable<? extends MultistageOperation<Solution_>> operations) {
    Objects.requireNonNull(operations);
    MultistageOperation<Solution_> bestOperation = null;
    MultistageEvaluation<Score_> bestEvaluation = null;
    for (var operation : operations) {
      checkTermination();
      var evaluation = evaluate(Objects.requireNonNull(operation));
      if (bestEvaluation == null || evaluation.compareTo(bestEvaluation) > 0) {
        bestOperation = operation;
        bestEvaluation = evaluation;
      }
    }
    return bestOperation == null
        ? MultistageStageResult.abortCandidate()
        : MultistageStageResult.apply(bestOperation);
  }

  /**
   * Calculates an exact evaluation of the current stage baseline and restores its stored score
   * before returning. A scoring failure, cancellation or exhausted probe budget aborts the
   * candidate as described above.
   */
  MultistageEvaluation<Score_> currentEvaluation();

  /**
   * Creates an operation applying the given operations in order. Each component must belong to this
   * stage. The list is copied; an empty sequence leaves the candidate unchanged.
   */
  MultistageOperation<Solution_> sequence(
      List<? extends MultistageOperation<Solution_>> operations);

  /** Cooperatively stops candidate evaluation when solver termination or its budget is reached. */
  void checkTermination();
}
