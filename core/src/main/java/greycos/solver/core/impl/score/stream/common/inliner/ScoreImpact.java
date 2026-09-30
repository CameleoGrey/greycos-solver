package greycos.solver.core.impl.score.stream.common.inliner;

import greycos.solver.core.api.score.Score;

import org.jspecify.annotations.Nullable;

public interface ScoreImpact<Score_ extends Score<Score_>> {

  /** Retracts the same contribution that was added when this impact was created. */
  void undo();

  /**
   * Returns this match's contribution. For floating-point scores, this is the product rounded once
   * to the score's precision, before it is added to the exact running total.
   */
  Score_ toScore();

  /** Original match weight, when retained for assertions that ignore the constraint weight. */
  default @Nullable Number matchWeight() {
    return null;
  }
}
