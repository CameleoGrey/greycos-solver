package greycos.solver.greybay.api.objective;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.greybay.api.ComponentIdentity;

import org.junit.jupiter.api.Test;

class ExactScoreAdapterTest {
  @Test
  void customNumbersRequireARegisteredExactAdapter() {
    var baseline = panel(1, 3);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new BalancedQualityObjective<FractionScore>().calibrate(baseline))
        .withMessageContaining("ExactScoreAdapter");
    var adapter = new FractionAdapter("app-artifact-v1");
    var objective = new BalancedQualityObjective<>(adapter);
    assertThat(objective.scoreAdapter()).isSameAs(adapter);
    assertThat(objective.scoreAdapter().identity())
        .isEqualTo(new ComponentIdentity("test.fractions", "1", "app-artifact-v1"));
    assertThat(objective.scoreAdapter().levels(baseline.observations().getFirst().score()))
        .containsExactly(ExactRational.of(1, 3));
    var calibration = objective.calibrate(baseline);
    assertThat(calibration.anchors().get("p")).containsExactly(ExactRational.of(1, 3));
    assertThat(objective.evaluate(panel(2, 3), calibration).normalizedGains())
        .containsExactly(ExactRational.of(1, 3));
    var changedArtifact = new BalancedQualityObjective<>(new FractionAdapter("app-artifact-v2"));
    assertThat(changedArtifact.identity()).isNotEqualTo(objective.identity());
    assertThatIllegalArgumentException()
        .isThrownBy(() -> changedArtifact.evaluate(panel(2, 3), calibration));
  }

  @Test
  void customObjectivesDefaultToTheStandardExactReportingAdapter() {
    StudyObjective<SimpleScore> objective =
        new StudyObjective<>() {
          @Override
          public ComponentIdentity identity() {
            return new ComponentIdentity("test.custom-objective", "1");
          }

          @Override
          public ObjectiveCalibration calibrate(CompletedPanel<SimpleScore> baseline) {
            throw new AssertionError("Reporting must use the exact score adapter directly.");
          }

          @Override
          public ObjectiveValue evaluate(
              CompletedPanel<SimpleScore> panel, ObjectiveCalibration calibration) {
            throw new AssertionError("Reporting must use the exact score adapter directly.");
          }

          @Override
          public int compare(ObjectiveValue left, ObjectiveValue right) {
            throw new AssertionError("Reporting must use the exact score adapter directly.");
          }

          @Override
          public ObjectiveCodec codec() {
            throw new AssertionError("Reporting must use the exact score adapter directly.");
          }
        };
    assertThat(objective.scoreAdapter().identity())
        .isEqualTo(ExactScoreAdapter.standard().identity());
    assertThat(objective.scoreAdapter().levels(SimpleScore.of(Long.MAX_VALUE)))
        .containsExactly(ExactRational.of(Long.MAX_VALUE));
  }

  private static CompletedPanel<FractionScore> panel(long numerator, long denominator) {
    return new CompletedPanel<>(
        List.of("p"),
        List.of(1L),
        List.of(
            new ScoredObservation<>(
                "p", 1, new FractionScore(new Fraction(numerator, denominator)))));
  }

  private record FractionAdapter(String artifact) implements ExactScoreAdapter<FractionScore> {
    @Override
    public ComponentIdentity identity() {
      return new ComponentIdentity("test.fractions", "1", artifact);
    }

    @Override
    public List<ExactRational> levels(FractionScore score) {
      return List.of(ExactRational.of(score.value.numerator, score.value.denominator));
    }
  }

  private static final class Fraction extends Number {
    private final long numerator;
    private final long denominator;

    private Fraction(long numerator, long denominator) {
      this.numerator = numerator;
      this.denominator = denominator;
    }

    @Override
    public int intValue() {
      throw new AssertionError("Lossy conversion must never be used.");
    }

    @Override
    public long longValue() {
      throw new AssertionError("Lossy conversion must never be used.");
    }

    @Override
    public float floatValue() {
      throw new AssertionError("Lossy conversion must never be used.");
    }

    @Override
    public double doubleValue() {
      throw new AssertionError("Lossy conversion must never be used.");
    }
  }

  private record FractionScore(Fraction value) implements Score<FractionScore> {
    @Override
    public Number[] toLevelNumbers() {
      return new Number[] {value};
    }

    @Override
    public boolean isFeasible() {
      return true;
    }

    @Override
    public String toShortString() {
      return value.numerator + "/" + value.denominator;
    }

    @Override
    public int compareTo(FractionScore other) {
      return ExactRational.of(value.numerator, value.denominator)
          .compareTo(ExactRational.of(other.value.numerator, other.value.denominator));
    }

    @Override
    public FractionScore zero() {
      throw new AssertionError("Score arithmetic must never be used.");
    }

    @Override
    public FractionScore add(FractionScore addend) {
      throw new AssertionError("Score arithmetic must never be used.");
    }

    @Override
    public FractionScore subtract(FractionScore subtrahend) {
      throw new AssertionError("Score arithmetic must never be used.");
    }

    @Override
    public FractionScore multiply(double multiplicand) {
      throw new AssertionError("Score arithmetic must never be used.");
    }

    @Override
    public FractionScore divide(double divisor) {
      throw new AssertionError("Score arithmetic must never be used.");
    }

    @Override
    public FractionScore power(double exponent) {
      throw new AssertionError("Score arithmetic must never be used.");
    }

    @Override
    public FractionScore abs() {
      throw new AssertionError("Score arithmetic must never be used.");
    }
  }
}
