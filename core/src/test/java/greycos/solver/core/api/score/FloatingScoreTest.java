package greycos.solver.core.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import greycos.solver.core.impl.score.definition.BendableDoubleScoreDefinition;
import greycos.solver.core.impl.score.definition.BendableFloatScoreDefinition;
import greycos.solver.core.impl.score.definition.HardMediumSoftDoubleScoreDefinition;
import greycos.solver.core.impl.score.definition.HardMediumSoftFloatScoreDefinition;
import greycos.solver.core.impl.score.definition.HardSoftDoubleScoreDefinition;
import greycos.solver.core.impl.score.definition.HardSoftFloatScoreDefinition;
import greycos.solver.core.impl.score.definition.ScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleDoubleScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleFloatScoreDefinition;

import org.junit.jupiter.api.Test;

class FloatingScoreTest {

  @Test
  void allFamiliesPreserveValuesOrderingAndStructuralState() {
    for (var definition : definitions()) {
      checkFamily(definition);
    }
  }

  private static <Score_ extends Score<Score_>> void checkFamily(
      ScoreDefinition<Score_> definition) {
    var levels = new Number[definition.getLevelsSize()];
    for (int i = 0; i < levels.length; i++) {
      levels[i] = -0.125 * (i + 1);
    }
    var score = definition.fromLevelNumbers(levels);
    assertThat(score.toLevelNumbers()).hasSize(levels.length);
    for (int i = 0; i < levels.length; i++) {
      assertThat(score.toLevelNumbers()[i].doubleValue()).isEqualTo(levels[i].doubleValue());
      assertThat(score.toLevelNumbers()[i].getClass())
          .isEqualTo(definition.getNumericType() == float.class ? Float.class : Double.class);
    }
    assertThat(definition.parseScore(score.toString())).isEqualTo(score).hasSameHashCodeAs(score);
    assertThat(score.compareTo(definition.getZeroScore())).isNegative();
    assertThat(score.isFeasible()).isEqualTo(definition.getFeasibleLevelsSize() == 0);
    var structural = definition.getStructurallyFlawedScore(score);
    assertThat(definition.parseScore(structural.toString()))
        .isEqualTo(structural)
        .hasSameHashCodeAs(structural);
    assertThat(structural.structuralScore()).isEqualTo(-1L);
    assertThat(structural.isFeasible()).isFalse();
    assertThat(structural.compareTo(score)).isNegative();
    assertThat(score.add(score.negate())).isEqualTo(definition.getZeroScore());
    assertThat(score.subtract(score)).isEqualTo(definition.getZeroScore());
    assertThat(score.multiply(2.0).divide(2.0)).isEqualTo(score);
    assertThat(score.abs()).isEqualTo(score.negate());
    assertThat(score.power(2.0).toLevelNumbers()[0].doubleValue()).isEqualTo(0.015625);
    if (levels.length > 1) {
      var higher = levels.clone();
      higher[0] = 0.0;
      higher[higher.length - 1] = -1_000_000.0;
      assertThat(definition.fromLevelNumbers(higher).compareTo(score)).isPositive();
    }
  }

  @Test
  void zeroIsCanonicalForEqualityHashOrderingAndSerialization() {
    assertThat(SimpleFloatScore.of(-0.0f))
        .isEqualTo(SimpleFloatScore.ZERO)
        .hasSameHashCodeAs(SimpleFloatScore.ZERO);
    assertThat(SimpleDoubleScore.of(-0.0))
        .isEqualTo(SimpleDoubleScore.ZERO)
        .hasSameHashCodeAs(SimpleDoubleScore.ZERO);
    assertThat(SimpleFloatScore.of(-0.0f).compareTo(SimpleFloatScore.ZERO)).isZero();
    assertThat(SimpleDoubleScore.of(-0.0).compareTo(SimpleDoubleScore.ZERO)).isZero();
    assertThat(SimpleFloatScore.parseScore("-0.0").toString()).isEqualTo("0.0");
    assertThat(HardMediumSoftDoubleScore.parseScore("-0.0hard/-0.0medium/-0.0soft").toString())
        .isEqualTo("0.0hard/0.0medium/0.0soft");
    assertThat(SimpleFloatScore.ZERO.toShortString()).isEqualTo("0");
    assertThat(HardSoftDoubleScore.ofSoft(0.25).toShortString()).isEqualTo("0.25soft");
  }

  @Test
  void bendableArraysRemainImmutable() {
    var floatHard = new float[] {-0.0f, 2.0f};
    var floatSoft = new float[] {3.0f};
    var floatScore = BendableFloatScore.of(floatHard, floatSoft);
    floatHard[0] = 9.0f;
    floatSoft[0] = 9.0f;
    floatScore.hardScores()[0] = 8.0f;
    floatScore.softScores()[0] = 8.0f;
    assertThat(floatScore)
        .isEqualTo(BendableFloatScore.of(new float[] {0.0f, 2.0f}, new float[] {3.0f}));
    assertThat(Float.floatToRawIntBits(floatScore.hardScore(0))).isZero();
    var doubleHard = new double[] {1.0};
    var doubleSoft = new double[] {-0.0, 3.0};
    var doubleScore = BendableDoubleScore.of(doubleHard, doubleSoft);
    doubleHard[0] = 9.0;
    doubleSoft[1] = 9.0;
    doubleScore.hardScores()[0] = 8.0;
    doubleScore.softScores()[1] = 8.0;
    assertThat(doubleScore)
        .isEqualTo(BendableDoubleScore.of(new double[] {1.0}, new double[] {0.0, 3.0}));
    assertThat(Double.doubleToRawLongBits(doubleScore.softScore(0))).isZero();
  }

  @Test
  void incompatibleBendableDimensionsFailAcrossArithmeticAndComparison() {
    var left = BendableFloatScore.zero(1, 1);
    var right = BendableFloatScore.zero(2, 0);
    assertThatIllegalArgumentException().isThrownBy(() -> left.add(right));
    assertThatIllegalArgumentException().isThrownBy(() -> left.subtract(right));
    assertThatIllegalArgumentException().isThrownBy(() -> left.compareTo(right));
    assertThatIllegalArgumentException().isThrownBy(() -> BendableDoubleScore.zero(0, 0));
    assertThatIllegalArgumentException().isThrownBy(() -> BendableFloatScore.zero(-1, 1));
  }

  @Test
  void invalidValuesFailAcrossConstructorParserAndArithmetic() {
    assertThatIllegalArgumentException().isThrownBy(() -> new SimpleFloatScore(Float.NaN));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new HardSoftDoubleScore(0.0, Double.NEGATIVE_INFINITY));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new HardMediumSoftFloatScore(0.0f, Float.POSITIVE_INFINITY, 0.0f));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> BendableDoubleScore.of(new double[] {Double.NaN}, new double[0]));
    assertThatIllegalArgumentException().isThrownBy(() -> SimpleFloatScore.parseScore("1e1000"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> HardSoftDoubleScore.parseScore("NaNhard/0.0soft"));
    assertThatIllegalArgumentException().isThrownBy(() -> new SimpleDoubleScore(1L, 0.0));
    assertThatThrownBy(
            () -> SimpleFloatScore.of(Float.MAX_VALUE).add(SimpleFloatScore.of(Float.MAX_VALUE)))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(
            () ->
                SimpleDoubleScore.of(Double.MAX_VALUE)
                    .subtract(SimpleDoubleScore.of(-Double.MAX_VALUE)))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> SimpleFloatScore.ONE.divide(0.0))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void tinyScoresRemainDistinctAndFormatsRoundTripExtremes() {
    assertThat(SimpleFloatScore.of(Float.MIN_VALUE).compareTo(SimpleFloatScore.ZERO)).isPositive();
    assertThat(SimpleDoubleScore.of(Double.MIN_VALUE).compareTo(SimpleDoubleScore.ZERO))
        .isPositive();
    assertThat(SimpleDoubleScore.of(Math.nextUp(1.0)).compareTo(SimpleDoubleScore.ONE))
        .isPositive();
    assertThat(SimpleFloatScore.parseScore(SimpleFloatScore.of(Float.MIN_VALUE).toString()))
        .isEqualTo(SimpleFloatScore.of(Float.MIN_VALUE));
    assertThat(SimpleDoubleScore.parseScore(SimpleDoubleScore.of(-Double.MAX_VALUE).toString()))
        .isEqualTo(SimpleDoubleScore.of(-Double.MAX_VALUE));
    assertThat(SimpleFloatScore.parseScore("*")).isEqualTo(SimpleFloatScore.of(-Float.MAX_VALUE));
    assertThat(SimpleDoubleScore.parseScore("*"))
        .isEqualTo(SimpleDoubleScore.of(-Double.MAX_VALUE));
  }

  private static List<ScoreDefinition<?>> definitions() {
    return List.of(
        new SimpleFloatScoreDefinition(),
        new SimpleDoubleScoreDefinition(),
        new HardSoftFloatScoreDefinition(),
        new HardSoftDoubleScoreDefinition(),
        new HardMediumSoftFloatScoreDefinition(),
        new HardMediumSoftDoubleScoreDefinition(),
        new BendableFloatScoreDefinition(2, 1),
        new BendableDoubleScoreDefinition(1, 2));
  }
}
