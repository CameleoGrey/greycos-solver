package greycos.solver.greybay.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.greybay.api.EvaluationRecord;
import greycos.solver.greybay.api.EvaluationStatus;
import greycos.solver.greybay.api.QualificationComparison;
import greycos.solver.greybay.api.QualificationResult;
import greycos.solver.greybay.api.QualificationStatus;
import greycos.solver.greybay.api.StudyResult;
import greycos.solver.greybay.api.StudyState;
import greycos.solver.greybay.api.TrainingStatus;
import greycos.solver.greybay.api.TrialResult;
import greycos.solver.greybay.api.TrialStatus;
import greycos.solver.greybay.api.VerificationResult;
import greycos.solver.greybay.api.objective.BalancedObjectiveValue;
import greycos.solver.greybay.api.objective.BalancedQualityObjective;
import greycos.solver.greybay.api.objective.ExactRational;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.config.RandomSamplerConfig;
import greycos.solver.greybay.config.TpeSamplerConfig;
import greycos.solver.greybay.examples.GreybayWorkflowExample;
import greycos.solver.greybay.examples.SchedulingExample;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CampaignHarnessTest {
  @TempDir Path temporary;

  @Test
  void freezesDeterministicallyBeforeAnyRunnerAndRetainsNegativeResultsAndCosts() throws Exception {
    var protocol = protocol(List.of(101L));
    var frozen =
        CampaignHarness.freeze(
            protocol, (method, seed) -> definition(protocol, method, seed, Duration.ofMillis(1)));
    var second =
        CampaignHarness.freeze(
            protocol, (method, seed) -> definition(protocol, method, seed, Duration.ofMillis(1)));
    assertThat(frozen.manifestBytes()).isEqualTo(second.manifestBytes());
    assertThat(frozen.fingerprint()).isEqualTo(second.fingerprint());
    var calls = new ArrayList<String>();
    var directory = temporary.resolve("controlled-fixture");
    var runs =
        frozen.execute(
            directory,
            (request, allocations, studyDirectory) -> {
              assertThat(directory.resolve("campaign-manifest.json")).exists();
              assertThat(allocations.searchAllocation()).isEqualTo(Duration.ofSeconds(1));
              assertThat(allocations.qualificationAllocation()).isEqualTo(Duration.ofSeconds(1));
              assertThat(request.definition().heldOutPanel())
                  .isEqualTo(
                      new ProblemPanel(
                          List.of("heldout-medium", "heldout-large"), List.of(31L, 37L)));
              calls.add(request.directoryName());
              return evidence(request.method(), false);
            });
    assertThat(calls).containsExactly("tpe-101", "random-101");
    assertThat(runs).hasSize(2);
    var report = Files.readString(directory.resolve("campaign-report.md"));
    assertThat(report)
        .contains("NO_HELD_OUT_IMPROVEMENT", "WORSE", "LIMITED EVIDENCE", "10000", "seed: 9821")
        .contains("900 | 200 | 1200 | true", "estimate=-15/1", "lower=-15/1", "upper=-15/1")
        .contains("baseline is a deployment comparator", "LOWER BOUND");
    var results = Files.readString(directory.resolve("campaign-results.json"));
    assertThat(results)
        .contains(
            "\"searchMeasuredNanos\":\"900\"",
            "\"totalAdmissionNanos\":\"1200\"",
            "\"comparison\":\"WORSE\"",
            "\"interruptedCostUnknown\":true");
    assertThatThrownBy(
            () ->
                frozen.execute(
                    directory, (request, allocations, path) -> evidence(request.method(), false)))
        .isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
  }

  @Test
  void aMissingObservationDisablesTheFullPanelInterval() throws Exception {
    var protocol = protocol(List.of(101L));
    var frozen =
        CampaignHarness.freeze(
            protocol, (method, seed) -> definition(protocol, method, seed, Duration.ofMillis(1)));
    var directory = temporary.resolve("missing-fixture");
    frozen.execute(directory, (request, allocations, path) -> evidence(request.method(), true));
    var report = Files.readString(directory.resolve("campaign-report.md"));
    assertThat(report)
        .contains("UNAVAILABLE: the predeclared paired panel is incomplete or ineligible")
        .contains("INCONCLUSIVE")
        .doesNotContain("Interval[estimate=");
  }

  @Test
  void rejectsUnequalHorizonsOrLimitsAtFreeze() {
    var protocol = protocol(List.of(101L));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                CampaignHarness.freeze(
                    protocol,
                    (method, seed) ->
                        definition(
                            protocol,
                            method,
                            seed,
                            Duration.ofMillis(method == CampaignHarness.Method.TPE ? 1 : 2))))
        .withMessageContaining("identical frozen study inputs");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                CampaignHarness.freeze(
                    protocol,
                    (method, seed) -> definition(protocol, method, seed + 1, Duration.ofMillis(1))))
        .withMessageContaining("optimizer seed and search limits");
  }

  @Test
  void freezesCollectionsAndPredeclaresBalancedExecutionOrder() {
    var sizes = new LinkedHashMap<>(protocol(List.of(101L)).instanceSizes());
    var seeds = new ArrayList<>(List.of(101L, 211L));
    var protocol =
        new CampaignHarness.Protocol(
            "controlled-scheduling",
            CampaignHarness.Model.BASIC_SCHEDULING,
            sizes,
            seeds,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            30,
            9821,
            "Retain all outcomes.");
    sizes.clear();
    seeds.clear();
    var frozen =
        CampaignHarness.freeze(
            protocol, (method, seed) -> definition(protocol, method, seed, Duration.ofMillis(1)));
    assertThat(frozen.requests().stream().map(CampaignHarness.Request::directoryName))
        .containsExactly("tpe-101", "random-101", "random-211", "tpe-211");
    assertThat(CampaignHarness.Protocol.referenceOptimizerSeeds())
        .hasSize(10)
        .doesNotHaveDuplicates();
    assertThatThrownBy(() -> frozen.manifest().put("family", "changed"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void qualificationAllowanceIncludesSetupAndMonotonicClockWraparound() {
    assertThat(GreybayCampaignRunner.remainingNanos(100, 140, 80)).isEqualTo(40);
    assertThat(GreybayCampaignRunner.remainingNanos(100, 180, 80)).isZero();
    assertThat(GreybayCampaignRunner.remainingNanos(100, 240, 80)).isZero();
    assertThat(GreybayCampaignRunner.remainingNanos(Long.MAX_VALUE - 10, Long.MIN_VALUE + 9, 80))
        .isEqualTo(60);
  }

  private static CampaignHarness.Protocol protocol(List<Long> seeds) {
    return new CampaignHarness.Protocol(
        "controlled-scheduling",
        CampaignHarness.Model.BASIC_SCHEDULING,
        Map.of(
            "train-small",
            CampaignHarness.Size.SMALL,
            "heldout-medium",
            CampaignHarness.Size.MEDIUM,
            "heldout-large",
            CampaignHarness.Size.LARGE),
        seeds,
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        30,
        9821,
        "Retain all outcomes.");
  }

  private static GreybayStudyDefinition<SchedulingExample.Schedule, HardSoftScore> definition(
      CampaignHarness.Protocol protocol,
      CampaignHarness.Method method,
      long seed,
      Duration horizon) {
    return GreybayStudyDefinition.<SchedulingExample.Schedule, HardSoftScore>builder()
        .baseSolverConfig(SchedulingExample.baseSolverConfig())
        .problemAdapter(SchedulingExample.problemAdapter())
        .trainingPanel(new ProblemPanel(List.of("train-small"), List.of(11L, 17L)))
        .heldOutPanel(
            new ProblemPanel(List.of("heldout-medium", "heldout-large"), List.of(31L, 37L)))
        .templates(List.of(GreybayWorkflowExample.template()))
        .objective(new BalancedQualityObjective<>())
        .sampler(
            method == CampaignHarness.Method.TPE
                ? new TpeSamplerConfig().buildSampler()
                : new RandomSamplerConfig().buildSampler())
        .optimizerSeed(seed)
        .solveDuration(horizon)
        .warmupDuration(Duration.ZERO)
        .maxTrials(protocol.maximumTrials())
        .activeTimeLimit(protocol.searchAllocation())
        .build();
  }

  private static CampaignHarness.Evidence<HardSoftScore> evidence(
      CampaignHarness.Method method, boolean missing) {
    var objective =
        new BalancedObjectiveValue(
            ExactRational.ONE, List.of(ExactRational.ZERO, ExactRational.ONE));
    var baselineObjective =
        new BalancedObjectiveValue(
            ExactRational.ONE, List.of(ExactRational.ZERO, ExactRational.ZERO));
    var trial =
        new TrialResult<HardSoftScore>(
            1, "selected-fixture", null, TrialStatus.COMPLETE, objective, List.of(), null);
    var search =
        new StudyResult<>(
            "fixture",
            StudyState.SEARCH_FINISHED,
            TrainingStatus.IMPROVEMENT_FOUND,
            List.of(trial),
            trial,
            null,
            null,
            900,
            1000,
            true,
            null);
    var finished =
        new StudyResult<>(
            "fixture",
            StudyState.FINISHED,
            TrainingStatus.IMPROVEMENT_FOUND,
            List.of(trial),
            trial,
            1L,
            null,
            1100,
            1200,
            true,
            null);
    var cells = new ArrayList<EvaluationRecord<HardSoftScore>>();
    for (var instance : List.of("heldout-medium", "heldout-large")) {
      for (long seed : List.of(31L, 37L)) {
        cells.add(cell(instance, seed, "HELD_OUT_BASELINE", -10));
        if (!missing) {
          cells.add(
              cell(
                  instance,
                  seed,
                  "HELD_OUT_SELECTED",
                  method == CampaignHarness.Method.TPE ? -20 : -5));
        }
      }
    }
    var qualification =
        new QualificationResult<>(
            missing
                ? QualificationStatus.INCONCLUSIVE
                : method == CampaignHarness.Method.TPE
                    ? QualificationStatus.NO_HELD_OUT_IMPROVEMENT
                    : QualificationStatus.IMPROVED_ON_HELD_OUT_PANEL,
            missing
                ? QualificationComparison.UNAVAILABLE
                : method == CampaignHarness.Method.TPE
                    ? QualificationComparison.WORSE
                    : QualificationComparison.BETTER,
            1L,
            missing ? null : ExactRational.ONE,
            baselineObjective,
            objective,
            cells,
            "Controlled fixture only; no solver-effectiveness evidence.");
    return new CampaignHarness.Evidence<>(
        search, finished, qualification, "Controlled cost fixture.");
  }

  private static EvaluationRecord<HardSoftScore> cell(
      String instance, long seed, String stage, long soft) {
    return new EvaluationRecord<>(
        stage + ":" + instance + ":" + seed,
        stage,
        stage.equals("HELD_OUT_BASELINE") ? 0 : 1,
        instance,
        seed,
        1,
        EvaluationStatus.COMPLETE,
        HardSoftScore.of(0, soft),
        0,
        100,
        0,
        100,
        10,
        20,
        VerificationResult.passed(),
        null,
        null);
  }
}
