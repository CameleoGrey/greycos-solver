package greycos.solver.core.impl.score.definition;

import java.util.Arrays;

import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;

/** Score definition for finite double levels. */
public class HardSoftDoubleScoreDefinition extends AbstractScoreDefinition<HardSoftDoubleScore> {

  public HardSoftDoubleScoreDefinition() {
    super(new String[] {"hard score", "soft score"});
  }

  @Override
  public int getFeasibleLevelsSize() {
    return 1;
  }

  @Override
  public Class<HardSoftDoubleScore> getScoreClass() {
    return HardSoftDoubleScore.class;
  }

  @Override
  public HardSoftDoubleScore getStructurallyFlawedScore() {
    return FloatingScoreSupport.withStructuralScore(getZeroScore(), -1L);
  }

  @Override
  public HardSoftDoubleScore getStructurallyFlawedScore(HardSoftDoubleScore score) {
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return FloatingScoreSupport.withStructuralScore(score, -1L);
  }

  @Override
  public HardSoftDoubleScore getZeroScore() {
    return HardSoftDoubleScore.ZERO;
  }

  @Override
  public HardSoftDoubleScore getOneSoftestScore() {
    return HardSoftDoubleScore.ONE_SOFT;
  }

  @Override
  public HardSoftDoubleScore parseScore(String scoreString) {
    var score = HardSoftDoubleScore.parseScore(scoreString);
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return score;
  }

  @Override
  public HardSoftDoubleScore fromLevelNumbers(Number[] levelNumbers) {
    if (levelNumbers.length != getLevelsSize()) {
      throw new IllegalArgumentException(
          "The levelNumbers ("
              + Arrays.toString(levelNumbers)
              + ") must contain "
              + getLevelsSize()
              + " levels for HardSoftDoubleScore.");
    }
    return FloatingScoreSupport.fromLevels(getZeroScore(), levelNumbers);
  }

  @Override
  public HardSoftDoubleScore buildOptimisticBound(
      InitializingScoreTrend initializingScoreTrend, HardSoftDoubleScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, true);
  }

  @Override
  public HardSoftDoubleScore buildPessimisticBound(
      InitializingScoreTrend initializingScoreTrend, HardSoftDoubleScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, false);
  }

  @Override
  public HardSoftDoubleScore divideBySanitizedDivisor(
      HardSoftDoubleScore dividend, HardSoftDoubleScore divisor) {
    return FloatingScoreDefinitionSupport.divide(getZeroScore(), dividend, divisor);
  }

  @Override
  public Class<?> getNumericType() {
    return double.class;
  }
}
