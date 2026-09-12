package greycos.solver.core.api.solver.alns;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.Score;

/**
 * A policy instance belongs to exactly one phase on one island. Selection must return one of the
 * supplied eligible pairs. Feedback contains only immutable outer-trial results; insertion scoring
 * probes are not observations. Cancellation must not be treated as a failed operator sample.
 */
public interface AlnsSelectionPolicy<Score_ extends Score<Score_>> {
  AlnsOperatorPair select(List<AlnsOperatorPair> eligiblePairs, RandomGenerator random);

  default void update(AlnsTrialResult<Score_> result) {}

  default Map<String, Double> weights() {
    return Map.of();
  }
}
