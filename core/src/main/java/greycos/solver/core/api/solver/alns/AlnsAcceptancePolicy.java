package greycos.solver.core.api.solver.alns;

import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.Score;

/**
 * Acceptance of one complete candidate; repair evaluations never call this policy. Each instance
 * belongs to one solve/island. All score arguments are immutable; the policy never receives mutable
 * working state. {@link #stepEnded(Score)} runs once per completed trial, including rejection and
 * repair failure. {@link #incumbentChanged(Score)} signals adoption of another island's incumbent.
 */
public interface AlnsAcceptancePolicy<Score_ extends Score<Score_>> {
  default void initialize(Score_ score) {}

  boolean isAccepted(Score_ current, Score_ candidate, RandomGenerator random);

  default void stepEnded(Score_ resultingIncumbent) {}

  default void incumbentChanged(Score_ score) {}
}
