package greycos.solver.core.api.solver.multistage;

import greycos.solver.core.api.score.Score;

/**
 * Selects one operation against the current state of an ordered multistage candidate. The evaluator
 * and its operations belong to this callback and must not escape into another stage. A stage may
 * temporarily unassign mandatory decisions; the completed candidate must restore assignment
 * completeness and structural consistency before it can be accepted.
 */
@FunctionalInterface
public interface ListVariableCustomStage<Solution_, Entity_, Value_, Score_ extends Score<Score_>> {

  MultistageStageResult<Solution_> selectMove(
      ListVariableMoveEvaluator<Solution_, Entity_, Value_, Score_> evaluator);
}
