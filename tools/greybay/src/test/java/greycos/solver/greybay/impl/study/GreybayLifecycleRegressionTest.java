package greycos.solver.greybay.impl.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.ConfigurationTemplate;
import greycos.solver.greybay.api.EvaluationStatus;
import greycos.solver.greybay.api.ProblemAdapter;
import greycos.solver.greybay.api.ProblemCapabilities;
import greycos.solver.greybay.api.ProblemDescriptor;
import greycos.solver.greybay.api.StudyState;
import greycos.solver.greybay.api.TrialStatus;
import greycos.solver.greybay.api.VerificationResult;
import greycos.solver.greybay.api.objective.BalancedQualityObjective;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.IntegerDimension;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.config.RandomSamplerConfig;
import greycos.solver.greybay.config.SearchSpace;
import greycos.solver.greybay.impl.evaluation.EvaluationOutcome;
import greycos.solver.greybay.impl.persistence.JournalRecord;
import greycos.solver.greybay.impl.persistence.StudyStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class GreybayLifecycleRegressionTest {
  private static final long CELL_COST = Duration.ofMillis(900).toNanos();
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void recoversCommittedCellCostAndMissingAggregateBeforeAnyHeartbeat(boolean failedCandidate)
      throws Exception {
    var definition = definition(1, List.of(11L));
    var source = directory.resolve("source");
    var clock = new AtomicLong();
    try (var study =
        open(source, definition, false, costingEvaluator(clock, failedCandidate), clock)) {
      study.run();
    }
    var crashed = directory.resolve("crashed");
    copyCommittedPrefix(
        source,
        crashed,
        definition,
        record -> record.kind().equals("EVALUATION_COMPLETED") && candidateCell(record, source));
    var resumedCalls = new AtomicInteger();
    EvaluationRunner<TestdataSolution, SimpleScore> mustNotRepeat =
        (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
          resumedCalls.incrementAndGet();
          throw new AssertionError("A committed terminal cell must never be rerun.");
        };
    var resumedClock = new AtomicLong();
    try (var resumed = open(crashed, definition, true, mustNotRepeat, resumedClock)) {
      var snapshot = resumed.snapshot();
      assertThat(snapshot.measuredActiveNanos()).isEqualTo(3 * CELL_COST);
      assertThat(snapshot.admissionChargedNanos()).isEqualTo(3 * CELL_COST);
      assertThat(snapshot.interruptedCostUnknown()).isTrue();
      assertThat(snapshot.trials()).hasSize(2);
      assertThat(snapshot.trials().get(1).status())
          .isEqualTo(failedCandidate ? TrialStatus.FAILED : TrialStatus.COMPLETE);
      resumed.run();
      assertThat(resumedCalls).hasValue(0);
    }
    // A second process must not charge the recovered operation or append its aggregate again.
    try (var resumed = open(crashed, definition, true, mustNotRepeat, new AtomicLong())) {
      assertThat(resumed.snapshot().measuredActiveNanos()).isEqualTo(3 * CELL_COST);
      assertThat(resumed.snapshot().admissionChargedNanos()).isEqualTo(3 * CELL_COST);
      assertThat(resumed.snapshot().trials().get(1).evaluations()).hasSize(1);
    }
    var manifest = StudyManifest.prepare(definition).value();
    try (var store = StudyStore.resume(crashed, manifest)) {
      assertThat(
              store.records().stream()
                  .filter(
                      record ->
                          record.kind().equals("TRIAL_TERMINAL")
                              && "1".equals(record.payload().get("trialId"))))
          .hasSize(1);
      assertThat(
              store.records().stream().filter(record -> record.kind().equals("ACTIVE_INTERRUPTED")))
          .hasSize(1);
    }
  }

  @Test
  void thirdCommittedFailureStopsRecoveryBeforeAnotherProposalOrEvaluation() throws Exception {
    var definition = definition(4, List.of(11L));
    var source = directory.resolve("source");
    var clock = new AtomicLong();
    try (var study = open(source, definition, false, costingEvaluator(clock, true), clock)) {
      assertThat(study.run().state()).isEqualTo(StudyState.FAILED);
    }
    var crashed = directory.resolve("crashed");
    copyCommittedPrefix(
        source,
        crashed,
        definition,
        record ->
            record.kind().equals("TRIAL_TERMINAL") && "3".equals(record.payload().get("trialId")));
    var calls = new AtomicInteger();
    try (var resumed =
        open(
            crashed,
            definition,
            true,
            (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
              calls.incrementAndGet();
              throw new AssertionError(
                  "Three committed candidate failures must prevent further evaluation.");
            },
            new AtomicLong())) {
      var result = resumed.run();
      assertThat(result.state()).isEqualTo(StudyState.FAILED);
      assertThat(result.trials()).hasSize(4);
      assertThat(calls).hasValue(0);
    }
    try (var store = StudyStore.resume(crashed, StudyManifest.prepare(definition).value())) {
      assertThat(store.records().stream().filter(record -> record.kind().equals("PROPOSED")))
          .hasSize(3);
    }
  }

  @Test
  void terminalQualificationRestoresFinishedStateWithoutASeparateStateCommit() throws Exception {
    var definition = definition(0, List.of(11L));
    var source = directory.resolve("source");
    var clock = new AtomicLong();
    try (var study = open(source, definition, false, costingEvaluator(clock, false), clock)) {
      study.run();
      study.qualify();
      assertThat(study.snapshot().state()).isEqualTo(StudyState.FINISHED);
    }
    var crashed = directory.resolve("crashed");
    copyCommittedPrefix(
        source,
        crashed,
        definition,
        record ->
            record.kind().equals("QUALIFICATION")
                && Boolean.TRUE.equals(record.payload().get("terminal")));
    try (var resumed =
        open(
            crashed,
            definition,
            true,
            (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
              throw new AssertionError("A terminal qualification must not run more evaluations.");
            },
            new AtomicLong())) {
      assertThat(resumed.snapshot().state()).isEqualTo(StudyState.FINISHED);
      assertThat(resumed.qualify().evaluations()).hasSize(1);
      assertThat(resumed.snapshot().frozenSelectionTrialId()).isZero();
    }
  }

  @Test
  void stopBetweenCellsRetainsCommittedEvidenceAndExplicitResumeRunsOnlyTheMissingCell() {
    var definition = definition(0, List.of(11L, 12L));
    var studyPath = directory.resolve("study");
    var holder = new AtomicReference<DefaultGreybayStudy<TestdataSolution, SimpleScore>>();
    var seeds = new ArrayList<Long>();
    var clock = new AtomicLong();
    EvaluationRunner<TestdataSolution, SimpleScore> stopAfterFirstTrainingCell =
        (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
          seeds.add(seed);
          clock.addAndGet(CELL_COST);
          if (seed == 11L) {
            holder.get().requestStop();
            assertThat(stop.getAsBoolean()).isTrue();
          }
          return successfulOutcome();
        };
    try (var study = open(studyPath, definition, false, stopAfterFirstTrainingCell, clock)) {
      holder.set(study);
      var stopped = study.run();
      assertThat(stopped.state()).isEqualTo(StudyState.PAUSED);
      assertThat(seeds).containsExactly(definition.preflightSeed(), 11L);
      assertThat(stopped.trials().getFirst().evaluations()).hasSize(1);
      assertThat(stopped.trials().getFirst().status()).isEqualTo(TrialStatus.INTERRUPTED);
    }
    var resumedSeeds = new ArrayList<Long>();
    try (var resumed =
        open(
            studyPath,
            definition,
            true,
            (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
              resumedSeeds.add(seed);
              return successfulOutcome();
            },
            new AtomicLong())) {
      var result = resumed.run();
      assertThat(result.state()).isEqualTo(StudyState.SEARCH_FINISHED);
      assertThat(resumedSeeds).containsExactly(12L);
      assertThat(result.trials().getFirst().evaluations()).hasSize(2);
      assertThat(result.trials().getFirst().status()).isEqualTo(TrialStatus.COMPLETE);
    }
  }

  @Test
  void closeAwaitsHeartbeatWithoutHoldingItsMonitorAndRetainsInterruptStatus() throws Exception {
    var definition = definition(0, List.of(11L));
    var studyPath = directory.resolve("study");
    var study =
        open(
            studyPath,
            definition,
            false,
            costingEvaluator(new AtomicLong(), false),
            new AtomicLong());
    Object lifecycle = field(study, "lifecycle");
    var original = (ScheduledExecutorService) field(study, "heartbeatExecutor");
    original.shutdown();
    assertThat(original.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    var heartbeatCompleted = new AtomicBoolean();
    var replacement =
        new ScheduledThreadPoolExecutor(1) {
          @Override
          public void shutdown() {
            execute(
                () -> {
                  synchronized (lifecycle) {
                    heartbeatCompleted.set(true);
                  }
                });
            super.shutdown();
          }

          @Override
          public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            assertThat(Thread.holdsLock(lifecycle))
                .as("A heartbeat may need the lifecycle monitor to exit")
                .isFalse();
            return super.awaitTermination(timeout, unit);
          }
        };
    setField(study, "heartbeatExecutor", replacement);
    Thread.currentThread().interrupt();
    try {
      study.close();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(heartbeatCompleted).isTrue();
      assertThat(replacement.isTerminated()).isTrue();
    } finally {
      Thread.interrupted();
      replacement.shutdownNow();
      assertThat(replacement.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
      ((StudyStore) field(study, "store")).close();
    }
    // Reacquiring the lock is permitted only after the heartbeat resource has ended.
    try (var reopened = StudyStore.resume(studyPath, StudyManifest.prepare(definition).value())) {
      assertThat(reopened.records()).isNotEmpty();
    }
  }

  private static DefaultGreybayStudy<TestdataSolution, SimpleScore> open(
      Path path,
      GreybayStudyDefinition<TestdataSolution, SimpleScore> definition,
      boolean resume,
      EvaluationRunner<TestdataSolution, SimpleScore> evaluator,
      AtomicLong clock) {
    return DefaultGreybayStudy.open(
        path, definition, definition.runLimits(), resume, evaluator, clock::get);
  }

  private static EvaluationRunner<TestdataSolution, SimpleScore> costingEvaluator(
      AtomicLong clock, boolean failCandidates) {
    return (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
      clock.addAndGet(CELL_COST);
      boolean candidate =
          ((LocalSearchPhaseConfig) config.getPhaseConfigList().getLast()).getAcceptorConfig()
              != null;
      if (failCandidates && candidate && seed != Long.MIN_VALUE) {
        return new EvaluationOutcome<>(
            EvaluationStatus.FAILED,
            null,
            -1,
            -1,
            -1,
            CELL_COST,
            0,
            0,
            VerificationResult.notConfigured(),
            IllegalArgumentException.class.getName(),
            "candidate failure",
            List.of());
      }
      return successfulOutcome();
    };
  }

  private static EvaluationOutcome<SimpleScore> successfulOutcome() {
    return new EvaluationOutcome<>(
        EvaluationStatus.COMPLETE,
        SimpleScore.ZERO,
        0,
        0,
        0,
        CELL_COST,
        0,
        0,
        VerificationResult.notConfigured(),
        null,
        null,
        List.of());
  }

  private static GreybayStudyDefinition<TestdataSolution, SimpleScore> definition(
      long maximumTrials, List<Long> trainingSeeds) {
    var base =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(TestdataConstraintProvider.class)
            .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig().withLocalSearchType(LocalSearchType.HILL_CLIMBING));
    return GreybayStudyDefinition.<TestdataSolution, SimpleScore>builder()
        .baseSolverConfig(base)
        .problemAdapter(new Adapter())
        .trainingPanel(new ProblemPanel(List.of("training"), trainingSeeds))
        .heldOutPanel(new ProblemPanel(List.of("held-out"), List.of(21L)))
        .templates(List.of(new Template()))
        .objective(new BalancedQualityObjective<>())
        .sampler(new RandomSamplerConfig().buildSampler())
        .optimizerSeed(37)
        .solveDuration(Duration.ofSeconds(1))
        .warmupDuration(Duration.ZERO)
        .maxTrials(maximumTrials)
        .activeTimeLimit(Duration.ofMinutes(2))
        .build();
  }

  private static final class Adapter implements ProblemAdapter<TestdataSolution> {
    @Override
    public ComponentIdentity identity() {
      return new ComponentIdentity("lifecycle-adapter", "1", "test-artifact-v1");
    }

    @Override
    public ProblemDescriptor describe(String id) {
      return new ProblemDescriptor(
          id,
          "content-" + id,
          new ProblemCapabilities(
              true, false, false, false, SimpleScore.class.getName(), List.of("score"), Set.of()));
    }

    @Override
    public TestdataSolution loadFresh(String id) {
      return TestdataSolution.generateSolution(2, 2);
    }
  }

  private static final class Template implements ConfigurationTemplate {
    @Override
    public ComponentIdentity identity() {
      return new ComponentIdentity("lifecycle-template", "1", "test-artifact-v1");
    }

    @Override
    public SearchSpace searchSpace() {
      return new SearchSpace(List.of(new IntegerDimension("history", 10, 15, 1)));
    }

    @Override
    public ParameterAssignment defaultAssignment() {
      return ParameterAssignment.of(Map.of("history", 10));
    }

    @Override
    public void validate(ProblemCapabilities capabilities) {}

    @Override
    public SolverConfig materialize(SolverConfig base, ParameterAssignment assignment) {
      return base.withPhases(
          base.getPhaseConfigList().getFirst(),
          new LocalSearchPhaseConfig()
              .withAcceptorConfig(
                  new LocalSearchAcceptorConfig()
                      .withLateAcceptanceSize(Math.toIntExact(assignment.getLong("history")))));
    }
  }

  private static boolean candidateCell(JournalRecord record, Path source) {
    var observation = reference(record.payload().get("observation"));
    try {
      var payload =
          greycos.solver.greybay.impl.persistence.StoreJson.readObject(
              Files.readAllBytes(source.resolve("artifacts").resolve(observation.fileName())));
      return "TRAINING".equals(payload.get("stage")) && "1".equals(payload.get("trialId"));
    } catch (IOException failure) {
      throw new java.io.UncheckedIOException(failure);
    }
  }

  /**
   * Reconstructs a valid committed journal prefix, exactly the state visible after process loss.
   */
  private static void copyCommittedPrefix(
      Path source,
      Path target,
      GreybayStudyDefinition<?, ?> definition,
      Predicate<JournalRecord> stopAfter)
      throws IOException {
    var manifest = StudyManifest.prepare(definition).value();
    try (var original = StudyStore.resume(source, manifest);
        var crashed = StudyStore.create(target, manifest)) {
      try (var artifacts = Files.list(source.resolve("artifacts"))) {
        for (var artifact : artifacts.toList()) {
          Files.copy(artifact, target.resolve("artifacts").resolve(artifact.getFileName()));
        }
      }
      boolean found = false;
      for (var record : original.records()) {
        // Model a crash before the first heartbeat; regenerate the valid hash chain through the
        // store.
        if (!record.kind().equals("ACTIVE_HEARTBEAT"))
          crashed.append(record.kind(), record.payload());
        if (stopAfter.test(record)) {
          found = true;
          break;
        }
      }
      assertThat(found).as("The test must reach its intended crash boundary").isTrue();
    }
  }

  @SuppressWarnings("unchecked")
  private static greycos.solver.greybay.impl.persistence.ArtifactReference reference(Object value) {
    var map = (Map<String, Object>) value;
    return new greycos.solver.greybay.impl.persistence.ArtifactReference(
        (String) map.get("digest"), (String) map.get("suffix"));
  }

  private static Object field(Object object, String name) throws Exception {
    var field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(object);
  }

  private static void setField(Object object, String name, Object value) throws Exception {
    var field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(object, value);
  }
}
