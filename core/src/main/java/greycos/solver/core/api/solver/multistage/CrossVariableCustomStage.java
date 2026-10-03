package greycos.solver.core.api.solver.multistage;

import greycos.solver.core.api.score.Score;

/**
 * Selects one operation, potentially combining several declared variables, against the current
 * state of an ordered multistage candidate. The evaluator, its typed views and its operations
 * belong to this callback. Temporary mandatory unassignment is permitted; the completed candidate
 * must restore assignment completeness and structural consistency before it can be accepted.
 */
@FunctionalInterface
public interface CrossVariableCustomStage<Solution_, Score_ extends Score<Score_>> {

  MultistageStageResult<Solution_> selectMove(
      CrossVariableMoveEvaluator<Solution_, Score_> evaluator);
}
