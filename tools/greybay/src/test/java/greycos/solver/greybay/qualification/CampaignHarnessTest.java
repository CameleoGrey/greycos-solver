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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongFunction;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.api.ComponentIdentity;
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
import greycos.solver.greybay.api.objective.CompletedPanel;
import greycos.solver.greybay.api.objective.ExactRational;
import greycos.solver.greybay.api.objective.ExactScoreAdapter;
import greycos.solver.greybay.api.objective.ObjectiveCalibration;
import greycos.solver.greybay.api.objective.ObjectiveCodec;
import greycos.solver.greybay.api.objective.ObjectiveValue;
import greycos.solver.greybay.api.objective.StudyObjective;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.ParameterAssignment;
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
  void freezesBaselineAssignmentAndRejectsUnequalHistoryPolicies() throws Exception {
    var protocol = protocol(List.of(101L, 211L));
    var template = GreybayWorkflowExample.template();
    var values = new LinkedHashMap<>(template.defaultAssignment().values());
    values.put("template", template.identity().id());
    var assignment = ParameterAssignment.of(values);
    var baseline =
        template.materialize(SchedulingExample.baseSolverConfig(), template.defaultAssignment());
    CampaignHarness.DefinitionFactory<SchedulingExample.Schedule, HardSoftScore> definitions =
        (method, seed) ->
            definitionBuilder(
                    protocol,
                    method,
                    seed,
                    Duration.ofMillis(1),
                    new BalancedQualityObjective<HardSoftScore>())
                .baseSolverConfig(baseline)
                .baselineCandidateAssignment(assignment)
                .build();
    var frozen = CampaignHarness.freeze(protocol, definitions);
    assertThat(frozen.manifest().get("study"))
        .isInstanceOfSatisfying(
            Map.class,
            study ->
                assertThat(study.get("baselineCandidateAssignment")).isEqualTo(assignment.toMap()));
    assertThat(CampaignHarness.freeze(protocol, definitions).manifestBytes())
        .isEqualTo(frozen.manifestBytes());
    CampaignHarness.DefinitionFactory<SchedulingExample.Schedule, HardSoftScore> omitted =
        (method, seed) ->
            definitionBuilder(
                    protocol,
                    method,
                    seed,
                    Duration.ofMillis(1),
                    new BalancedQualityObjective<HardSoftScore>())
                .baseSolverConfig(baseline)
                .build();
    var withoutAssignment = CampaignHarness.freeze(protocol, omitted);
    assertThat(withoutAssignment.fingerprint()).isNotEqualTo(frozen.fingerprint());
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                CampaignHarness.freeze(
                    protocol,
                    (method, seed) ->
                        method == CampaignHarness.Method.TPE
                            ? definitions.create(method, seed)
                            : omitted.create(method, seed)))
        .withMessageContaining("identical frozen study inputs");

    var changedRequests = new ArrayList<>(frozen.requests());
    var first = changedRequests.getFirst();
    changedRequests.set(
        0,
        new CampaignHarness.Request<>(
            first.method(),
            first.optimizerSeed(),
            omitted.create(first.method(), first.optimizerSeed())));
    var changed = new CampaignHarness.Frozen<>(protocol, frozen.manifest(), changedRequests);
    var directory = temporary.resolve("changed-baseline-history");
    assertThatThrownBy(
            () ->
                changed.execute(
                    directory,
                    (request, allocations, path) -> {
                      throw new AssertionError(
                          "Changed definitions must be rejected before any runner invocation.");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Frozen study identity changed");
    assertThat(directory).doesNotExist();
  }

  @Test
  void requiresTheSameExactScoreAdapterAcrossMethodsAndOptimizerSeeds() {
    var protocol = protocol(List.of(101L, 211L));
    var stable = new ReportingObjective<>(scaledAdapter("stable", 1));
    var frozen =
        CampaignHarness.freeze(
            protocol,
            (method, seed) ->
                definitionBuilder(protocol, method, seed, Duration.ofMillis(1), stable).build());
    assertThat(frozen.requests()).hasSize(4);
    assertThat(frozen.manifest().get("study"))
        .isInstanceOfSatisfying(
            Map.class,
            study ->
                assertThat(study.get("scoreAdapter"))
                    .isEqualTo(
                        Map.of(
                            "id", "campaign-exact-score", "version", "1", "artifact", "stable")));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                CampaignHarness.freeze(
                    protocol,
                    (method, seed) ->
                        definitionBuilder(
                                protocol,
                                method,
                                seed,
                                Duration.ofMillis(1),
                                new ReportingObjective<>(scaledAdapter(method.name(), 1)))
                            .build()))
        .withMessageContaining("identical frozen study inputs");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                CampaignHarness.freeze(
                    protocol,
                    (method, seed) ->
                        definitionBuilder(
                                protocol,
                                method,
                                seed,
                                Duration.ofMillis(1),
                                new ReportingObjective<>(scaledAdapter(Long.toString(seed), 1)))
                            .build()))
        .withMessageContaining("identical frozen study inputs");
  }

  @Test
  void rechecksTheExactScoreAdapterIdentityBeforeExecution() {
    var identity =
        new AtomicReference<>(new ComponentIdentity("campaign-exact-score", "1", "before"));
    ExactScoreAdapter<HardSoftScore> adapter =
        new ExactScoreAdapter<>() {
          @Override
          public ComponentIdentity identity() {
            return identity.get();
          }

          @Override
          public List<ExactRational> levels(HardSoftScore score) {
            return ExactScoreAdapter.<HardSoftScore>standard().levels(score);
          }
        };
    var objective = new ReportingObjective<>(adapter);
    var protocol = protocol(List.of(101L));
    var frozen =
        CampaignHarness.freeze(
            protocol,
            (method, seed) ->
                definitionBuilder(protocol, method, seed, Duration.ofMillis(1), objective).build());
    identity.set(new ComponentIdentity("campaign-exact-score", "1", "after"));
    var directory = temporary.resolve("changed-score-adapter");
    assertThatThrownBy(
            () ->
                frozen.execute(
                    directory,
                    (request, allocations, path) -> {
                      throw new AssertionError(
                          "Changed adapter must be rejected before any runner invocation.");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Frozen study identity changed");
    assertThat(directory).doesNotExist();
  }

  @Test
  void pairedReportsUseTheRegisteredAdapterAndPreserveNativeScoresAndFeasibility()
      throws Exception {
    var protocol = protocol(List.of(101L));
    var objective = new BalancedQualityObjective<>(scaledAdapter("doubled-levels", 2));
    var frozen =
        CampaignHarness.freeze(
            protocol,
            (method, seed) ->
                definitionBuilder(protocol, method, seed, Duration.ofMillis(1), objective).build());
    var directory = temporary.resolve("registered-score-adapter");
    frozen.execute(directory, (request, allocations, path) -> evidence(request.method(), false));
    var report = Files.readString(directory.resolve("campaign-report.md"));
    assertThat(report)
        .contains("estimate=-30/1", "lower=-30/1", "upper=-30/1")
        .contains("Feasibility difference: Interval[estimate=0/1, lower=0/1, upper=0/1]")
        .contains("estimate=-20/1", "estimate=10/1")
        .doesNotContain("estimate=-15/1");
    assertThat(Files.readString(directory.resolve("campaign-results.json")))
        .contains(
            "\"score\":\"0hard/-20soft\"", "\"comparison\":\"WORSE\"", "\"feasibility\":\"1/1\"");
  }

  @Test
  void pairedReportsSupportCustomNumberLevelsThroughTheRegisteredAdapter() throws Exception {
    ExactScoreAdapter<FractionScore> adapter =
        new ExactScoreAdapter<>() {
          @Override
          public ComponentIdentity identity() {
            return new ComponentIdentity("campaign-fractions", "1", "exact-third-levels");
          }

          @Override
          public List<ExactRational> levels(FractionScore score) {
            return List.of(ExactRational.ZERO, ExactRational.of(score.soft.numerator, 3));
          }
        };
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ExactScoreAdapter.<FractionScore>standard()
                    .levels(new FractionScore(new Third(-20))))
        .withMessageContaining("ExactScoreAdapter");
    var protocol = protocol(List.of(101L));
    var objective = new BalancedQualityObjective<>(adapter);
    // Only the controlled runner supplies observations; no solver uses this custom-score fixture.
    var frozen =
        CampaignHarness.freeze(
            protocol,
            (method, seed) ->
                definitionBuilder(protocol, method, seed, Duration.ofMillis(1), objective).build());
    var directory = temporary.resolve("custom-number-adapter");
    frozen.execute(
        directory,
        (request, allocations, path) ->
            evidence(request.method(), false, soft -> new FractionScore(new Third(soft))));
    assertThat(Files.readString(directory.resolve("campaign-report.md")))
        .contains("estimate=-5/1", "lower=-5/1", "upper=-5/1")
        .contains("Feasibility difference: Interval[estimate=0/1, lower=0/1, upper=0/1]");
    assertThat(Files.readString(directory.resolve("campaign-results.json")))
        .contains(
            "\"score\":\"0hard/-20/3soft\"", "\"qualificationStatus\":\"NO_HELD_OUT_IMPROVEMENT\"");
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
    return definitionBuilder(
            protocol, method, seed, horizon, new BalancedQualityObjective<HardSoftScore>())
        .build();
  }

  private static <Score_ extends Score<Score_>>
      GreybayStudyDefinition.Builder<SchedulingExample.Schedule, Score_> definitionBuilder(
          CampaignHarness.Protocol protocol,
          CampaignHarness.Method method,
          long seed,
          Duration horizon,
          StudyObjective<Score_> objective) {
    return GreybayStudyDefinition.<SchedulingExample.Schedule, Score_>builder()
        .baseSolverConfig(SchedulingExample.baseSolverConfig())
        .problemAdapter(SchedulingExample.problemAdapter())
        .trainingPanel(new ProblemPanel(List.of("train-small"), List.of(11L, 17L)))
        .heldOutPanel(
            new ProblemPanel(List.of("heldout-medium", "heldout-large"), List.of(31L, 37L)))
        .templates(List.of(GreybayWorkflowExample.template()))
        .objective(objective)
        .sampler(
            method == CampaignHarness.Method.TPE
                ? new TpeSamplerConfig().buildSampler()
                : new RandomSamplerConfig().buildSampler())
        .optimizerSeed(seed)
        .solveDuration(horizon)
        .warmupDuration(Duration.ZERO)
        .maxTrials(protocol.maximumTrials())
        .activeTimeLimit(protocol.searchAllocation());
  }

  private static CampaignHarness.Evidence<HardSoftScore> evidence(
      CampaignHarness.Method method, boolean missing) {
    return evidence(method, missing, soft -> HardSoftScore.of(0, soft));
  }

  private static <Score_ extends Score<Score_>> CampaignHarness.Evidence<Score_> evidence(
      CampaignHarness.Method method, boolean missing, LongFunction<Score_> scores) {
    var objective =
        new BalancedObjectiveValue(
            ExactRational.ONE, List.of(ExactRational.ZERO, ExactRational.ONE));
    var baselineObjective =
        new BalancedObjectiveValue(
            ExactRational.ONE, List.of(ExactRational.ZERO, ExactRational.ZERO));
    var trial =
        new TrialResult<Score_>(
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
    var cells = new ArrayList<EvaluationRecord<Score_>>();
    for (var instance : List.of("heldout-medium", "heldout-large")) {
      for (long seed : List.of(31L, 37L)) {
        cells.add(cell(instance, seed, "HELD_OUT_BASELINE", scores.apply(-10)));
        if (!missing) {
          cells.add(
              cell(
                  instance,
                  seed,
                  "HELD_OUT_SELECTED",
                  scores.apply(method == CampaignHarness.Method.TPE ? -20 : -5)));
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

  private static <Score_ extends Score<Score_>> EvaluationRecord<Score_> cell(
      String instance, long seed, String stage, Score_ score) {
    return new EvaluationRecord<>(
        stage + ":" + instance + ":" + seed,
        stage,
        stage.equals("HELD_OUT_BASELINE") ? 0 : 1,
        instance,
        seed,
        1,
        EvaluationStatus.COMPLETE,
        score,
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

  private static ExactScoreAdapter<HardSoftScore> scaledAdapter(String artifact, long factor) {
    return new ExactScoreAdapter<>() {
      @Override
      public ComponentIdentity identity() {
        return new ComponentIdentity("campaign-exact-score", "1", artifact);
      }

      @Override
      public List<ExactRational> levels(HardSoftScore score) {
        return ExactScoreAdapter.<HardSoftScore>standard().levels(score).stream()
            .map(level -> level.multiply(ExactRational.of(factor)))
            .toList();
      }
    };
  }

  // A custom objective's identity and reporting adapter identity are independent declarations.
  private record ReportingObjective<Score_ extends Score<Score_>>(
      ExactScoreAdapter<Score_> scoreAdapter) implements StudyObjective<Score_> {
    @Override
    public ComponentIdentity identity() {
      return new ComponentIdentity("campaign-custom-objective", "1", "fixed-objective-artifact");
    }

    @Override
    public ObjectiveCalibration calibrate(CompletedPanel<Score_> baseline) {
      return new BalancedQualityObjective<>(scoreAdapter).calibrate(baseline);
    }

    @Override
    public ObjectiveValue evaluate(CompletedPanel<Score_> panel, ObjectiveCalibration calibration) {
      return new BalancedQualityObjective<>(scoreAdapter).evaluate(panel, calibration);
    }

    @Override
    public int compare(ObjectiveValue left, ObjectiveValue right) {
      return new BalancedQualityObjective<>(scoreAdapter).compare(left, right);
    }

    @Override
    public ObjectiveCodec codec() {
      return new BalancedQualityObjective<>(scoreAdapter).codec();
    }
  }

  private static final class Third extends Number {
    private final long numerator;

    private Third(long numerator) {
      this.numerator = numerator;
    }

    @Override
    public int intValue() {
      throw new AssertionError("Custom score levels require exact conversion.");
    }

    @Override
    public long longValue() {
      throw new AssertionError("Custom score levels require exact conversion.");
    }

    @Override
    public float floatValue() {
      throw new AssertionError("Custom score levels require exact conversion.");
    }

    @Override
    public double doubleValue() {
      throw new AssertionError("Custom score levels require exact conversion.");
    }
  }

  private record FractionScore(Third soft) implements Score<FractionScore> {
    @Override
    public Number[] toLevelNumbers() {
      return new Number[] {0L, soft};
    }

    @Override
    public boolean isFeasible() {
      return true;
    }

    @Override
    public String toString() {
      return "0hard/" + soft.numerator + "/3soft";
    }

    @Override
    public String toShortString() {
      return toString();
    }

    @Override
    public int compareTo(FractionScore other) {
      return Long.compare(soft.numerator, other.soft.numerator);
    }

    @Override
    public FractionScore zero() {
      throw new AssertionError("Campaign reporting must not perform score arithmetic.");
    }

    @Override
    public FractionScore add(FractionScore addend) {
      throw new AssertionError("Campaign reporting must not perform score arithmetic.");
    }

    @Override
    public FractionScore subtract(FractionScore subtrahend) {
      throw new AssertionError("Campaign reporting must not perform score arithmetic.");
    }

    @Override
    public FractionScore multiply(double multiplicand) {
      throw new AssertionError("Campaign reporting must not perform score arithmetic.");
    }

    @Override
    public FractionScore divide(double divisor) {
      throw new AssertionError("Campaign reporting must not perform score arithmetic.");
    }

    @Override
    public FractionScore power(double exponent) {
      throw new AssertionError("Campaign reporting must not perform score arithmetic.");
    }

    @Override
    public FractionScore abs() {
      throw new AssertionError("Campaign reporting must not perform score arithmetic.");
    }
  }
}
