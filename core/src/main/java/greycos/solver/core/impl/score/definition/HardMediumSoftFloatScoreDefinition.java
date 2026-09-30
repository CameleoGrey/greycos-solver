package greycos.solver.core.impl.score.definition;

import java.util.Arrays;

import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;

/** Score definition for finite float levels. */
public class HardMediumSoftFloatScoreDefinition
    extends AbstractScoreDefinition<HardMediumSoftFloatScore> {

  public HardMediumSoftFloatScoreDefinition() {
    super(new String[] {"hard score", "medium score", "soft score"});
  }

  @Override
  public int getFeasibleLevelsSize() {
    return 1;
  }

  @Override
  public Class<HardMediumSoftFloatScore> getScoreClass() {
    return HardMediumSoftFloatScore.class;
  }

  @Override
  public HardMediumSoftFloatScore getStructurallyFlawedScore() {
    return FloatingScoreSupport.withStructuralScore(getZeroScore(), -1L);
  }

  @Override
  public HardMediumSoftFloatScore getStructurallyFlawedScore(HardMediumSoftFloatScore score) {
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return FloatingScoreSupport.withStructuralScore(score, -1L);
  }

  @Override
  public HardMediumSoftFloatScore getZeroScore() {
    return HardMediumSoftFloatScore.ZERO;
  }

  @Override
  public HardMediumSoftFloatScore getOneSoftestScore() {
    return HardMediumSoftFloatScore.ONE_SOFT;
  }

  @Override
  public HardMediumSoftFloatScore parseScore(String scoreString) {
    var score = HardMediumSoftFloatScore.parseScore(scoreString);
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return score;
  }

  @Override
  public HardMediumSoftFloatScore fromLevelNumbers(Number[] levelNumbers) {
    if (levelNumbers.length != getLevelsSize()) {
      throw new IllegalArgumentException(
          "The levelNumbers ("
              + Arrays.toString(levelNumbers)
              + ") must contain "
              + getLevelsSize()
              + " levels for HardMediumSoftFloatScore.");
    }
    return FloatingScoreSupport.fromLevels(getZeroScore(), levelNumbers);
  }

  @Override
  public HardMediumSoftFloatScore buildOptimisticBound(
      InitializingScoreTrend initializingScoreTrend, HardMediumSoftFloatScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, true);
  }

  @Override
  public HardMediumSoftFloatScore buildPessimisticBound(
      InitializingScoreTrend initializingScoreTrend, HardMediumSoftFloatScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, false);
  }

  @Override
  public HardMediumSoftFloatScore divideBySanitizedDivisor(
      HardMediumSoftFloatScore dividend, HardMediumSoftFloatScore divisor) {
    return FloatingScoreDefinitionSupport.divide(getZeroScore(), dividend, divisor);
  }

  @Override
  public Class<?> getNumericType() {
    return float.class;
  }
}
