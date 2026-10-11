package greycos.solver.greybay.api.objective;

import java.util.Objects;

import greycos.solver.core.api.score.IBendableScore;
import greycos.solver.core.api.score.Score;

/** Score implementation and level layout, including the runtime bendable hard/soft split. */
public record ScoreLayout(String scoreType, int levelCount, int hardLevelCount) {
  public ScoreLayout {
    Objects.requireNonNull(scoreType, "scoreType");
    if (scoreType.isBlank()
        || levelCount <= 0
        || hardLevelCount < -1
        || hardLevelCount > levelCount) {
      throw new IllegalArgumentException(
          "Invalid score layout (" + scoreType + ", " + levelCount + ", " + hardLevelCount + ").");
    }
  }

  /**
   * A hard-level count of -1 denotes a custom non-bendable score; native feasibility still applies.
   */
  public static ScoreLayout of(Score<?> score) {
    Objects.requireNonNull(score, "score");
    int levelCount = score.toLevelNumbers().length;
    int hardCount = -1;
    if (score instanceof IBendableScore<?> bendable) {
      if (bendable.hardLevelsSize() < 0
          || bendable.softLevelsSize() < 0
          || (long) bendable.hardLevelsSize() + bendable.softLevelsSize() != levelCount) {
        throw new IllegalArgumentException("Inconsistent bendable score layout (" + score + ").");
      }
      hardCount = bendable.hardLevelsSize();
    } else if (score.getClass().getPackageName().equals("greycos.solver.core.api.score")) {
      hardCount =
          switch (score.getClass().getSimpleName()) {
            case "SimpleScore", "SimpleBigDecimalScore", "SimpleDoubleScore", "SimpleFloatScore" ->
                0;
            case "HardSoftScore",
                "HardSoftBigDecimalScore",
                "HardSoftDoubleScore",
                "HardSoftFloatScore",
                "HardMediumSoftScore",
                "HardMediumSoftBigDecimalScore",
                "HardMediumSoftDoubleScore",
                "HardMediumSoftFloatScore" ->
                1;
            default -> -1;
          };
    }
    return new ScoreLayout(score.getClass().getName(), levelCount, hardCount);
  }
}
