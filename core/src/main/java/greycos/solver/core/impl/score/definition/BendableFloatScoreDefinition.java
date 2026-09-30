package greycos.solver.core.impl.score.definition;

import java.util.Arrays;

import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;

/** Score definition for finite float levels. */
public class BendableFloatScoreDefinition
    extends AbstractBendableScoreDefinition<BendableFloatScore> {

  public BendableFloatScoreDefinition(int hardLevelsSize, int softLevelsSize) {
    super(hardLevelsSize, softLevelsSize);
    BendableFloatScore.zero(hardLevelsSize, softLevelsSize);
  }

  @Override
  public Class<BendableFloatScore> getScoreClass() {
    return BendableFloatScore.class;
  }

  @Override
  public BendableFloatScore getStructurallyFlawedScore() {
    return FloatingScoreSupport.withStructuralScore(getZeroScore(), -1L);
  }

  @Override
  public BendableFloatScore getStructurallyFlawedScore(BendableFloatScore score) {
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return FloatingScoreSupport.withStructuralScore(score, -1L);
  }

  @Override
  public BendableFloatScore getZeroScore() {
    return BendableFloatScore.zero(hardLevelsSize, softLevelsSize);
  }

  @Override
  public BendableFloatScore getOneSoftestScore() {
    return softLevelsSize > 0
        ? BendableFloatScore.ofSoft(hardLevelsSize, softLevelsSize, softLevelsSize - 1, 1.0f)
        : BendableFloatScore.ofHard(hardLevelsSize, softLevelsSize, hardLevelsSize - 1, 1.0f);
  }

  @Override
  public BendableFloatScore parseScore(String scoreString) {
    var score = BendableFloatScore.parseScore(scoreString);
    FloatingScoreSupport.validateCompatible(getZeroScore(), score);
    return score;
  }

  @Override
  public BendableFloatScore fromLevelNumbers(Number[] levelNumbers) {
    if (levelNumbers.length != getLevelsSize()) {
      throw new IllegalArgumentException(
          "The levelNumbers ("
              + Arrays.toString(levelNumbers)
              + ") must contain "
              + getLevelsSize()
              + " levels for BendableFloatScore.");
    }
    return FloatingScoreSupport.fromLevels(getZeroScore(), levelNumbers);
  }

  public BendableFloatScore createScore(float... scores) {
    if (scores.length != getLevelsSize()) {
      throw new IllegalArgumentException(
          "The scores ("
              + Arrays.toString(scores)
              + ") must contain "
              + getLevelsSize()
              + " levels.");
    }
    return BendableFloatScore.of(
        Arrays.copyOfRange(scores, 0, hardLevelsSize),
        Arrays.copyOfRange(scores, hardLevelsSize, scores.length));
  }

  @Override
  public BendableFloatScore buildOptimisticBound(
      InitializingScoreTrend initializingScoreTrend, BendableFloatScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, true);
  }

  @Override
  public BendableFloatScore buildPessimisticBound(
      InitializingScoreTrend initializingScoreTrend, BendableFloatScore score) {
    return FloatingScoreDefinitionSupport.bound(
        getZeroScore(), score, initializingScoreTrend, false);
  }

  @Override
  public BendableFloatScore divideBySanitizedDivisor(
      BendableFloatScore dividend, BendableFloatScore divisor) {
    return FloatingScoreDefinitionSupport.divide(getZeroScore(), dividend, divisor);
  }

  @Override
  public Class<?> getNumericType() {
    return float.class;
  }
}
