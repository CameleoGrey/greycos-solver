package greycos.solver.core.impl.score.definition;

import java.util.Arrays;

import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;

/** Score definition for finite double levels. */
public class HardMediumSoftDoubleScoreDefinition
    extends AbstractScoreDefinition<HardMediumSoftDoubleScore> {

  public HardMediumSoftDoubleScoreDefinition() {
    super(new String[] {"hard score", "medium score", "soft score"});
  }

  @Override
  public int getFeasibleLevelsSize() {
    return 1;
  }

  @Override
  public Class<HardMediumSoftDoubleScore> getScoreClass() {
    return HardMediumSoftDoubleScore.class;
  }

  @Override
  public HardMediumSoftDoubleScore getStructurallyFlawedScore() {
    return FloatingScoreSupport.withStructuralScore(getZeroScore(), -1L);
  }

  @Override
  public HardMediumSoftDoubleScore getStructurallyFlawedScore(HardMediumSoftDoubleScore score) {
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return FloatingScoreSupport.withStructuralScore(score, -1L);
  }

  @Override
  public HardMediumSoftDoubleScore getZeroScore() {
    return HardMediumSoftDoubleScore.ZERO;
  }

  @Override
  public HardMediumSoftDoubleScore getOneSoftestScore() {
    return HardMediumSoftDoubleScore.ONE_SOFT;
  }

  @Override
  public HardMediumSoftDoubleScore parseScore(String scoreString) {
    var score = HardMediumSoftDoubleScore.parseScore(scoreString);
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return score;
  }

  @Override
  public HardMediumSoftDoubleScore fromLevelNumbers(Number[] levelNumbers) {
    if (levelNumbers.length != getLevelsSize()) {
      throw new IllegalArgumentException(
          "The levelNumbers ("
              + Arrays.toString(levelNumbers)
              + ") must contain "
              + getLevelsSize()
              + " levels for HardMediumSoftDoubleScore.");
    }
    return FloatingScoreSupport.fromLevels(getZeroScore(), levelNumbers);
  }

  @Override
  public HardMediumSoftDoubleScore buildOptimisticBound(
      InitializingScoreTrend initializingScoreTrend, HardMediumSoftDoubleScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, true);
  }

  @Override
  public HardMediumSoftDoubleScore buildPessimisticBound(
      InitializingScoreTrend initializingScoreTrend, HardMediumSoftDoubleScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, false);
  }

  @Override
  public HardMediumSoftDoubleScore divideBySanitizedDivisor(
      HardMediumSoftDoubleScore dividend, HardMediumSoftDoubleScore divisor) {
    return FloatingScoreDefinitionSupport.divide(getZeroScore(), dividend, divisor);
  }

  @Override
  public Class<?> getNumericType() {
    return double.class;
  }
}
