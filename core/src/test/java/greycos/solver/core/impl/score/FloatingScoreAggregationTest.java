package greycos.solver.core.impl.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.impl.score.constraint.ConstraintMatchTotal;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FloatingScoreAggregationTest {

  static Stream<Score<?>> scores() {
    return Stream.of(
        SimpleFloatScore.of(0x1p24f),
        HardSoftFloatScore.of(0x1p24f, 0x1p24f),
        HardMediumSoftFloatScore.of(0x1p24f, 0x1p24f, 0x1p24f),
        BendableFloatScore.of(new float[] {0x1p24f}, new float[] {0x1p24f}),
        SimpleDoubleScore.of(0x1p53),
        HardSoftDoubleScore.of(0x1p53, 0x1p53),
        HardMediumSoftDoubleScore.of(0x1p53, 0x1p53, 0x1p53),
        BendableDoubleScore.of(new double[] {0x1p53}, new double[] {0x1p53}));
  }

  @ParameterizedTest
  @MethodSource("scores")
  <Score_ extends Score<Score_>> void accumulationAndCancellationRetainSmallContributions(
      Score_ large) {
    var total = new ConstraintMatchTotal<>(ConstraintRef.of("exact"), large.zero());
    var one = large.divide(large.toLevelNumbers()[0].doubleValue());
    var bigMatch = total.addConstraintMatch(List.of("large"), large);
    var first = total.addConstraintMatch(List.of("first"), one);
    var second = total.addConstraintMatch(List.of("second"), one);
    for (var level : total.getScore().toLevelNumbers()) {
      assertThat(level.doubleValue()).isEqualTo(large.toLevelNumbers()[0].doubleValue() + 2);
    }
    total.removeConstraintMatch(bigMatch);
    for (var level : total.getScore().toLevelNumbers()) {
      assertThat(level.doubleValue()).isEqualTo(2);
    }
    total.removeConstraintMatch(second);
    total.removeConstraintMatch(first);
    assertThat(total.getScore()).isEqualTo(large.zero());
  }

  @Test
  void extractionOverflowDoesNotLoseRetainedMatches() {
    var total = new ConstraintMatchTotal<>(ConstraintRef.of("overflow"), SimpleDoubleScore.ZERO);
    total.addConstraintMatch(List.of("a"), SimpleDoubleScore.of(Double.MAX_VALUE));
    var second = total.addConstraintMatch(List.of("b"), SimpleDoubleScore.of(Double.MAX_VALUE));
    assertThatThrownBy(total::getScore).isInstanceOf(ArithmeticException.class);
    total.removeConstraintMatch(second);
    assertThat(total.getScore()).isEqualTo(SimpleDoubleScore.of(Double.MAX_VALUE));
  }

  @Test
  void groupingByJustificationRoundsOnce() {
    var total = new ConstraintMatchTotal<>(ConstraintRef.of("group"), SimpleDoubleScore.ONE);
    var justification = new Justification();
    total.addConstraintMatch(justification, SimpleDoubleScore.of(0x1p53));
    total.addConstraintMatch(justification, SimpleDoubleScore.ONE);
    total.addConstraintMatch(justification, SimpleDoubleScore.ONE);
    var analysis =
        InnerScoreDirector.getConstraintAnalysis(total, ScoreAnalysisFetchPolicy.FETCH_ALL);
    assertThat(analysis.matches()).hasSize(1);
    assertThat(analysis.matches().getFirst().score()).isEqualTo(SimpleDoubleScore.of(0x1p53 + 2));
    assertThat(analysis.score()).isEqualTo(SimpleDoubleScore.of(0x1p53 + 2));
  }

  private record Justification() implements ConstraintJustification {}
}
