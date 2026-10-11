package greycos.solver.greybay.impl.objective;

import greycos.solver.core.api.score.BendableBigDecimalScore;
import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.Score;

/** Protects the array-backed built-in scores at the immutable observation boundary. */
public final class ScoreCopies {
  private ScoreCopies() {}

  @SuppressWarnings("unchecked")
  public static <Score_ extends Score<Score_>> Score_ copy(Score_ score) {
    return (Score_)
        switch (score) {
          case BendableScore bendable ->
              new BendableScore(
                  bendable.structuralScore(),
                  bendable.hardScores().clone(),
                  bendable.softScores().clone());
          case BendableBigDecimalScore bendable ->
              new BendableBigDecimalScore(
                  bendable.structuralScore(),
                  bendable.hardScores().clone(),
                  bendable.softScores().clone());
          case BendableDoubleScore bendable ->
              new BendableDoubleScore(
                  bendable.structuralScore(),
                  bendable.hardScores().clone(),
                  bendable.softScores().clone());
          case BendableFloatScore bendable ->
              new BendableFloatScore(
                  bendable.structuralScore(),
                  bendable.hardScores().clone(),
                  bendable.softScores().clone());
          default -> score;
        };
  }
}
