package greycos.solver.core.impl.score.definition;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import greycos.solver.core.impl.score.FloatingPointMath;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;

/** Common definition operations which never use infinite score bounds. */
final class FloatingScoreDefinitionSupport {

  private FloatingScoreDefinitionSupport() {}

  static <Score_ extends Score<Score_>> Score_ bound(
      Score_ zero, Score_ score, InitializingScoreTrend trend, boolean optimistic) {
    FloatingScoreSupport.validateCompatible(zero, score);
    int count = FloatingScoreSupport.levelsSize(zero);
    if (trend.getLevelsSize() != count) {
      throw new IllegalArgumentException(
          "The initializingScoreTrend levels ("
              + trend.getLevelsSize()
              + ") must match score levels ("
              + count
              + ").");
    }
    var levels = new Number[count];
    boolean floatPrecision = FloatingScoreSupport.isFloatScore(zero);
    for (int i = 0; i < count; i++) {
      boolean unchanged =
          trend.trendLevels()[i]
              == (optimistic
                  ? InitializingScoreTrendLevel.ONLY_DOWN
                  : InitializingScoreTrendLevel.ONLY_UP);
      if (unchanged) {
        levels[i] = FloatingScoreSupport.level(score, i);
      } else if (floatPrecision) {
        levels[i] = optimistic ? Float.MAX_VALUE : -Float.MAX_VALUE;
      } else {
        levels[i] = optimistic ? Double.MAX_VALUE : -Double.MAX_VALUE;
      }
    }
    return FloatingScoreSupport.fromLevels(zero, levels);
  }

  static <Score_ extends Score<Score_>> Score_ divide(
      Score_ zero, Score_ dividend, Score_ divisor) {
    FloatingScoreSupport.validateCompatible(zero, dividend);
    FloatingScoreSupport.validateCompatible(zero, divisor);
    var levels = new Number[FloatingScoreSupport.levelsSize(zero)];
    for (int i = 0; i < levels.length; i++) {
      double divisorLevel = FloatingScoreSupport.level(divisor, i).doubleValue();
      if (divisorLevel == 0.0) {
        divisorLevel = 1.0;
      }
      if (FloatingScoreSupport.isFloatScore(zero)) {
        levels[i] =
            FloatingPointMath.divide(
                FloatingScoreSupport.level(dividend, i).floatValue(), divisorLevel);
      } else {
        levels[i] =
            FloatingPointMath.divide(
                FloatingScoreSupport.level(dividend, i).doubleValue(), divisorLevel);
      }
    }
    return FloatingScoreSupport.fromLevels(zero, levels);
  }
}
