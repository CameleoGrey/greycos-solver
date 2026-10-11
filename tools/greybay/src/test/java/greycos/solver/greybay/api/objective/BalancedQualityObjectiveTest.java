package greycos.solver.greybay.api.objective;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleScore;

import org.junit.jupiter.api.Test;

class BalancedQualityObjectiveTest {
  @Test
  void workedExampleBalancesInstancesInsteadOfRawScoreMagnitude() {
    var objective = new BalancedQualityObjective<SimpleScore>();
    var baseline = simplePanel(new long[][] {{-100}, {-10000}});
    var calibration = objective.calibrate(baseline);
    var first = objective.evaluate(simplePanel(new long[][] {{-80}, {-9900}}), calibration);
    var second = objective.evaluate(simplePanel(new long[][] {{-99}, {-9000}}), calibration);
    assertThat(first.normalizedGains()).containsExactly(ExactRational.of(21, 200));
    assertThat(second.normalizedGains()).containsExactly(ExactRational.of(11, 200));
    assertThat(objective.compare(first, second)).isPositive();
    assertThat(objective.evaluate(baseline, calibration).normalizedGains())
        .containsExactly(ExactRational.ZERO);
  }

  @Test
  void anchorsAreSeedMeansBeforeTakingAbsoluteScaleAndCrossingZeroIsValid() {
    var objective = new BalancedQualityObjective<SimpleScore>();
    var calibration = objective.calibrate(simplePanel(new long[][] {{-3, 5}, {-3, 3}}));
    assertThat(calibration.anchors().get("p0")).containsExactly(ExactRational.ONE);
    assertThat(calibration.anchors().get("p1")).containsExactly(ExactRational.ZERO);
    assertThat(calibration.scales().get("p0")).containsExactly(ExactRational.ONE);
    assertThat(calibration.scales().get("p1")).containsExactly(ExactRational.ONE);
    var value = objective.evaluate(simplePanel(new long[][] {{5, 7}, {2, 4}}), calibration);
    assertThat(value.normalizedGains()).containsExactly(ExactRational.of(4));
  }

  @Test
  void seedMeansAndLargeIntegralScoresRemainExact() {
    var objective = new BalancedQualityObjective<SimpleScore>();
    var baseline = simplePanel(new long[][] {{Long.MAX_VALUE, Long.MAX_VALUE - 1}});
    var calibration = objective.calibrate(baseline);
    var expected =
        new ExactRational(
            BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).subtract(BigInteger.ONE),
            BigInteger.TWO);
    assertThat(calibration.anchors().get("p0")).containsExactly(expected);
    assertThat(objective.evaluate(baseline, calibration).normalizedGains())
        .containsExactly(ExactRational.ZERO);
  }

  @Test
  void feasibilityPrecedesScoreLevelsAndLevelsCompareLexicographically() {
    var objective = new BalancedQualityObjective<HardSoftScore>();
    var baseline = panel(HardSoftScore.of(-10, -100), HardSoftScore.of(-10, -100));
    var calibration = objective.calibrate(baseline);
    var partial =
        objective.evaluate(
            panel(HardSoftScore.of(100, Long.MAX_VALUE), HardSoftScore.of(-1, Long.MAX_VALUE)),
            calibration);
    var feasible =
        objective.evaluate(
            panel(HardSoftScore.of(0, Long.MIN_VALUE), HardSoftScore.of(0, Long.MIN_VALUE)),
            calibration);
    assertThat(partial.feasibilityRate()).isEqualTo(ExactRational.of(1, 2));
    assertThat(feasible.fullyFeasible()).isTrue();
    assertThat(objective.compare(feasible, partial)).isPositive();
    var betterHard =
        objective.evaluate(
            panel(HardSoftScore.of(-1, Long.MIN_VALUE), HardSoftScore.of(-1, Long.MIN_VALUE)),
            calibration);
    var betterSoft =
        objective.evaluate(
            panel(HardSoftScore.of(-2, Long.MAX_VALUE), HardSoftScore.of(-2, Long.MAX_VALUE)),
            calibration);
    assertThat(objective.compare(betterHard, betterSoft)).isPositive();
  }

  @Test
  void nativeFeasibilityIncludesEveryBendableHardLevelAndLayoutCannotChange() {
    var objective = new BalancedQualityObjective<BendableScore>();
    var baseline = panel(BendableScore.of(new long[] {0, 0}, new long[] {-100}));
    var calibration = objective.calibrate(baseline);
    var candidate = panel(BendableScore.of(new long[] {100, -1}, new long[] {0}));
    assertThat(objective.evaluate(candidate, calibration).feasibilityRate())
        .isEqualTo(ExactRational.ZERO);
    var otherLayout = panel(BendableScore.of(new long[] {0}, new long[] {0, -100}));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> objective.evaluate(otherLayout, calibration));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                panel(
                    BendableScore.of(new long[] {0}, new long[] {0, -100}),
                    BendableScore.of(new long[] {0, 0}, new long[] {-100})));
  }

  @Test
  void genuineTiesAndHistoricalValuesDoNotDependOnOtherCandidates() {
    var objective = new BalancedQualityObjective<SimpleScore>();
    var calibration = objective.calibrate(simplePanel(new long[][] {{-10, -10}}));
    var first = objective.evaluate(simplePanel(new long[][] {{-8, -12}}), calibration);
    var second = objective.evaluate(simplePanel(new long[][] {{-9, -11}}), calibration);
    assertThat(objective.compare(first, second)).isZero();
    var encoded = objective.codec().encodeValue(first);
    objective.evaluate(simplePanel(new long[][] {{Long.MAX_VALUE, Long.MIN_VALUE}}), calibration);
    assertThat(objective.codec().encodeValue(first)).isEqualTo(encoded);
  }

  @Test
  void floatingPointDifferencesBelowDisplayPrecisionRemainOrdered() {
    var objective = new BalancedQualityObjective<SimpleDoubleScore>();
    var calibration = objective.calibrate(panel(SimpleDoubleScore.of(0.0)));
    var lower = objective.evaluate(panel(SimpleDoubleScore.of(0.1)), calibration);
    var upper = objective.evaluate(panel(SimpleDoubleScore.of(Math.nextUp(0.1))), calibration);
    assertThat(objective.compare(upper, lower)).isPositive();
    assertThat(lower.normalizedGains()).containsExactly(ExactRational.fromNumber(0.1));
  }

  @Test
  void explicitScalesAreValidatedImmutableAndPartOfIdentity() {
    var scales = new ArrayList<>(List.of(ExactRational.of(10)));
    var objective = new BalancedQualityObjective<SimpleScore>(Map.of("p0", scales));
    scales.set(0, ExactRational.of(100));
    var calibration = objective.calibrate(simplePanel(new long[][] {{0}}));
    assertThat(objective.evaluate(simplePanel(new long[][] {{5}}), calibration).normalizedGains())
        .containsExactly(ExactRational.of(1, 2));
    assertThat(objective.identity())
        .isNotEqualTo(new BalancedQualityObjective<SimpleScore>().identity());
    assertThat(objective.identity())
        .isEqualTo(
            new BalancedQualityObjective<SimpleScore>(Map.of("p0", List.of(ExactRational.of(10))))
                .identity());
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new BalancedQualityObjective<SimpleScore>(
                    Map.of("p0", List.of(ExactRational.ZERO))));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new BalancedQualityObjective<SimpleScore>(
                        Map.of("unknown", List.of(ExactRational.ONE)))
                    .validateProblemIds(List.of("p0", "held-out")));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new BalancedQualityObjective<SimpleScore>(
                        Map.of("p0", List.of(ExactRational.ONE, ExactRational.ONE)))
                    .calibrate(simplePanel(new long[][] {{0}})));
    assertThatThrownBy(() -> calibration.scales().get("p0").set(0, ExactRational.ONE))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void trainingOverridesAllowHeldOutCalibrationAndBothPanelsMayOverride() {
    var training =
        new CompletedPanel<>(
            List.of("training"),
            List.of(1L),
            List.of(new ScoredObservation<>("training", 1, SimpleScore.of(-100))));
    var heldOut =
        new CompletedPanel<>(
            List.of("held-out"),
            List.of(2L),
            List.of(new ScoredObservation<>("held-out", 2, SimpleScore.of(-10000))));
    var trainingOnly =
        new BalancedQualityObjective<SimpleScore>(
            Map.of("training", List.of(ExactRational.of(10))));
    trainingOnly.validateProblemIds(List.of("training", "held-out"));
    assertThat(trainingOnly.calibrate(training).scales().get("training"))
        .containsExactly(ExactRational.of(10));
    assertThat(trainingOnly.calibrate(heldOut).scales().get("held-out"))
        .containsExactly(ExactRational.of(10000));
    var bothPanels =
        new BalancedQualityObjective<SimpleScore>(
            Map.of(
                "training",
                List.of(ExactRational.of(10)),
                "held-out",
                List.of(ExactRational.of(20))));
    bothPanels.validateProblemIds(List.of("training", "held-out"));
    assertThat(bothPanels.calibrate(training).scales().get("training"))
        .containsExactly(ExactRational.of(10));
    assertThat(bothPanels.calibrate(heldOut).scales().get("held-out"))
        .containsExactly(ExactRational.of(20));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> bothPanels.validateProblemIds(List.of("training")))
        .withMessageContaining("held-out");
  }

  @Test
  void panelsRejectDuplicatesMissingCellsAndChangedSeeds() {
    var observation = new ScoredObservation<>("p", 1, SimpleScore.of(0));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new CompletedPanel<>(List.of("p"), List.of(1L, 2L), List.of(observation)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new CompletedPanel<>(
                    List.of("p"), List.of(1L, 2L), List.of(observation, observation)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CompletedPanel<>(List.of("p"), List.of(2L), List.of(observation)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ScoredObservation<>("p", 1, new SimpleScore(-1, 0)));
    var objective = new BalancedQualityObjective<SimpleScore>();
    var original = new CompletedPanel<>(List.of("p"), List.of(1L), List.of(observation));
    var changed =
        new CompletedPanel<>(
            List.of("p"), List.of(2L), List.of(new ScoredObservation<>("p", 2, SimpleScore.of(0))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> objective.evaluate(changed, objective.calibrate(original)));
  }

  @Test
  void bendableObservationsAndPanelCollectionsAreDefensivelyCopied() {
    var hard = new long[] {0};
    var soft = new long[] {-100};
    var observation = new ScoredObservation<>("p", 1, BendableScore.of(hard, soft));
    hard[0] = -1;
    soft[0] = 5;
    observation.score().softScores()[0] = 100;
    assertThat(observation.score()).isEqualTo(BendableScore.of(new long[] {0}, new long[] {-100}));
    var observations = new ArrayList<>(List.of(observation));
    var panel = new CompletedPanel<>(List.of("p"), List.of(1L), observations);
    observations.clear();
    assertThat(panel.observations()).hasSize(1);
  }

  @SafeVarargs
  private static <Score_ extends Score<Score_>> CompletedPanel<Score_> panel(Score_... scores) {
    var seeds = new ArrayList<Long>();
    var observations = new ArrayList<ScoredObservation<Score_>>();
    for (int i = 0; i < scores.length; i++) {
      seeds.add((long) i);
      observations.add(new ScoredObservation<>("p", i, scores[i]));
    }
    return new CompletedPanel<>(List.of("p"), seeds, observations);
  }

  private static CompletedPanel<SimpleScore> simplePanel(long[][] values) {
    var problemIds = new ArrayList<String>();
    var seeds = new ArrayList<Long>();
    var observations = new ArrayList<ScoredObservation<SimpleScore>>();
    for (int seed = 0; seed < values[0].length; seed++) {
      seeds.add((long) seed);
    }
    for (int instance = 0; instance < values.length; instance++) {
      var problemId = "p" + instance;
      problemIds.add(problemId);
      for (int seed = 0; seed < values[instance].length; seed++) {
        observations.add(
            new ScoredObservation<>(problemId, seed, SimpleScore.of(values[instance][seed])));
      }
    }
    return new CompletedPanel<>(problemIds, seeds, observations);
  }
}
