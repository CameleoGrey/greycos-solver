package greycos.solver.greybay.api.objective;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.SimpleScore;

import org.junit.jupiter.api.Test;

class BalancedObjectiveCodecTest {
  @Test
  void valueGoldenEncodingAndRoundTripAreExact() {
    var codec = new BalancedQualityObjective<SimpleScore>().codec();
    var value =
        new BalancedObjectiveValue(
            ExactRational.of(2, 3),
            List.of(ExactRational.of(-5, 7), ExactRational.of(999999999999L, 2)));
    var golden = "greybay-balanced-value:1\n2/3\n2\n-5/7\n999999999999/2";
    assertThat(codec.encodeValue(value)).isEqualTo(golden);
    assertThat(codec.decodeValue(golden)).isEqualTo(value);
    assertThat(codec.encodeValue(codec.decodeValue(golden))).isEqualTo(golden);
  }

  @Test
  void calibrationPreservesIdentityUnicodePanelOrderAnchorsAndScales() {
    var objective =
        new BalancedQualityObjective<SimpleScore>(
            Map.of("β\nproblem", List.of(ExactRational.of(1, 7))));
    var panel =
        new CompletedPanel<>(
            List.of("β\nproblem", "a"),
            List.of(Long.MIN_VALUE, 3L),
            List.of(
                new ScoredObservation<>("a", 3, SimpleScore.of(4)),
                new ScoredObservation<>("β\nproblem", 3, SimpleScore.of(1)),
                new ScoredObservation<>("a", Long.MIN_VALUE, SimpleScore.of(-3)),
                new ScoredObservation<>("β\nproblem", Long.MIN_VALUE, SimpleScore.of(-2))));
    var original = objective.calibrate(panel);
    var encoded = objective.codec().encodeCalibration(original);
    var decoded = objective.codec().decodeCalibration(encoded);
    assertThat(decoded).isEqualTo(original);
    assertThat(objective.codec().encodeCalibration(decoded)).isEqualTo(encoded);
    assertThat(objective.evaluate(panel, decoded)).isEqualTo(objective.evaluate(panel, original));
    assertThat(original.problemIds()).containsExactly("β\nproblem", "a");
    assertThat(original.anchors().get("β\nproblem")).containsExactly(ExactRational.of(-1, 2));
    assertThat(original.anchors().get("a")).containsExactly(ExactRational.of(1, 2));
  }

  @Test
  void corruptUnsupportedAndNoncanonicalValuesAreRejected() {
    var codec = new BalancedQualityObjective<SimpleScore>().codec();
    for (var encoded :
        List.of(
            "greybay-balanced-value:2\n1/1\n1\n0/1",
            "greybay-balanced-value:1\n1/1\n1",
            "greybay-balanced-value:1\n2/1\n1\n0/1",
            "greybay-balanced-value:1\n1/1\n0",
            "greybay-balanced-value:1\n1/1\n2147483647\n0/1",
            "greybay-balanced-value:1\n1/1\n01\n0/1",
            "greybay-balanced-value:1\n1/1\n1\n0/2",
            "greybay-balanced-value:1\n1/1\n1\n0/1\n")) {
      assertThatIllegalArgumentException().isThrownBy(() -> codec.decodeValue(encoded));
    }
  }

  @Test
  void truncatedCalibrationAndNonpositiveScalesAreRejected() {
    var objective = new BalancedQualityObjective<SimpleScore>();
    var calibration =
        objective.calibrate(
            new CompletedPanel<>(
                List.of("p"),
                List.of(1L),
                List.of(new ScoredObservation<>("p", 1, SimpleScore.of(0)))));
    var encoded = objective.codec().encodeCalibration(calibration);
    var invalidScale = encoded.substring(0, encoded.lastIndexOf('\n') + 1) + "0/1";
    assertThatIllegalArgumentException()
        .isThrownBy(() -> objective.codec().decodeCalibration(invalidScale));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> objective.codec().decodeCalibration(encoded + "\n"));
    var fields = encoded.split("\n");
    for (int length = 1; length < fields.length; length++) {
      var truncated = String.join("\n", List.of(fields).subList(0, length));
      assertThatIllegalArgumentException()
          .isThrownBy(() -> objective.codec().decodeCalibration(truncated));
    }
  }
}
