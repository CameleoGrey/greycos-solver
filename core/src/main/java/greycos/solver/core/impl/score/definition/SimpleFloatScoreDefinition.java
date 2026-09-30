package greycos.solver.core.impl.score.definition;

import java.util.Arrays;

import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;

/** Score definition for finite float levels. */
public class SimpleFloatScoreDefinition extends AbstractScoreDefinition<SimpleFloatScore> {

  public SimpleFloatScoreDefinition() {
    super(new String[] {"score"});
  }

  @Override
  public int getFeasibleLevelsSize() {
    return 0;
  }

  @Override
  public Class<SimpleFloatScore> getScoreClass() {
    return SimpleFloatScore.class;
  }

  @Override
  public SimpleFloatScore getStructurallyFlawedScore() {
    return FloatingScoreSupport.withStructuralScore(getZeroScore(), -1L);
  }

  @Override
  public SimpleFloatScore getStructurallyFlawedScore(SimpleFloatScore score) {
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return FloatingScoreSupport.withStructuralScore(score, -1L);
  }

  @Override
  public SimpleFloatScore getZeroScore() {
    return SimpleFloatScore.ZERO;
  }

  @Override
  public SimpleFloatScore getOneSoftestScore() {
    return SimpleFloatScore.ONE;
  }

  @Override
  public SimpleFloatScore parseScore(String scoreString) {
    var score = SimpleFloatScore.parseScore(scoreString);
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return score;
  }

  @Override
  public SimpleFloatScore fromLevelNumbers(Number[] levelNumbers) {
    if (levelNumbers.length != getLevelsSize()) {
      throw new IllegalArgumentException(
          "The levelNumbers ("
              + Arrays.toString(levelNumbers)
              + ") must contain "
              + getLevelsSize()
              + " levels for SimpleFloatScore.");
    }
    return FloatingScoreSupport.fromLevels(getZeroScore(), levelNumbers);
  }

  @Override
  public SimpleFloatScore buildOptimisticBound(
      InitializingScoreTrend initializingScoreTrend, SimpleFloatScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, true);
  }

  @Override
  public SimpleFloatScore buildPessimisticBound(
      InitializingScoreTrend initializingScoreTrend, SimpleFloatScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, false);
  }

  @Override
  public SimpleFloatScore divideBySanitizedDivisor(
      SimpleFloatScore dividend, SimpleFloatScore divisor) {
    return FloatingScoreDefinitionSupport.divide(getZeroScore(), dividend, divisor);
  }

  @Override
  public Class<?> getNumericType() {
    return float.class;
  }
}
