package greycos.solver.greybay.impl.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
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
import greycos.solver.greybay.api.sampling.CandidateProposal;
import greycos.solver.greybay.api.sampling.ProposalReason;
import greycos.solver.greybay.api.sampling.Sampler;
import greycos.solver.greybay.api.sampling.SamplerState;
import greycos.solver.greybay.api.sampling.SamplingContext;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.IntegerDimension;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.config.SearchSpace;
import greycos.solver.greybay.impl.evaluation.EvaluationOutcome;
import greycos.solver.greybay.impl.persistence.JournalRecord;
import greycos.solver.greybay.impl.persistence.StudyStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(60)
class GreybayCheckpointRegressionTest {
  private static final long CHECKPOINT_COST = Duration.ofMillis(7).toNanos();
  @TempDir Path directory;

  @Test
  void doublingCandidatesKeepsJournalGrowthLinearAndCheckpointCountLogarithmic() throws Exception {
    var smaller = runGrowthStudy(100);
    var larger = runGrowthStudy(200);

    assertThat(larger.journalBytes()).isGreaterThan(smaller.journalBytes());
    assertThat((double) larger.journalBytes() / smaller.journalBytes()).isLessThan(2.5);
    assertThat(larger.checkpointCount()).isEqualTo(smaller.checkpointCount() + 1);
  }

  @Test
  void proposalWithoutAnIntermediateCacheRestoresReservationAndSamplerPosition() throws Exception {
    var definition = definition(3);
    var source = directory.resolve("source");
    List<Map<String, Object>> proposals;
    try (var study = open(source, definition, false, successfulEvaluator(), () -> 0)) {
      assertThat(study.run().state()).isEqualTo(StudyState.SEARCH_FINISHED);
      proposals = proposalPayloads(store(study));
    }
    var crashed = directory.resolve("crashed");
    copyCommittedPrefix(
        source,
        crashed,
        definition,
        record -> record.kind().equals("PROPOSED") && "2".equals(record.payload().get("trialId")));
    assertThat(Files.exists(crashed.resolve("checkpoint.json"))).isFalse();
    var evaluatedHistories = new ArrayList<Integer>();
    try (var resumed =
        open(
            crashed,
            definition,
            true,
            (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
              evaluatedHistories.add(history(config));
              return successfulOutcome(config);
            },
            () -> 0)) {
      var snapshot = resumed.snapshot();
      assertThat(snapshot.trials()).hasSize(3);
      assertThat(snapshot.trials().get(1).status()).isEqualTo(TrialStatus.COMPLETE);
      assertThat(snapshot.trials().get(2).status()).isEqualTo(TrialStatus.INTERRUPTED);
      assertThat(snapshot.trials().get(2).candidateIdentity())
          .isEqualTo(proposals.get(1).get("candidateIdentity"));
      var rebuiltCache = store(resumed).checkpoint().orElseThrow().state();
      assertThat(rebuiltCache)
          .containsEntry("candidateCount", "2")
          .containsEntry("samplerState", proposals.get(1).get("samplerState"));
      assertThat((List<?>) rebuiltCache.get("reservedIdentities"))
          .anySatisfy(
              identity ->
                  assertThat(identity).isEqualTo(proposals.get(1).get("candidateIdentity")));

      assertThat(resumed.run().state()).isEqualTo(StudyState.SEARCH_FINISHED);
      assertThat(evaluatedHistories).containsExactly(12, 13);
      assertThat(proposalPayloads(store(resumed))).isEqualTo(proposals);
    }
  }

  @Test
  void frozenSelectionWithoutAnIntermediateCacheResumesOnlyHeldOutEvaluation() throws Exception {
    var definition = definition(3);
    var source = directory.resolve("source");
    try (var study = open(source, definition, false, successfulEvaluator(), () -> 0)) {
      study.run();
      assertThat(study.qualify().selectedTrialId()).isEqualTo(3);
    }
    var crashed = directory.resolve("crashed");
    copyCommittedPrefix(
        source, crashed, definition, record -> record.kind().equals("SELECTION_FROZEN"));
    assertThat(Files.exists(crashed.resolve("checkpoint.json"))).isFalse();
    var evaluatedHistories = new ArrayList<Integer>();
    try (var resumed =
        open(
            crashed,
            definition,
            true,
            (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
              assertThat(descriptor.id()).isEqualTo("held-out");
              assertThat(seed).isEqualTo(21L);
              evaluatedHistories.add(history(config));
              return successfulOutcome(config);
            },
            () -> 0)) {
      assertThat(resumed.snapshot().frozenSelectionTrialId()).isEqualTo(3);
      assertThat(store(resumed).checkpoint().orElseThrow().state())
          .containsEntry("frozenSelectionTrialId", "3")
          .containsEntry("qualificationTerminal", false);
      assertThatThrownBy(resumed::run)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("selection is frozen");

      assertThat(resumed.qualify().selectedTrialId()).isEqualTo(3);
      assertThat(resumed.snapshot().state()).isEqualTo(StudyState.FINISHED);
      assertThat(evaluatedHistories).containsExactly(0, 13);
      assertThat(
              store(resumed).records().stream()
                  .filter(record -> record.kind().equals("SELECTION_FROZEN")))
          .hasSize(1);
    }
  }

  @Test
  void checkpointCostIsIncludedInTheOperationAndSurvivesRecovery() throws Exception {
    var definition = definition(0);
    var path = directory.resolve("study");
    var clock = new CheckpointClock();
    long charged;
    try (var study = open(path, definition, false, successfulEvaluator(), clock)) {
      clock.attach(store(study));
      var result = study.run();
      assertThat(result.state()).isEqualTo(StudyState.SEARCH_FINISHED);
      assertThat(clock.chargedCheckpoints()).isEqualTo(2);
      charged = clock.chargedCheckpoints() * CHECKPOINT_COST;
      assertThat(result.measuredActiveNanos()).isEqualTo(charged);
      assertThat(result.admissionChargedNanos()).isEqualTo(charged);
      assertCompletedAfterCheckpoint(store(study), charged);
    }
    try (var resumed = open(path, definition, true, successfulEvaluator(), () -> 0)) {
      assertThat(resumed.snapshot().measuredActiveNanos()).isEqualTo(charged);
      assertThat(resumed.snapshot().admissionChargedNanos()).isEqualTo(charged);
      assertThat(resumed.snapshot().interruptedCostUnknown()).isFalse();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedCacheWriteStillCommitsAccountingAndPreservesTheOriginalFailure(boolean failEvaluation)
      throws Exception {
    var definition = definition(0);
    var path = directory.resolve("study");
    var clock = new CheckpointClock();
    var primaryFailure = new IllegalArgumentException("Synthetic evaluation failure.");
    EvaluationRunner<TestdataSolution, SimpleScore> evaluator =
        (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
          throw primaryFailure;
        };
    try (var study = open(path, definition, false, evaluator, clock)) {
      clock.attach(store(study));
      // Only cache replacement fails; the journal and its accounting commits remain writable.
      Files.delete(path.resolve("checkpoint.json"));
      Files.createDirectory(path.resolve("checkpoint.json"));
      if (!failEvaluation) study.requestStop();

      var failure = catchThrowable(study::run);
      if (failEvaluation) {
        assertThat(failure).isSameAs(primaryFailure);
        assertThat(failure.getSuppressed())
            .singleElement()
            .isInstanceOf(UncheckedIOException.class);
      } else {
        assertThat(failure).isInstanceOf(UncheckedIOException.class);
      }
      assertThat(clock.chargedCheckpoints()).isEqualTo(1);
      assertCompletedAfterCheckpoint(store(study), CHECKPOINT_COST);
      assertThat(study.snapshot().measuredActiveNanos()).isEqualTo(CHECKPOINT_COST);
      assertThat(study.snapshot().admissionChargedNanos()).isEqualTo(CHECKPOINT_COST);
      assertThat(study.snapshot().state()).isEqualTo(StudyState.FAILED);
    }
  }

  private JournalSize runGrowthStudy(int candidates) throws Exception {
    var definition = definition(candidates);
    var path = directory.resolve("growth-" + candidates);
    try (var study = open(path, definition, false, successfulEvaluator(), () -> 0)) {
      var result = study.run();
      assertThat(result.state()).isEqualTo(StudyState.SEARCH_FINISHED);
      assertThat(result.trials()).hasSize(candidates + 1);
      var records = store(study).records();
      assertThat(records).noneMatch(record -> record.kind().equals(StudyStore.CHECKPOINT_KIND));
      var checkpoints =
          records.stream()
              .filter(record -> record.kind().equals(StudyStore.CHECKPOINT_MARKER_KIND))
              .toList();
      // One setup boundary, powers of two completed trials including baseline, one run boundary.
      int expectedCount = Long.SIZE - Long.numberOfLeadingZeros(candidates + 1L) + 2;
      assertThat(checkpoints)
          .hasSize(expectedCount)
          .allSatisfy(
              checkpoint -> {
                assertThat(checkpoint.payload()).containsOnlyKeys("stateDigest");
                assertThat((String) checkpoint.payload().get("stateDigest"))
                    .matches("sha256:[0-9a-f]{64}");
              });
      return new JournalSize(Files.size(path.resolve("journal.gb")), checkpoints.size());
    }
  }

  private static void assertCompletedAfterCheckpoint(StudyStore store, long elapsed) {
    var records = store.records();
    var completed = records.getLast();
    assertThat(completed.kind()).isEqualTo("ACTIVE_COMPLETED");
    assertThat(completed.payload())
        .containsEntry("elapsedNanos", Long.toString(elapsed))
        .containsEntry("admissionNanos", Long.toString(elapsed))
        .containsEntry("unknownCost", false);
    assertThat(records.get(records.size() - 2).kind()).isEqualTo(StudyStore.CHECKPOINT_MARKER_KIND);
  }

  private static DefaultGreybayStudy<TestdataSolution, SimpleScore> open(
      Path path,
      GreybayStudyDefinition<TestdataSolution, SimpleScore> definition,
      boolean resume,
      EvaluationRunner<TestdataSolution, SimpleScore> evaluator,
      LongSupplier clock)
      throws Exception {
    var study =
        DefaultGreybayStudy.open(
            path, definition, definition.runLimits(), resume, evaluator, clock);
    var field = DefaultGreybayStudy.class.getDeclaredField("heartbeatExecutor");
    field.setAccessible(true);
    var executor = (ScheduledExecutorService) field.get(study);
    executor.shutdown();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    return study;
  }

  private static StudyStore store(DefaultGreybayStudy<?, ?> study) throws Exception {
    var field = DefaultGreybayStudy.class.getDeclaredField("store");
    field.setAccessible(true);
    return (StudyStore) field.get(study);
  }

  private static List<Map<String, Object>> proposalPayloads(StudyStore store) {
    return store.records().stream()
        .filter(record -> record.kind().equals("PROPOSED"))
        .map(JournalRecord::payload)
        .toList();
  }

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
        crashed.append(record.kind(), record.payload());
        if (stopAfter.test(record)) {
          found = true;
          break;
        }
      }
      assertThat(found).as("The committed prefix must reach the requested crash boundary").isTrue();
      assertThat(crashed.checkpoint()).isEmpty();
    }
  }

  private static EvaluationRunner<TestdataSolution, SimpleScore> successfulEvaluator() {
    return (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) ->
        successfulOutcome(config);
  }

  private static EvaluationOutcome<SimpleScore> successfulOutcome(SolverConfig config) {
    return new EvaluationOutcome<>(
        EvaluationStatus.COMPLETE,
        SimpleScore.of(history(config)),
        0,
        0,
        0,
        0,
        0,
        0,
        VerificationResult.notConfigured(),
        null,
        null,
        List.of());
  }

  private static int history(SolverConfig config) {
    var acceptor =
        ((LocalSearchPhaseConfig) config.getPhaseConfigList().getLast()).getAcceptorConfig();
    return acceptor == null ? 0 : acceptor.getLateAcceptanceSize();
  }

  private static GreybayStudyDefinition<TestdataSolution, SimpleScore> definition(long candidates) {
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
        .trainingPanel(new ProblemPanel(List.of("training"), List.of(11L)))
        .heldOutPanel(new ProblemPanel(List.of("held-out"), List.of(21L)))
        .templates(List.of(new Template()))
        .objective(new BalancedQualityObjective<>())
        .sampler(new SequentialSampler())
        .optimizerSeed(37)
        .solveDuration(Duration.ofSeconds(1))
        .warmupDuration(Duration.ZERO)
        .maxTrials(candidates)
        .activeTimeLimit(Duration.ofHours(1))
        .build();
  }

  private record JournalSize(long journalBytes, int checkpointCount) {}

  private static final class CheckpointClock implements LongSupplier {
    private StudyStore store;
    private long initialCheckpoints;
    private long chargedCheckpoints;

    private void attach(StudyStore store) {
      this.store = store;
      initialCheckpoints = checkpointCount();
    }

    private long checkpointCount() {
      return store.records().stream()
          .filter(record -> record.kind().equals(StudyStore.CHECKPOINT_MARKER_KIND))
          .count();
    }

    @Override
    public long getAsLong() {
      // Charge a deterministic write cost once the checkpoint marker has been committed.
      // The next elapsed-time sample must include it, even if the cache replacement failed.
      if (store != null) chargedCheckpoints = checkpointCount() - initialCheckpoints;
      return chargedCheckpoints * CHECKPOINT_COST;
    }

    private long chargedCheckpoints() {
      return chargedCheckpoints;
    }
  }

  private static final class Adapter implements ProblemAdapter<TestdataSolution> {
    @Override
    public ComponentIdentity identity() {
      return new ComponentIdentity("checkpoint-adapter", "1", "test-artifact-v1");
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
      return new ComponentIdentity("checkpoint-template", "1", "test-artifact-v1");
    }

    @Override
    public SearchSpace searchSpace() {
      return new SearchSpace(List.of(new IntegerDimension("history", 10, 10_000, 1)));
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

  private static final class SequentialSampler implements Sampler {
    @Override
    public ComponentIdentity identity() {
      return new ComponentIdentity("checkpoint-sequential", "1", "test-artifact-v1");
    }

    @Override
    public SamplerState initialState(long seed) {
      return new SamplerState("1", 0, 0, 0, 0, 0, Set.of());
    }

    @Override
    public CandidateProposal propose(SamplingContext context, SamplerState state) {
      long sequence = state.requestSequence() + 1;
      var assignment =
          ParameterAssignment.of(
              Map.of("template", "checkpoint-template", "history", sequence + 10));
      return new CandidateProposal(
          assignment,
          context.identityResolver().apply(assignment),
          sequence,
          ProposalReason.STARTUP,
          null,
          0,
          0,
          new SamplerState("1", 0, sequence, 0, 0, sequence, Set.of()));
    }
  }
}
