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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

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

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class GreybayHeartbeatRegressionTest {
  private static final long CLOCK_ORIGIN = 1_000_000_000_000_000L;
  private static final long CELL_COST = Duration.ofMillis(100).toNanos();
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3})
  void heartbeatAtEvaluationPublicationCannotChargeTheClockOriginOnRecovery(int crashBoundary)
      throws Exception {
    var definition = definition();
    var source = directory.resolve("source");
    var current = new AtomicReference<DefaultGreybayStudy<TestdataSolution, SimpleScore>>();
    var store = new AtomicReference<StudyStore>();
    var elapsed = new AtomicLong(CLOCK_ORIGIN);
    var starts = new AtomicInteger();
    var seenStart = new AtomicLong();
    var crashSequence = new AtomicLong();
    long reservation =
        definition.solveDuration().plus(definition.protocol().graceDuration()).toNanos();
    LongSupplier clock =
        () -> {
          if (current.get() != null) {
            var last = store.get().records().getLast();
            if (last.kind().equals("EVALUATION_STARTED")
                && seenStart.getAndSet(last.sequence()) != last.sequence()) {
              // This is the clock sample used to construct the active evaluation, after its
              // durable STARTED record but before publication. Reentering the heartbeat here
              // models the exact interleaving without relying on scheduler timing.
              heartbeat(current.get());
              var boundary = store.get().records().getLast();
              assertThat(boundary.kind()).isEqualTo("ACTIVE_HEARTBEAT");
              assertThat(boundary.payload())
                  .containsEntry("evaluationId", null)
                  .containsEntry("evaluationElapsedNanos", "0")
                  .containsEntry("reservedNanos", "0")
                  .containsEntry("elapsedNanos", Long.toString(elapsed.get() - CLOCK_ORIGIN));
              assertThat(current.get().snapshot().activeEvaluationId()).isNull();
              if (starts.incrementAndGet() == crashBoundary) crashSequence.set(boundary.sequence());
            }
          }
          return elapsed.get();
        };
    EvaluationRunner<TestdataSolution, SimpleScore> evaluator =
        (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
          String evaluationId = current.get().snapshot().activeEvaluationId();
          assertThat(evaluationId).isNotNull();
          elapsed.addAndGet(CELL_COST);
          heartbeat(current.get());
          assertThat(store.get().records().getLast().payload())
              .containsEntry("evaluationId", evaluationId)
              .containsEntry("evaluationElapsedNanos", Long.toString(CELL_COST))
              .containsEntry("reservedNanos", Long.toString(reservation));
          return successfulOutcome();
        };
    try (var study =
        DefaultGreybayStudy.open(
            source, definition, definition.runLimits(), false, evaluator, clock)) {
      disableScheduledHeartbeat(study);
      store.set((StudyStore) field(study, "store"));
      current.set(study);
      assertThat(study.run().state()).isEqualTo(StudyState.SEARCH_FINISHED);
      assertThat(starts).hasValue(3);
      assertThat(crashSequence.get()).isPositive();
      assertThat(study.snapshot().activeEvaluationId()).isNull();
      assertThat(study.snapshot().measuredActiveNanos()).isEqualTo(3 * CELL_COST);
    }
    current.set(null);
    var crashed = directory.resolve("crashed");
    copyCommittedPrefix(source, crashed, definition, crashSequence.get());
    var resumedClock = new AtomicLong(CLOCK_ORIGIN);
    var resumedSeeds = new ArrayList<Long>();
    EvaluationRunner<TestdataSolution, SimpleScore> resumedEvaluator =
        (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
          resumedSeeds.add(seed);
          resumedClock.addAndGet(CELL_COST);
          return successfulOutcome();
        };
    try (var resumed =
        DefaultGreybayStudy.open(
            crashed,
            definition,
            definition.runLimits(),
            true,
            resumedEvaluator,
            resumedClock::get)) {
      disableScheduledHeartbeat(resumed);
      long completedCost = (crashBoundary - 1L) * CELL_COST;
      assertThat(resumed.snapshot().measuredActiveNanos()).isEqualTo(completedCost);
      assertThat(resumed.snapshot().admissionChargedNanos()).isEqualTo(completedCost + reservation);
      assertThat(resumed.snapshot().interruptedCostUnknown()).isTrue();
      assertThat(resumed.snapshot().activeEvaluationId()).isNull();
      assertThat(resumed.run().state()).isEqualTo(StudyState.SEARCH_FINISHED);
      assertThat(resumedSeeds)
          .containsExactlyElementsOf(
              List.of(definition.preflightSeed(), 11L, 12L).subList(crashBoundary - 1, 3));
      assertThat(resumed.snapshot().activeEvaluationId()).isNull();
      assertThat(resumed.snapshot().measuredActiveNanos()).isEqualTo(3 * CELL_COST);
      assertThat(resumed.snapshot().admissionChargedNanos()).isEqualTo(3 * CELL_COST + reservation);
    }
  }

  private static void heartbeat(DefaultGreybayStudy<?, ?> study) {
    try {
      var heartbeat = DefaultGreybayStudy.class.getDeclaredMethod("heartbeat");
      heartbeat.setAccessible(true);
      heartbeat.invoke(study);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("Unable to inject the heartbeat interleaving.", failure);
    }
  }

  private static void disableScheduledHeartbeat(DefaultGreybayStudy<?, ?> study) throws Exception {
    var executor = (ScheduledExecutorService) field(study, "heartbeatExecutor");
    executor.shutdown();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
  }

  private static Object field(Object object, String name) throws Exception {
    var field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(object);
  }

  private static void copyCommittedPrefix(
      Path source, Path target, GreybayStudyDefinition<?, ?> definition, long finalSequence)
      throws IOException {
    var manifest = StudyManifest.prepare(definition).value();
    try (var original = StudyStore.resume(source, manifest);
        var crashed = StudyStore.create(target, manifest)) {
      try (var artifacts = Files.list(source.resolve("artifacts"))) {
        for (var artifact : artifacts.toList())
          Files.copy(artifact, target.resolve("artifacts").resolve(artifact.getFileName()));
      }
      for (JournalRecord record : original.records()) {
        crashed.append(record.kind(), record.payload());
        if (record.sequence() == finalSequence) break;
      }
      assertThat(crashed.records().getLast().sequence()).isEqualTo(finalSequence);
      assertThat(crashed.records().getLast().kind()).isEqualTo("ACTIVE_HEARTBEAT");
    }
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

  private static GreybayStudyDefinition<TestdataSolution, SimpleScore> definition() {
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
        .trainingPanel(new ProblemPanel(List.of("training"), List.of(11L, 12L)))
        .heldOutPanel(new ProblemPanel(List.of("held-out"), List.of(21L)))
        .templates(List.of(new Template()))
        .objective(new BalancedQualityObjective<>())
        .sampler(new RandomSamplerConfig())
        .optimizerSeed(37)
        .solveDuration(Duration.ofSeconds(1))
        .warmupDuration(Duration.ZERO)
        .maxTrials(0)
        .activeTimeLimit(Duration.ofMinutes(2))
        .build();
  }

  private static final class Adapter implements ProblemAdapter<TestdataSolution> {
    @Override
    public ComponentIdentity identity() {
      return new ComponentIdentity("heartbeat-adapter", "1", "test-artifact-v1");
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
      return new ComponentIdentity("heartbeat-template", "1", "test-artifact-v1");
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
}
