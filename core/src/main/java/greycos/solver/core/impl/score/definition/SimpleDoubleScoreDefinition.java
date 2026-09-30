package greycos.solver.core.impl.score.definition;

import java.util.Arrays;

import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;

/** Score definition for finite double levels. */
public class SimpleDoubleScoreDefinition extends AbstractScoreDefinition<SimpleDoubleScore> {

  public SimpleDoubleScoreDefinition() {
    super(new String[] {"score"});
  }

  @Override
  public int getFeasibleLevelsSize() {
    return 0;
  }

  @Override
  public Class<SimpleDoubleScore> getScoreClass() {
    return SimpleDoubleScore.class;
  }

  @Override
  public SimpleDoubleScore getStructurallyFlawedScore() {
    return FloatingScoreSupport.withStructuralScore(getZeroScore(), -1L);
  }

  @Override
  public SimpleDoubleScore getStructurallyFlawedScore(SimpleDoubleScore score) {
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return FloatingScoreSupport.withStructuralScore(score, -1L);
  }

  @Override
  public SimpleDoubleScore getZeroScore() {
    return SimpleDoubleScore.ZERO;
  }

  @Override
  public SimpleDoubleScore getOneSoftestScore() {
    return SimpleDoubleScore.ONE;
  }

  @Override
  public SimpleDoubleScore parseScore(String scoreString) {
    var score = SimpleDoubleScore.parseScore(scoreString);
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return score;
  }

  @Override
  public SimpleDoubleScore fromLevelNumbers(Number[] levelNumbers) {
    if (levelNumbers.length != getLevelsSize()) {
      throw new IllegalArgumentException(
          "The levelNumbers ("
              + Arrays.toString(levelNumbers)
              + ") must contain "
              + getLevelsSize()
              + " levels for SimpleDoubleScore.");
    }
    return FloatingScoreSupport.fromLevels(getZeroScore(), levelNumbers);
  }

  @Override
  public SimpleDoubleScore buildOptimisticBound(
      InitializingScoreTrend initializingScoreTrend, SimpleDoubleScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, true);
  }

  @Override
  public SimpleDoubleScore buildPessimisticBound(
      InitializingScoreTrend initializingScoreTrend, SimpleDoubleScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, false);
  }

  @Override
  public SimpleDoubleScore divideBySanitizedDivisor(
      SimpleDoubleScore dividend, SimpleDoubleScore divisor) {
    return FloatingScoreDefinitionSupport.divide(getZeroScore(), dividend, divisor);
  }

  @Override
  public Class<?> getNumericType() {
    return double.class;
  }
}
