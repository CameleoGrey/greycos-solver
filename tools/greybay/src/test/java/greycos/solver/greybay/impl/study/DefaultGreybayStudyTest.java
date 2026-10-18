package greycos.solver.greybay.impl.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.ConfigurationTemplate;
import greycos.solver.greybay.api.EvaluationStatus;
import greycos.solver.greybay.api.ProblemCapabilities;
import greycos.solver.greybay.api.QualificationComparison;
import greycos.solver.greybay.api.QualificationStatus;
import greycos.solver.greybay.api.StudyState;
import greycos.solver.greybay.api.TrainingStatus;
import greycos.solver.greybay.api.TrialStatus;
import greycos.solver.greybay.api.VerificationResult;
import greycos.solver.greybay.api.objective.BalancedQualityObjective;
import greycos.solver.greybay.api.sampling.CandidateProposal;
import greycos.solver.greybay.api.sampling.Sampler;
import greycos.solver.greybay.api.sampling.SamplerState;
import greycos.solver.greybay.api.sampling.SamplingContext;
import greycos.solver.greybay.api.sampling.SamplingObservation;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.IntegerDimension;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.config.RandomSamplerConfig;
import greycos.solver.greybay.config.SearchSpace;
import greycos.solver.greybay.config.StudyRunLimits;
import greycos.solver.greybay.examples.SchedulingExample;
import greycos.solver.greybay.impl.evaluation.EvaluationOutcome;
import greycos.solver.greybay.impl.persistence.ArtifactReference;
import greycos.solver.greybay.impl.persistence.StudyStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DefaultGreybayStudyTest {
  @TempDir Path directory;

  @Test
  void heartbeatCancellationAfterPausedPublicationPreservesPausedDuringCleanup() throws Exception {
    var definition = definition(0);
    var current =
        new AtomicReference<DefaultGreybayStudy<SchedulingExample.Schedule, HardSoftScore>>();
    var elapsed = new AtomicLong();
    var injected = new AtomicBoolean();
    var heartbeat = DefaultGreybayStudy.class.getDeclaredMethod("heartbeat");
    heartbeat.setAccessible(true);
    LongSupplier clock =
        () -> {
          var study = current.get();
          if (study != null
              && study.snapshot().state() == StudyState.PAUSED
              && injected.compareAndSet(false, true)) {
            // checkpoint() reads the clock after publishing PAUSED while the operation still owns
            // cleanup.
            // Force the exact heartbeat/cancellation interleaving without scheduler timing or
            // sleeps.
            elapsed.set(definition.runLimits().activeTimeLimit().toNanos());
            try {
              heartbeat.invoke(study);
            } catch (ReflectiveOperationException e) {
              throw new AssertionError(e);
            }
          }
          return elapsed.get();
        };
    EvaluationRunner<SchedulingExample.Schedule, HardSoftScore> mustNotRun =
        (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
          throw new AssertionError("Stop before run must not admit an evaluation.");
        };
    try (var study =
        DefaultGreybayStudy.open(
            directory.resolve("study"),
            definition,
            definition.runLimits(),
            false,
            mustNotRun,
            clock)) {
      current.set(study);
      study.requestStop();
      var result = study.run();
      assertThat(injected.get()).isTrue();
      assertThat(result.state()).isEqualTo(StudyState.PAUSED);
      assertThat(study.snapshot().state()).isEqualTo(StudyState.PAUSED);
      study.requestStop();
      assertThat(study.snapshot().state()).isEqualTo(StudyState.PAUSED);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "INVALID_RESULT, FAILED",
    "BUDGET_EXCEEDED, FAILED",
    "INCOMPLETE_RESULT, INCONCLUSIVE"
  })
  void qualificationSeparatesKnownCandidateFailuresFromMissingEvidence(
      EvaluationStatus candidateStatus, QualificationStatus expectedStatus) {
    var clock = new AtomicLong();
    EvaluationRunner<SchedulingExample.Schedule, HardSoftScore> evaluator =
        (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
          clock.addAndGet(1_000_000);
          if (descriptor.id().startsWith("heldout-") && history(config) > 0) {
            return new EvaluationOutcome<>(
                candidateStatus,
                null,
                -1,
                -1,
                -1,
                2_000_000_000L,
                0,
                0,
                VerificationResult.notConfigured(),
                null,
                "Synthetic held-out candidate outcome: " + candidateStatus,
                List.of());
          }
          return complete(HardSoftScore.of(0, -100 + history(config)));
        };
    try (var study = open(definition(1), evaluator, clock, false)) {
      assertThat(study.run().incumbent().trialId()).isPositive();
      var qualification = study.qualify();
      assertThat(qualification.status()).isEqualTo(expectedStatus);
      assertThat(qualification.comparison()).isEqualTo(QualificationComparison.UNAVAILABLE);
      assertThat(qualification.baselineObjective()).isNotNull();
      assertThat(qualification.selectedObjective()).isNull();
      assertThat(study.snapshot().state()).isEqualTo(StudyState.FINISHED);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void checkpointsProjectReservationsTerminalTrialsAndFrozenQualification() throws Exception {
    var definition = definition(1);
    var clock = new AtomicLong();
    long selected;
    try (var study =
        open(
            definition,
            runner(
                clock,
                new AtomicInteger(),
                (config, problem) -> HardSoftScore.of(0, -100 + history(config))),
            clock,
            false)) {
      study.run();
      selected = study.qualify().selectedTrialId();
    }
    try (var store =
        StudyStore.resume(directory.resolve("study"), StudyManifest.prepare(definition).value())) {
      var records = store.records();
      var proposal =
          records.stream()
              .filter(record -> record.kind().equals("PROPOSED"))
              .findFirst()
              .orElseThrow()
              .payload();
      var checkpoint = store.checkpoint().orElseThrow();
      var state = checkpoint.state();
      assertThat((List<String>) state.get("reservedIdentities"))
          .contains((String) proposal.get("candidateIdentity"))
          .isSorted();
      assertThat(state.get("samplerState")).isEqualTo(proposal.get("samplerState"));
      var selectedTrial = ((List<Map<String, Object>>) state.get("trials")).get(1);
      assertThat(selectedTrial)
          .containsEntry("assignment", proposal.get("assignment"))
          .containsEntry("config", proposal.get("config"));

      assertThat(state)
          .containsEntry("projectionVersion", "1")
          .containsEntry("recoveryAuthority", "journal")
          .containsEntry("state", "FINISHED")
          .containsEntry("maximumCandidateTrials", "1")
          .containsEntry("frozenSelectionTrialId", Long.toString(selected))
          .containsEntry("qualificationTerminal", true);
      assertThat(state.get("calibration")).isInstanceOf(String.class);
      assertThat(state.get("heldOutCalibration")).isInstanceOf(String.class);
      assertThat((Map<String, Object>) state.get("pendingAttempts")).isEmpty();
      assertThat((Map<String, Object>) state.get("nextAttempts"))
          .isNotEmpty()
          .allSatisfy((key, attempt) -> assertThat(attempt).isEqualTo("1"));
      assertThat((Map<String, Object>) state.get("operationAccounting")).isNotEmpty();
      assertThat((List<String>) state.get("completedHeldOutEvaluationIds")).hasSize(2);
      assertThat((Map<String, Object>) state.get("qualification"))
          .containsEntry("status", "IMPROVED_ON_HELD_OUT_PANEL")
          .containsEntry("comparison", "BETTER")
          .containsEntry("selectedTrialId", Long.toString(selected));
      var terminalTrials = (List<Map<String, Object>>) state.get("trials");
      assertThat(terminalTrials)
          .hasSize(2)
          .allSatisfy(
              trial -> {
                assertThat(trial).containsEntry("status", "COMPLETE");
                assertThat(trial.get("objective")).isInstanceOf(String.class);
              });
      var committed = (List<Map<String, Object>>) state.get("committedAttempts");
      assertThat(committed).hasSameSizeAs((List<?>) state.get("terminalEvaluationIds"));
      for (var attempt : committed) {
        var reference = (Map<String, Object>) attempt.get("observation");
        assertThat(
                store.readArtifact(
                    new ArtifactReference(
                        (String) reference.get("digest"), (String) reference.get("suffix"))))
            .isNotEmpty();
      }
      var selection =
          records.stream()
              .filter(record -> record.kind().equals("SELECTION_FROZEN"))
              .findFirst()
              .orElseThrow();
      assertThat(selection.payload()).containsEntry("trialId", Long.toString(selected));
      assertThat(selection.sequence()).isLessThan(checkpoint.sequence());
      assertThat(
              records.stream().filter(record -> record.kind().equals(StudyStore.CHECKPOINT_KIND)))
          .isEmpty();
      assertThat(
              records.stream()
                  .filter(record -> record.kind().equals(StudyStore.CHECKPOINT_MARKER_KIND)))
          .isNotEmpty()
          .allSatisfy(record -> assertThat(record.payload()).containsOnlyKeys("stateDigest"));
      assertThat(records.getLast().kind()).isEqualTo("ACTIVE_COMPLETED");
      assertThat(records.getLast().sequence()).isGreaterThan(checkpoint.sequence());
    }
  }

  @Test
  void resumesCreationInterruptedBeforeTheBaselineReservation() throws Exception {
    var definition = definition(0);
    try (var store =
        StudyStore.create(directory.resolve("study"), StudyManifest.prepare(definition).value())) {
      assertThat(store.records()).isEmpty();
    }
    var clock = new AtomicLong();
    try (var resumed =
        open(
            definition,
            runner(clock, new AtomicInteger(), (config, problem) -> HardSoftScore.ZERO),
            clock,
            true)) {
      assertThat(resumed.snapshot().interruptedCostUnknown()).isTrue();
      assertThat(resumed.snapshot().trials()).hasSize(1);
      assertThat(resumed.run().hasEligibleIncumbent()).isTrue();
    }
  }

  @Test
  void preservesOpeningHeartbeatBeforeAnyProcessStartedRecord() throws Exception {
    var definition = definition(0);
    try (var store =
        StudyStore.create(directory.resolve("study"), StudyManifest.prepare(definition).value())) {
      store.append("ACTIVE_STARTED", Map.of("operationId", "session-1/setup-1", "kind", "setup"));
      store.append(
          "ACTIVE_HEARTBEAT",
          Map.of("operationId", "session-1/setup-1", "elapsedNanos", "2000000000"));
    }
    var clock = new AtomicLong();
    try (var resumed =
        open(
            definition,
            runner(clock, new AtomicInteger(), (config, problem) -> HardSoftScore.ZERO),
            clock,
            true)) {
      assertThat(resumed.snapshot().interruptedCostUnknown()).isTrue();
      assertThat(resumed.snapshot().measuredActiveNanos()).isEqualTo(2_000_000_000L);
      assertThat(resumed.run().hasEligibleIncumbent()).isTrue();
    }
  }

  @Test
  void completeFixedPanelsQualifyOnceAndForbidSearchAfterHeldOutAccess() {
    var clock = new AtomicLong();
    var calls = new AtomicInteger();
    var definition = definition(2);
    var runner =
        runner(clock, calls, (config, problem) -> HardSoftScore.of(0, -100 + history(config)));
    try (var study = open(definition, runner, clock, false)) {
      var result = study.run();
      assertThat(result.state()).isEqualTo(StudyState.SEARCH_FINISHED);
      assertThat(result.trials()).hasSize(3);
      assertThat(result.trials())
          .allSatisfy(
              trial -> {
                assertThat(trial.status()).isEqualTo(TrialStatus.COMPLETE);
                assertThat(trial.evaluations()).hasSize(4);
                assertThat(trial.evaluations())
                    .extracting(record -> record.problemId() + "/" + record.solverSeed())
                    .containsExactly(
                        "train-small/11", "train-small/29", "train-medium/11", "train-medium/29");
              });
      assertThat(result.incumbent().assignment().getLong("history")).isEqualTo(3);
      assertThat(result.trainingStatus()).isEqualTo(TrainingStatus.IMPROVEMENT_FOUND);
      int beforeHeldOut = calls.get();
      var qualified = study.qualify();
      assertThat(qualified.status()).isEqualTo(QualificationStatus.IMPROVED_ON_HELD_OUT_PANEL);
      assertThat(qualified.comparison()).isEqualTo(QualificationComparison.BETTER);
      assertThat(qualified.evaluations()).hasSize(2);
      assertThat(calls.get() - beforeHeldOut).isEqualTo(2);
      assertThat(study.qualify()).isEqualTo(qualified);
      assertThatThrownBy(study::run)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("frozen");
    }
    try (var resumed = open(definition, runner, clock, true)) {
      assertThat(resumed.snapshot().state()).isEqualTo(StudyState.FINISHED);
      assertThat(resumed.qualify().status())
          .isEqualTo(QualificationStatus.IMPROVED_ON_HELD_OUT_PANEL);
      assertThatThrownBy(resumed::run).hasMessageContaining("frozen");
    }
  }

  @Test
  void exactTiesRetainBaselineAndReuseItsHeldOutCells() {
    var clock = new AtomicLong();
    var calls = new AtomicInteger();
    var runner = runner(clock, calls, (config, problem) -> HardSoftScore.of(0, -100));
    try (var study = open(definition(2), runner, clock, false)) {
      assertThat(study.run().incumbent().trialId()).isZero();
      int before = calls.get();
      var qualification = study.qualify();
      assertThat(qualification.comparison()).isEqualTo(QualificationComparison.EQUAL);
      assertThat(qualification.status()).isEqualTo(QualificationStatus.NO_HELD_OUT_IMPROVEMENT);
      assertThat(qualification.evaluations()).hasSize(1);
      assertThat(calls.get() - before).isEqualTo(1);
    }
  }

  @Test
  void bestEffortImprovementDoesNotBecomeAFeasibleRecommendation() {
    var clock = new AtomicLong();
    var runner =
        runner(
            clock,
            new AtomicInteger(),
            (config, problem) -> HardSoftScore.of(-10 + history(config), -100));
    try (var study = open(definition(2), runner, clock, false)) {
      assertThat(study.run().trainingStatus())
          .isEqualTo(TrainingStatus.NO_FULLY_FEASIBLE_CANDIDATE);
      var qualification = study.qualify();
      assertThat(qualification.comparison()).isEqualTo(QualificationComparison.BETTER);
      assertThat(qualification.status()).isEqualTo(QualificationStatus.NO_FULLY_FEASIBLE_CANDIDATE);
      assertThat(qualification.feasibilityRate().numerator()).isZero();
    }
  }

  @Test
  void interruptedTrialKeepsSlotAndCommittedCellsOnResume() {
    var clock = new AtomicLong();
    var current =
        new AtomicReference<DefaultGreybayStudy<SchedulingExample.Schedule, HardSoftScore>>();
    var interruptedOnce = new AtomicInteger();
    var runner =
        runner(
            clock,
            new AtomicInteger(),
            (config, problem) -> {
              if (config.getRandomSeed() == 11
                  && history(config) > 0
                  && interruptedOnce.getAndIncrement() == 0) {
                current.get().requestStop();
              }
              return HardSoftScore.of(0, -100 + history(config));
            });
    var definition = definition(1);
    try (var study = open(definition, runner, clock, false)) {
      current.set(study);
      var paused = study.run();
      assertThat(paused.state()).isEqualTo(StudyState.PAUSED);
      assertThat(paused.trials()).hasSize(2);
      assertThat(paused.trials().get(1).status()).isEqualTo(TrialStatus.INTERRUPTED);
      assertThat(paused.trials().get(1).evaluations()).hasSize(1);
    }
    var resumedCalls = new AtomicInteger();
    try (var resumed =
        open(
            definition,
            runner(
                clock,
                resumedCalls,
                (config, problem) -> HardSoftScore.of(0, -100 + history(config))),
            clock,
            true)) {
      var finished = resumed.run();
      assertThat(finished.trials()).hasSize(2);
      assertThat(finished.trials().get(1).evaluations()).hasSize(4);
      assertThat(resumedCalls).hasValue(3);
    }
  }

  @Test
  void operationalCapInterruptsInsteadOfFabricatingAnIneligibleCell() {
    var clock = new AtomicLong();
    var definition = builder(2).activeTimeLimit(Duration.ofMillis(5)).build();
    var runner = runner(clock, new AtomicInteger(), (config, problem) -> HardSoftScore.of(0, -100));
    try (var study = open(definition, runner, clock, false)) {
      var paused = study.run();
      assertThat(paused.state()).isEqualTo(StudyState.PAUSED);
      assertThat(paused.stopReason()).isEqualTo("ACTIVE_TIME_LIMIT");
      assertThat(paused.trials())
          .allSatisfy(
              trial ->
                  assertThat(trial.status()).isNotIn(TrialStatus.INELIGIBLE, TrialStatus.FAILED));
    }
    try (var resumed =
        DefaultGreybayStudy.open(
            directory.resolve("study"),
            definition,
            StudyRunLimits.of(2, Duration.ofHours(1)),
            true,
            runner,
            clock::get)) {
      assertThat(resumed.run().hasEligibleIncumbent()).isTrue();
    }
  }

  @Test
  void invalidCandidateHasNoObjectiveAndCannotWin() {
    var clock = new AtomicLong();
    EvaluationRunner<SchedulingExample.Schedule, HardSoftScore> runner =
        (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
          clock.addAndGet(1_000_000);
          if (history(config) > 0 && seed != Long.MIN_VALUE) {
            return new EvaluationOutcome<>(
                EvaluationStatus.INVALID_RESULT,
                HardSoftScore.of(0, 999),
                0,
                1,
                0,
                1_000_000,
                0,
                0,
                VerificationResult.invalid("Independent replay rejected assignments"),
                null,
                "invalid",
                List.of());
          }
          return complete(HardSoftScore.of(0, -100));
        };
    try (var study = open(definition(1), runner, clock, false)) {
      var result = study.run();
      assertThat(result.incumbent().trialId()).isZero();
      assertThat(result.trials().get(1).status()).isEqualTo(TrialStatus.INELIGIBLE);
      assertThat(result.trials().get(1).objective()).isNull();
      assertThat(result.trials().get(1).evaluations()).hasSize(1);
    }
  }

  @Test
  void incompleteBaselineStopsBeforeSampling() {
    var clock = new AtomicLong();
    EvaluationRunner<SchedulingExample.Schedule, HardSoftScore> runner =
        (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) ->
            seed == Long.MIN_VALUE
                ? complete(HardSoftScore.ZERO)
                : new EvaluationOutcome<>(
                    EvaluationStatus.INCOMPLETE_RESULT,
                    null,
                    -1,
                    -1,
                    -1,
                    1,
                    0,
                    0,
                    VerificationResult.notConfigured(),
                    null,
                    "No on-time initialized event",
                    List.of());
    try (var study = open(definition(2), runner, clock, false)) {
      assertThatThrownBy(study::run)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Calibration failed");
      assertThat(study.snapshot().state()).isEqualTo(StudyState.FAILED);
      assertThat(study.snapshot().trials()).hasSize(1);
      assertThat(study.snapshot().hasEligibleIncumbent()).isFalse();
    }
  }

  @Test
  void explicitMatchingBaselineAssignmentEntersDensityHistoryAsCalibration() {
    var sampler = new RecordingSampler();
    var template = new HistoryTemplate();
    var baseline =
        template.materialize(
            SchedulingExample.baseSolverConfig(), ParameterAssignment.of(Map.of("history", 2L)));
    var definition =
        builder(1)
            .baseSolverConfig(baseline)
            .sampler(sampler)
            .baselineCandidateAssignment(
                ParameterAssignment.of(Map.of("template", "history", "history", 2L)))
            .build();
    var clock = new AtomicLong();
    try (var study =
        open(
            definition,
            runner(clock, new AtomicInteger(), (config, problem) -> HardSoftScore.ZERO),
            clock,
            false)) {
      study.run();
      assertThat(sampler.history).hasSize(1);
      assertThat(sampler.history.getFirst().calibration()).isTrue();
      assertThat(sampler.history.getFirst().trialId()).isZero();
      assertThat(study.snapshot().trials()).hasSize(2);
    }
  }

  @Test
  void rejectsClaimedBaselineAssignmentThatMaterializesAnotherConfiguration() {
    var definition =
        builder(1)
            .baselineCandidateAssignment(
                ParameterAssignment.of(Map.of("template", "history", "history", 2L)))
            .build();
    var clock = new AtomicLong();
    assertThatThrownBy(
            () ->
                open(
                    definition,
                    runner(clock, new AtomicInteger(), (config, problem) -> HardSoftScore.ZERO),
                    clock,
                    false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly the effective baseline");
  }

  @Test
  void configurationAndOperationalValidationFailBeforeWork() {
    assertThatThrownBy(() -> builder(1).optimizerSeed(1).solveDuration(Duration.ofNanos(1)).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("whole-millisecond");
    assertThatThrownBy(
            () ->
                builder(1)
                    .heldOutPanel(new ProblemPanel(List.of("heldout-small"), List.of(11L)))
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("disjoint");
    var source = SchedulingExample.baseSolverConfig();
    var definition = builder(1).baseSolverConfig(source).build();
    source.setTerminationConfig(new TerminationConfig().withBestScoreLimit("0hard/0soft"));
    assertThat(definition.baseSolverConfig().getTerminationConfig()).isNull();
    var clock = new AtomicLong();
    var runner = runner(clock, new AtomicInteger(), (config, problem) -> HardSoftScore.ZERO);
    try (var study = open(definition, runner, clock, false)) {
      study.run();
    }
    assertThatThrownBy(
            () ->
                DefaultGreybayStudy.open(
                    directory.resolve("study"),
                    definition,
                    StudyRunLimits.of(0, Duration.ofHours(1)),
                    true,
                    runner,
                    clock::get))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot reduce");
  }

  @Test
  void protocolRejectsHiddenSearchTerminationAndPhaseWorkers() {
    var definition = definition(1);
    var config = definition.baseSolverConfig();
    config
        .getPhaseConfigList()
        .getLast()
        .setTerminationConfig(new TerminationConfig().withBestScoreLimit("0hard/0soft"));
    assertThatThrownBy(
            () -> StudyProtocol.apply(definition, config, null, null, 11, Duration.ofSeconds(1)))
        .isInstanceOf(StudyProtocolException.class)
        .hasMessageContaining("hidden termination");
    var workers = definition.baseSolverConfig();
    ((LocalSearchPhaseConfig) workers.getPhaseConfigList().getLast()).setMoveThreadCount("8");
    assertThatThrownBy(
            () -> StudyProtocol.apply(definition, workers, null, null, 11, Duration.ofSeconds(1)))
        .isInstanceOf(StudyProtocolException.class)
        .hasMessageContaining("fixed resources");
  }

  private DefaultGreybayStudy<SchedulingExample.Schedule, HardSoftScore> open(
      GreybayStudyDefinition<SchedulingExample.Schedule, HardSoftScore> definition,
      EvaluationRunner<SchedulingExample.Schedule, HardSoftScore> runner,
      AtomicLong clock,
      boolean resume) {
    return DefaultGreybayStudy.open(
        directory.resolve("study"), definition, definition.runLimits(), resume, runner, clock::get);
  }

  private static GreybayStudyDefinition<SchedulingExample.Schedule, HardSoftScore> definition(
      long trials) {
    return builder(trials).build();
  }

  private static GreybayStudyDefinition.Builder<SchedulingExample.Schedule, HardSoftScore> builder(
      long trials) {
    return GreybayStudyDefinition.<SchedulingExample.Schedule, HardSoftScore>builder()
        .baseSolverConfig(SchedulingExample.baseSolverConfig())
        .problemAdapter(SchedulingExample.problemAdapter())
        .templates(List.of(new HistoryTemplate()))
        .sampler(new RandomSamplerConfig())
        .optimizerSeed(17)
        .objective(new BalancedQualityObjective<>())
        .trainingPanel(new ProblemPanel(List.of("train-small", "train-medium"), List.of(11L, 29L)))
        .heldOutPanel(new ProblemPanel(List.of("heldout-small"), List.of(101L)))
        .solveDuration(Duration.ofSeconds(1))
        .warmupDuration(Duration.ZERO)
        .maxTrials(trials)
        .activeTimeLimit(Duration.ofHours(1));
  }

  private static EvaluationRunner<SchedulingExample.Schedule, HardSoftScore> runner(
      AtomicLong clock,
      AtomicInteger calls,
      BiFunction<SolverConfig, String, HardSoftScore> score) {
    return (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
      calls.incrementAndGet();
      clock.addAndGet(1_000_000);
      assertThat(config.getRandomSeed()).isEqualTo(seed);
      assertThat(config.getTerminationConfig().getSpentLimit()).isEqualTo(horizon);
      return complete(score.apply(config, descriptor.id()));
    };
  }

  private static EvaluationOutcome<HardSoftScore> complete(HardSoftScore score) {
    return new EvaluationOutcome<>(
        EvaluationStatus.COMPLETE,
        score,
        0,
        1,
        0,
        1_000_000,
        0,
        0,
        VerificationResult.passed(),
        null,
        null,
        List.of());
  }

  private static int history(SolverConfig config) {
    var phase = (LocalSearchPhaseConfig) config.getPhaseConfigList().getLast();
    return phase.getAcceptorConfig() == null
        ? 0
        : phase.getAcceptorConfig().getLateAcceptanceSize();
  }

  private static final class HistoryTemplate implements ConfigurationTemplate {
    @Override
    public ComponentIdentity identity() {
      return new ComponentIdentity("history", "1");
    }

    @Override
    public SearchSpace searchSpace() {
      return new SearchSpace("1", List.of(new IntegerDimension("history", 2, 3, 1)));
    }

    @Override
    public ParameterAssignment defaultAssignment() {
      return ParameterAssignment.of(Map.of("history", 2L));
    }

    @Override
    public void validate(ProblemCapabilities capabilities) {}

    @Override
    public SolverConfig materialize(SolverConfig base, ParameterAssignment assignment) {
      var phases = new ArrayList<>(base.getPhaseConfigList());
      phases.set(
          phases.size() - 1,
          new LocalSearchPhaseConfig()
              .withAcceptorConfig(
                  new LocalSearchAcceptorConfig()
                      .withLateAcceptanceSize((int) assignment.getLong("history")))
              .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1)));
      base.setPhaseConfigList(phases);
      return base;
    }
  }

  private static final class RecordingSampler implements Sampler {
    private final Sampler delegate = new RandomSamplerConfig().buildSampler();
    private List<SamplingObservation> history = List.of();

    @Override
    public ComponentIdentity identity() {
      return new ComponentIdentity("recording-sampler", "1");
    }

    @Override
    public SamplerState initialState(long seed) {
      return delegate.initialState(seed);
    }

    @Override
    public CandidateProposal propose(SamplingContext context, SamplerState state) {
      history = context.history();
      return delegate.propose(context, state);
    }
  }
}
