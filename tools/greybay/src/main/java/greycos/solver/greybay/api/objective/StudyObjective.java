package greycos.solver.greybay.api.objective;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.api.ComponentIdentity;

/**
 * A population-independent quality ordering. Implementations must be immutable and comparators
 * deterministic and transitive. Custom component identity includes the application artifact.
 */
public interface StudyObjective<Score_ extends Score<Score_>> {
  ComponentIdentity identity();

  /**
   * Supplies exact native score levels for persisted reports, paired comparisons and variance.
   * Objectives using custom number types must expose their registered adapter here as well as using
   * it for calibration and evaluation.
   */
  default ExactScoreAdapter<Score_> scoreAdapter() {
    return ExactScoreAdapter.standard();
  }

  ObjectiveCalibration calibrate(CompletedPanel<Score_> baseline);

  ObjectiveValue evaluate(CompletedPanel<Score_> panel, ObjectiveCalibration calibration);

  /**
   * Returns positive when left is better, negative when right is better, and zero on a quality tie.
   */
  int compare(ObjectiveValue left, ObjectiveValue right);

  ObjectiveCodec codec();
}
