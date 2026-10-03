package greycos.solver.core.api.solver.multistage;

import greycos.solver.core.api.score.Score;

/**
 * Evaluates operations across the genuine variables declared by one selector. Each typed view
 * belongs to this stage and shares its transaction, scoring probes, budget and termination checks.
 * Views and operations must not escape the stage callback.
 *
 * <p>Operations from different views of this stage may be combined with {@link #sequence} and
 * evaluated as one alternative. A sequence applies its components in order and scores their final
 * state. A successful probe restores every affected variable to the stage baseline. The common
 * evaluation methods on any view evaluate the entire solution, including the other declared
 * variables.
 *
 * <p>A view can only access its declared entity scope. The model's existing restrictions on basic
 * and list variables still apply; declaring references does not expand the supported model.
 */
public interface CrossVariableMoveEvaluator<Solution_, Score_ extends Score<Score_>>
    extends MultistageMoveEvaluator<Solution_, Score_> {

  /**
   * Returns the basic variable's typed view. The reference must equal a declaration in this
   * selector; an undeclared reference is rejected before any operation is created.
   */
  <Entity_, Value_> BasicVariableMoveEvaluator<Solution_, Entity_, Value_, Score_> basic(
      BasicVariableReference<Entity_, Value_> variable);

  /**
   * Returns the list variable's typed view. The reference must equal a declaration in this
   * selector; an undeclared reference is rejected before any operation is created.
   */
  <Entity_, Value_> ListVariableMoveEvaluator<Solution_, Entity_, Value_, Score_> list(
      ListVariableReference<Entity_, Value_> variable);
}
