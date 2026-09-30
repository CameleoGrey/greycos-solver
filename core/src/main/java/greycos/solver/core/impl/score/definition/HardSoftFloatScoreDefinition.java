package greycos.solver.core.impl.score.definition;

import java.util.Arrays;

import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;

/** Score definition for finite float levels. */
public class HardSoftFloatScoreDefinition extends AbstractScoreDefinition<HardSoftFloatScore> {

  public HardSoftFloatScoreDefinition() {
    super(new String[] {"hard score", "soft score"});
  }

  @Override
  public int getFeasibleLevelsSize() {
    return 1;
  }

  @Override
  public Class<HardSoftFloatScore> getScoreClass() {
    return HardSoftFloatScore.class;
  }

  @Override
  public HardSoftFloatScore getStructurallyFlawedScore() {
    return FloatingScoreSupport.withStructuralScore(getZeroScore(), -1L);
  }

  @Override
  public HardSoftFloatScore getStructurallyFlawedScore(HardSoftFloatScore score) {
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return FloatingScoreSupport.withStructuralScore(score, -1L);
  }

  @Override
  public HardSoftFloatScore getZeroScore() {
    return HardSoftFloatScore.ZERO;
  }

  @Override
  public HardSoftFloatScore getOneSoftestScore() {
    return HardSoftFloatScore.ONE_SOFT;
  }

  @Override
  public HardSoftFloatScore parseScore(String scoreString) {
    var score = HardSoftFloatScore.parseScore(scoreString);
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return score;
  }

  @Override
  public HardSoftFloatScore fromLevelNumbers(Number[] levelNumbers) {
    if (levelNumbers.length != getLevelsSize()) {
      throw new IllegalArgumentException(
          "The levelNumbers ("
              + Arrays.toString(levelNumbers)
              + ") must contain "
              + getLevelsSize()
              + " levels for HardSoftFloatScore.");
    }
    return FloatingScoreSupport.fromLevels(getZeroScore(), levelNumbers);
  }

  @Override
  public HardSoftFloatScore buildOptimisticBound(
      InitializingScoreTrend initializingScoreTrend, HardSoftFloatScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, true);
  }

  @Override
  public HardSoftFloatScore buildPessimisticBound(
      InitializingScoreTrend initializingScoreTrend, HardSoftFloatScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, false);
  }

  @Override
  public HardSoftFloatScore divideBySanitizedDivisor(
      HardSoftFloatScore dividend, HardSoftFloatScore divisor) {
    return FloatingScoreDefinitionSupport.divide(getZeroScore(), dividend, divisor);
  }

  @Override
  public Class<?> getNumericType() {
    return float.class;
  }
}
