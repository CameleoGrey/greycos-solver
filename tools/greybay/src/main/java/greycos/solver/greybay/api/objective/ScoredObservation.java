package greycos.solver.greybay.api.objective;

import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.impl.objective.ScoreCopies;

/**
 * The original score of a successful, initialized, verified on-time evaluation. Construction is the
 * caller's assertion of these admission conditions; a score alone cannot certify initialization,
 * execution status or timing. Custom scores must obey the immutable {@link Score} contract.
 */
public record ScoredObservation<Score_ extends Score<Score_>>(
    String problemId, long seed, Score_ score) {
  public ScoredObservation {
    Objects.requireNonNull(problemId, "problemId");
    Objects.requireNonNull(score, "score");
    if (problemId.isBlank()) {
      throw new IllegalArgumentException("The problemId must not be blank.");
    }
    if (score.structuralScore() != 0L) {
      throw new IllegalArgumentException(
          "The score for problem ("
              + problemId
              + "), seed ("
              + seed
              + ") must have zero structural score, but was ("
              + score.structuralScore()
              + ").");
    }
    score = ScoreCopies.copy(score);
  }

  @Override
  public Score_ score() {
    return ScoreCopies.copy(score);
  }
}
