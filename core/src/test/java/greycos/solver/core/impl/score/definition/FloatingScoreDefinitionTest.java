package greycos.solver.core.impl.score.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import java.util.List;

import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;

import org.junit.jupiter.api.Test;

class FloatingScoreDefinitionTest {

  @Test
  void allDefinitionsRoundTripAndProvideFiniteBounds() {
    List<ScoreDefinition<?>> definitions =
        List.of(
            new SimpleFloatScoreDefinition(),
            new SimpleDoubleScoreDefinition(),
            new HardSoftFloatScoreDefinition(),
            new HardSoftDoubleScoreDefinition(),
            new HardMediumSoftFloatScoreDefinition(),
            new HardMediumSoftDoubleScoreDefinition(),
            new BendableFloatScoreDefinition(2, 1),
            new BendableDoubleScoreDefinition(1, 2));
    for (var definition : definitions) {
      checkDefinition(definition);
    }
  }

  private static <Score_ extends Score<Score_>> void checkDefinition(
      ScoreDefinition<Score_> definition) {
    assertThat(definition.getZeroScore().getClass()).isEqualTo(definition.getScoreClass());
    assertThat(definition.getZeroScore().isZero()).isTrue();
    assertThat(definition.getLevelLabels()).hasSize(definition.getLevelsSize());
    assertThat(definition.getOneSoftestScore().compareTo(definition.getZeroScore())).isPositive();
    var levels = new Number[definition.getLevelsSize()];
    Arrays.fill(levels, -1.25);
    var score = definition.fromLevelNumbers(levels);
    assertThat(definition.parseScore(score.toString())).isEqualTo(score);
    assertThat(definition.fromLevelNumbers(score.toLevelNumbers())).isEqualTo(score);
    assertThat(definition.divideBySanitizedDivisor(score, definition.getZeroScore()))
        .isEqualTo(score);
    var any =
        InitializingScoreTrend.buildUniformTrend(InitializingScoreTrendLevel.ANY, levels.length);
    var optimistic = definition.buildOptimisticBound(any, score);
    var pessimistic = definition.buildPessimisticBound(any, score);
    double maximum =
        definition.getNumericType() == float.class ? Float.MAX_VALUE : Double.MAX_VALUE;
    for (int i = 0; i < levels.length; i++) {
      assertThat(optimistic.toLevelNumbers()[i].doubleValue()).isEqualTo(maximum);
      assertThat(pessimistic.toLevelNumbers()[i].doubleValue()).isEqualTo(-maximum);
    }
    assertThat(
            definition.buildOptimisticBound(
                InitializingScoreTrend.buildUniformTrend(
                    InitializingScoreTrendLevel.ONLY_DOWN, levels.length),
                score))
        .isEqualTo(score);
    assertThat(
            definition.buildPessimisticBound(
                InitializingScoreTrend.buildUniformTrend(
                    InitializingScoreTrendLevel.ONLY_UP, levels.length),
                score))
        .isEqualTo(score);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition.fromLevelNumbers(new Number[levels.length + 1]));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                definition.buildOptimisticBound(
                    InitializingScoreTrend.buildUniformTrend(
                        InitializingScoreTrendLevel.ANY, levels.length + 1),
                    score));
  }

  @Test
  void divisionPreservesFractionalValuesAndSanitizesZeroPerLevel() {
    var definition = new HardSoftFloatScoreDefinition();
    assertThat(
            definition.divideBySanitizedDivisor(
                HardSoftFloatScore.of(0.75f, -0.75f), HardSoftFloatScore.of(2.0f, 0.0f)))
        .isEqualTo(HardSoftFloatScore.of(0.375f, -0.75f));
  }

  @Test
  void bendableDefinitionsValidateDimensionsAndSupportHardOnlyScores() {
    var definition = new BendableDoubleScoreDefinition(2, 0);
    assertThat(definition.getOneSoftestScore()).isEqualTo(BendableDoubleScore.ofHard(2, 0, 1, 1.0));
    assertThat(definition.createScore(1.0, 2.0))
        .isEqualTo(BendableDoubleScore.of(new double[] {1.0, 2.0}, new double[0]));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition.parseScore("[1.0]hard/[2.0]soft"));
    assertThatIllegalArgumentException().isThrownBy(() -> definition.createScore(1.0));
    assertThatIllegalArgumentException().isThrownBy(() -> new BendableFloatScoreDefinition(0, 0));
  }
}
