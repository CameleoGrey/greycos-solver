package greycos.solver.core.impl.score.definition;

import java.util.Arrays;

import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;

/** Score definition for finite double levels. */
public class BendableDoubleScoreDefinition
    extends AbstractBendableScoreDefinition<BendableDoubleScore> {

  public BendableDoubleScoreDefinition(int hardLevelsSize, int softLevelsSize) {
    super(hardLevelsSize, softLevelsSize);
    BendableDoubleScore.zero(hardLevelsSize, softLevelsSize);
  }

  @Override
  public Class<BendableDoubleScore> getScoreClass() {
    return BendableDoubleScore.class;
  }

  @Override
  public BendableDoubleScore getStructurallyFlawedScore() {
    return FloatingScoreSupport.withStructuralScore(getZeroScore(), -1L);
  }

  @Override
  public BendableDoubleScore getStructurallyFlawedScore(BendableDoubleScore score) {
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return FloatingScoreSupport.withStructuralScore(score, -1L);
  }

  @Override
  public BendableDoubleScore getZeroScore() {
    return BendableDoubleScore.zero(hardLevelsSize, softLevelsSize);
  }

  @Override
  public BendableDoubleScore getOneSoftestScore() {
    return softLevelsSize > 0
        ? BendableDoubleScore.ofSoft(hardLevelsSize, softLevelsSize, softLevelsSize - 1, 1.0)
        : BendableDoubleScore.ofHard(hardLevelsSize, softLevelsSize, hardLevelsSize - 1, 1.0);
  }

  @Override
  public BendableDoubleScore parseScore(String scoreString) {
    var score = BendableDoubleScore.parseScore(scoreString);
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return score;
  }

  @Override
  public BendableDoubleScore fromLevelNumbers(Number[] levelNumbers) {
    if (levelNumbers.length != getLevelsSize()) {
      throw new IllegalArgumentException(
          "The levelNumbers ("
              + Arrays.toString(levelNumbers)
              + ") must contain "
              + getLevelsSize()
              + " levels for BendableDoubleScore.");
    }
    return FloatingScoreSupport.fromLevels(getZeroScore(), levelNumbers);
  }

  public BendableDoubleScore createScore(double... scores) {
    if (scores.length != getLevelsSize()) {
      throw new IllegalArgumentException(
          "The scores ("
              + Arrays.toString(scores)
              + ") must contain "
              + getLevelsSize()
              + " levels.");
    }
    return BendableDoubleScore.of(
        Arrays.copyOfRange(scores, 0, hardLevelsSize),
        Arrays.copyOfRange(scores, hardLevelsSize, scores.length));
  }

  @Override
  public BendableDoubleScore buildOptimisticBound(
      InitializingScoreTrend initializingScoreTrend, BendableDoubleScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, true);
  }

  @Override
  public BendableDoubleScore buildPessimisticBound(
      InitializingScoreTrend initializingScoreTrend, BendableDoubleScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, false);
  }

  @Override
  public BendableDoubleScore divideBySanitizedDivisor(
      BendableDoubleScore dividend, BendableDoubleScore divisor) {
    return FloatingScoreDefinitionSupport.divide(getZeroScore(), dividend, divisor);
  }

  @Override
  public Class<?> getNumericType() {
    return double.class;
  }
}
