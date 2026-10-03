package greycos.solver.core.api.solver.multistage;

import java.util.List;

import greycos.solver.core.api.score.Score;

import org.jspecify.annotations.Nullable;

/** Evaluates changes to one basic planning variable, including as a cross-variable stage's view. */
public interface BasicVariableMoveEvaluator<
        Solution_, Entity_, Value_, Score_ extends Score<Score_>>
    extends MultistageMoveEvaluator<Solution_, Score_> {

  @Nullable Value_ currentValue(Entity_ entity);

  /**
   * Returns the legal values in domain order. Null is included only for optional variables. The
   * entity must belong to this working solution and be movable.
   */
  List<@Nullable Value_> legalValues(Entity_ entity);

  /** Assigns a value from the entity's range. Null is permitted only for optional variables. */
  MultistageOperation<Solution_> assign(Entity_ entity, @Nullable Value_ value);

  /** Exchanges the current values of two movable entities when both resulting values are legal. */
  MultistageOperation<Solution_> swap(Entity_ leftEntity, Entity_ rightEntity);

  /**
   * Temporarily unassigns a movable entity, including a mandatory variable. A candidate that leaves
   * a mandatory variable unassigned cannot be accepted.
   */
  MultistageOperation<Solution_> unassign(Entity_ entity);
}
