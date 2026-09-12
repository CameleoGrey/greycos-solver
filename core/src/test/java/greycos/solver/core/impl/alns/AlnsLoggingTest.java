package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsAcceptancePolicy;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsOutcome;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** The phase logger is shared with other solver tests, including tests running concurrently. */
@Isolated
@Timeout(30)
class AlnsLoggingTest {
  private static final String DESTROY_ID = "destroy/%";
  private static final String REPAIR_ID = "repair/%";
  private static final String PAIR_ID = "destroy%2F%25/repair%2F%25";
  private static final AtomicReference<BlockingState> BLOCKING = new AtomicReference<>();

  @Test
  void logsEachCompletedTrialOnceWithItsSettledScoresAndOutcome() {
    try (var capture = new LogCapture(Level.DEBUG)) {
      var run = runScripted();
      assertThat(run.results())
          .extracting(AlnsTrialResult::outcome)
          .containsExactly(
              AlnsOutcome.NEW_BEST,
              AlnsOutcome.ACCEPTED,
              AlnsOutcome.IMPROVED,
              AlnsOutcome.REJECTED,
              AlnsOutcome.NO_CHANGE,
              AlnsOutcome.REPAIR_FAILED);
      assertThat(run.results())
          .extracting(result -> (Object) result.beforeScore())
          .containsExactly(score(0), score(3), score(1), score(2), score(2), score(2));
      assertThat(run.results())
          .extracting(result -> (Object) result.candidateScore())
          .containsExactly(score(3), score(1), score(2), score(0), score(2), null);
      assertThat(run.results())
          .extracting(result -> (Object) result.afterScore())
          .containsExactly(score(3), score(1), score(2), score(2), score(2), score(2));
      var logs = capture.trials();
      assertThat(logs).hasSize(6);
      assertThat(run.recordedTrials()).isEqualTo(logs.size());
      assertThat(run.probes()).isEqualTo(24);
      for (int i = 0; i < logs.size(); i++) {
        assertTrialLog(logs.get(i), run.results().get(i), 1, "");
      }
      assertThat(
              logs.stream()
                  .map(event -> ((Number) event.getArgumentArray()[3]).longValue())
                  .toList())
          .isSorted();
      // Rejection logs the restored incumbent, not the speculative score zero.
      assertThat(logs.get(3).getFormattedMessage())
          .contains("score (2)", "best score (3)", "candidate score (0)", "outcome (REJECTED)");
      assertThat(logs.get(5).getFormattedMessage())
          .contains("candidate score (unavailable)", "outcome (REPAIR_FAILED)");
      assertThat(capture.summaries()).hasSize(1);
    }
  }

  @Test
  void logsCancellationOnceAfterRollbackEvenWithoutACompletedStep() throws Exception {
    var state = new BlockingState();
    BLOCKING.set(state);
    try (var capture = new LogCapture(Level.DEBUG);
        var meters = new TrialMeters();
        var executor = Executors.newSingleThreadExecutor()) {
      var solver = buildSolver(config(phase(BlockingRepair.class, 4)), meters);
      var future = executor.submit(() -> solver.solve(problem()));
      try {
        assertThat(state.entered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(solver.terminateEarly()).isTrue();
        state.release.countDown();
        var solution = future.get(10, TimeUnit.SECONDS);
        assertThat(solution.getScore()).isEqualTo(score(0));
        assertThat(solution.getEntityList().getFirst().getValue().getCode()).isEqualTo("0");
        assertThat(solver.getMoveEvaluationCount()).isZero();
        assertThat(meters.total(".trials")).isEqualTo(1);
        var event = capture.trials().getFirst();
        assertThat(capture.trials()).hasSize(1);
        var arguments = event.getArgumentArray();
        assertThat(arguments[1]).isEqualTo(0L);
        assertThat(arguments[2]).isEqualTo(1);
        assertThat(arguments[4]).isEqualTo(score(0));
        assertThat(arguments[5]).isEqualTo("   ");
        assertThat(arguments[6]).isEqualTo(score(0));
        assertThat(arguments[7]).isEqualTo("unavailable");
        assertThat(arguments[8]).isEqualTo(AlnsOutcome.CANCELLED);
        assertThat(arguments[9]).isEqualTo(PAIR_ID);
        assertThat(arguments[10]).isEqualTo(1);
        assertThat(arguments[11]).isEqualTo(0);
        assertThat(arguments[12]).isEqualTo(1L);
        assertThat(((Number) arguments[13]).doubleValue()).isNotNegative();
        assertThat(arguments[14]).isEqualTo("");
        assertThat(event.getFormattedMessage())
            .contains(
                "ALNS trial (0), phase (1)",
                "score (0)",
                "candidate score (unavailable)",
                "outcome (CANCELLED)")
            .doesNotContain("score (3)");
        assertThat(capture.summaries()).hasSize(1);
      } finally {
        state.release.countDown();
        solver.terminateEarly();
      }
    } finally {
      BLOCKING.set(null);
    }
  }

  @Test
  void concurrentIslandTrialsIncludeTheirOwnPhaseAndIslandIdentifiers() {
    var alns = phase(AlnsTrialSemanticsTest.OriginalRepair.class, 3);
    var config = config(alns);
    config.withPhases(
        new ConstructionHeuristicPhaseConfig().withMoveThreadCount("NONE"),
        new IslandModelPhaseConfig()
            .withIslandCount(2)
            .withMoveThreadCount("NONE")
            .withMigrationFrequency(100)
            .withReceiveGlobalUpdateFrequency(100)
            .withPhaseConfigList(List.of(alns)));
    try (var capture = new LogCapture(Level.DEBUG);
        var meters = new TrialMeters()) {
      var solution = buildSolver(config, meters).solve(problem());
      assertThat(solution.getScore()).isEqualTo(score(0));
      assertThat(meters.total(".trials")).isEqualTo(6);
      assertThat(capture.trials())
          .hasSize(6)
          .allSatisfy(
              event -> {
                assertThat(event.getArgumentArray()[2]).isEqualTo(0);
                assertThat(event.getArgumentArray()[8]).isEqualTo(AlnsOutcome.NO_CHANGE);
                assertThat(event.getArgumentArray()[9]).isEqualTo(PAIR_ID);
                assertThat(event.getFormattedMessage()).contains("phase (0)");
              });
      for (int island = 0; island < 2; island++) {
        var suffix = ", island (" + island + ")";
        var islandLogs =
            capture.trials().stream()
                .filter(event -> suffix.equals(event.getArgumentArray()[14]))
                .toList();
        assertThat(islandLogs).hasSize(3);
        assertThat(islandLogs)
            .extracting(event -> event.getArgumentArray()[1])
            .containsExactly(0L, 1L, 2L);
        assertThat(islandLogs)
            .allSatisfy(event -> assertThat(event.getFormattedMessage()).endsWith(suffix + "."));
      }
      assertThat(capture.summaries()).hasSize(2);
    }
  }

  @Test
  void infoSuppressesTrialLogsAndKeepsPhaseSummaryWithoutChangingSearch() {
    ObservedRun debug;
    try (var capture = new LogCapture(Level.DEBUG)) {
      debug = runScripted();
      assertThat(capture.trials()).hasSize(6);
      assertThat(capture.summaries()).hasSize(1);
    }
    ObservedRun info;
    try (var capture = new LogCapture(Level.INFO)) {
      info = runScripted();
      assertThat(capture.trials()).isEmpty();
      assertThat(capture.events)
          .noneSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.DEBUG));
      assertThat(capture.summaries())
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getFormattedMessage()).contains("best score (3)", "trials (6)"));
    }
    assertThat(debug.score()).isEqualTo(score(3)).isEqualTo(info.score());
    assertThat(debug.values()).containsExactly("3").isEqualTo(info.values());
    assertThat(debug.scoreCalculations()).isPositive().isEqualTo(info.scoreCalculations());
    assertThat(debug.moves()).isEqualTo(6).isEqualTo(info.moves());
    assertThat(debug.recordedTrials()).isEqualTo(6).isEqualTo(info.recordedTrials());
    assertThat(debug.probes()).isEqualTo(info.probes());
    assertThat(debug.results())
        .extracting(AlnsTrialResult::outcome)
        .containsExactlyElementsOf(info.results().stream().map(AlnsTrialResult::outcome).toList());
  }

  private static void assertTrialLog(
      ILoggingEvent event, AlnsTrialResult<?> result, int phase, String islandSuffix) {
    assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
    var arguments = event.getArgumentArray();
    assertThat(arguments).hasSize(15);
    assertThat(arguments[1]).isEqualTo(result.trialIndex());
    assertThat(arguments[2]).isEqualTo(phase);
    assertThat(((Number) arguments[3]).longValue()).isNotNegative();
    assertThat(arguments[4]).isEqualTo(result.afterScore());
    assertThat(arguments[5]).isEqualTo(result.outcome() == AlnsOutcome.NEW_BEST ? "new" : "   ");
    assertThat(arguments[6]).isEqualTo(result.bestAfterScore());
    assertThat(arguments[7])
        .isEqualTo(result.candidateScore() == null ? "unavailable" : result.candidateScore());
    assertThat(arguments[8]).isEqualTo(result.outcome());
    assertThat(arguments[9]).isEqualTo(PAIR_ID);
    assertThat(arguments[10]).isEqualTo(result.destroyedCount());
    assertThat(arguments[11]).isEqualTo(result.recoveryCount());
    assertThat(arguments[12]).isEqualTo(result.probeCount());
    assertThat(arguments[13]).isEqualTo(result.elapsedNanos() / 1_000_000.0);
    assertThat(arguments[14]).isEqualTo(islandSuffix);
    assertThat(event.getFormattedMessage())
        .contains(
            "ALNS trial (" + result.trialIndex() + "), phase (" + phase + ")",
            "operator pair (" + PAIR_ID + ")",
            "destroyed/recovery count (1/0)",
            "probes (4)");
  }

  private static ObservedRun runScripted() {
    try (var meters = new TrialMeters()) {
      var solver = buildSolver(config(phase(ScriptedRepair.class, 6)), meters);
      var results = new ArrayList<AlnsTrialResult<?>>();
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> stepScope) {
              if (stepScope instanceof AlnsStepScope<TestdataSolution> alns) {
                results.add(alns.getTrialResult());
              }
            }
          });
      var solution = solver.solve(problem());
      return new ObservedRun(
          solution.getScore(),
          solution.getEntityList().stream().map(entity -> entity.getValue().getCode()).toList(),
          solver.getScoreCalculationCount(),
          solver.getMoveEvaluationCount(),
          meters.total(".trials"),
          meters.total(".probes"),
          List.copyOf(results));
    }
  }

  private static DefaultSolver<TestdataSolution> buildSolver(
      SolverConfig config, TrialMeters meters) {
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    solver.setMonitorTagMap(Map.of("test.id", meters.id));
    return solver;
  }

  private static SolverConfig config(AlnsPhaseConfig phase) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withConstraintProviderClass(AlnsCancellationTest.NumberConstraints.class)
        .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
        .withMoveThreadCount("NONE")
        .withRandomSeed(0L)
        .withMonitoringConfig(
            new MonitoringConfig().withSolverMetricList(List.of(SolverMetric.ALNS_STATISTICS)))
        .withPhases(new ConstructionHeuristicPhaseConfig().withMoveThreadCount("NONE"), phase);
  }

  private static AlnsPhaseConfig phase(
      Class<? extends AlnsRepairOperator> repairClass, int trials) {
    return new AlnsPhaseConfig()
        .withAcceptancePolicyClass(AcceptPositive.class)
        .withDestroyOperators(
            new AlnsDestroyOperatorConfig()
                .withId(DESTROY_ID)
                .withCustomClass(AlnsCancellationTest.FirstDestroy.class)
                .withMinimumDestroyedCount(1)
                .withMaximumDestroyedCount(1))
        .withRepairOperators(
            new AlnsRepairOperatorConfig().withId(REPAIR_ID).withCustomClass(repairClass))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(trials));
  }

  private static TestdataSolution problem() {
    var values =
        List.of(
            new TestdataValue("0"),
            new TestdataValue("1"),
            new TestdataValue("2"),
            new TestdataValue("3"));
    var solution = new TestdataSolution("logging");
    solution.setValueList(new ArrayList<>(values));
    solution.setEntityList(
        new ArrayList<>(List.of(new TestdataEntity("entity", values.getFirst()))));
    return solution;
  }

  private static SimpleScore score(long value) {
    return SimpleScore.of(value);
  }

  public static final class AcceptPositive implements AlnsAcceptancePolicy<SimpleScore> {
    @Override
    public boolean isAccepted(SimpleScore current, SimpleScore candidate, RandomGenerator random) {
      return candidate.compareTo(SimpleScore.ZERO) > 0;
    }
  }

  public static final class ScriptedRepair
      implements AlnsRepairOperator<TestdataSolution, SimpleScore> {
    private int trial;

    @Override
    public boolean repair(
        AlnsContext<TestdataSolution, SimpleScore> context,
        List<AlnsTarget<TestdataSolution>> pending) {
      var placements = context.assignments(pending.getFirst());
      // Multiple scratch probes must still produce exactly one outer-trial progress message.
      placements.forEach(context::evaluate);
      if (trial == 5) {
        return false;
      }
      var code = new String[] {"3", "1", "2", "0", "2"}[trial++];
      context.assign(
          placements.stream()
              .filter(placement -> code.equals(((TestdataValue) placement.value()).getCode()))
              .findFirst()
              .orElseThrow());
      return true;
    }
  }

  public static final class BlockingRepair
      implements AlnsRepairOperator<TestdataSolution, SimpleScore> {
    @Override
    public boolean repair(
        AlnsContext<TestdataSolution, SimpleScore> context,
        List<AlnsTarget<TestdataSolution>> pending) {
      var placement = context.assignments(pending.getFirst()).getLast();
      context.evaluate(placement);
      context.assign(placement);
      var state = BLOCKING.get();
      state.entered.countDown();
      try {
        if (!state.release.await(10, TimeUnit.SECONDS)) {
          throw new AssertionError("Timed out waiting for cancellation.");
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      context.checkTermination();
      return true;
    }
  }

  private record ObservedRun(
      SimpleScore score,
      List<String> values,
      long scoreCalculations,
      long moves,
      long recordedTrials,
      long probes,
      List<AlnsTrialResult<?>> results) {}

  private static final class BlockingState {
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
  }

  private static final class TrialMeters implements AutoCloseable {
    final String id = UUID.randomUUID().toString();
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    TrialMeters() {
      Metrics.addRegistry(registry);
    }

    long total(String suffix) {
      return registry.find("greycos.solver.alns" + suffix).tag("test.id", id).counters().stream()
          .mapToLong(counter -> (long) counter.count())
          .sum();
    }

    @Override
    public void close() {
      Metrics.removeRegistry(registry);
      for (var meter : List.copyOf(Metrics.globalRegistry.getMeters())) {
        if (id.equals(meter.getId().getTag("test.id"))) {
          Metrics.globalRegistry.remove(meter);
        }
      }
      registry.close();
    }
  }

  private static final class LogCapture extends AppenderBase<ILoggingEvent>
      implements AutoCloseable {
    final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(DefaultAlnsPhase.class);
    private final Level previousLevel = logger.getLevel();
    private final boolean previousAdditive = logger.isAdditive();

    LogCapture(Level level) {
      setContext(logger.getLoggerContext());
      start();
      logger.addAppender(this);
      logger.setAdditive(false);
      logger.setLevel(level);
    }

    @Override
    protected void append(ILoggingEvent event) {
      event.prepareForDeferredProcessing();
      events.add(event);
    }

    List<ILoggingEvent> trials() {
      return events.stream()
          .filter(
              event ->
                  event.getLevel() == Level.DEBUG
                      && event.getFormattedMessage().contains("ALNS trial ("))
          .toList();
    }

    List<ILoggingEvent> summaries() {
      return events.stream()
          .filter(
              event ->
                  event.getLevel() == Level.INFO
                      && event.getFormattedMessage().contains("ALNS phase (")
                      && event.getFormattedMessage().contains("ended:"))
          .toList();
    }

    @Override
    public void close() {
      logger.detachAppender(this);
      logger.setLevel(previousLevel);
      logger.setAdditive(previousAdditive);
      stop();
    }
  }
}
