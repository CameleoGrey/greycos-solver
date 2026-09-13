package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsOperatorPair;
import greycos.solver.core.api.solver.alns.AlnsSelectionPolicy;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.api.solver.phase.PhaseCommandContext;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsMoveThreadingMode;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Outer island limits exercised through public configuration and operator/command callbacks. */
@Timeout(15)
class IslandTerminationIntegrationTest {
  private static final Map<String, Harness> HARNESSES = new ConcurrentHashMap<>();

  @ParameterizedTest
  @MethodSource("zeroLimits")
  void zeroOuterLimitPreventsTheFirstCustomCommand(TerminationConfig outer) {
    try (var harness = new Harness()) {
      var config = config(harness, 2, outer, marker(harness));
      var result =
          SolverFactory.<TestdataSolution>create(config).buildSolver().solve(problem(harness));
      assertThat(harness.commands).hasValue(0);
      assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
    }
  }

  private static Stream<TerminationConfig> zeroLimits() {
    return Stream.of(
        new TerminationConfig().withSpentLimit(Duration.ZERO),
        new TerminationConfig().withStepCountLimit(0),
        new TerminationConfig().withMoveCountLimit(0L),
        new TerminationConfig().withScoreCalculationCountLimit(0L));
  }

  @ParameterizedTest
  @ValueSource(strings = {"localSearch", "alns"})
  void explicitUnboundedSearchObservesTheOuterClock(String phaseType) {
    try (var harness = new Harness()) {
      harness.advanceOnScoring = true;
      PhaseConfig<?> phase =
          phaseType.equals("localSearch")
              ? new LocalSearchPhaseConfig()
              : alns(harness, "search", null, AlnsMoveThreadingMode.PROBES, "NONE");
      var config =
          config(harness, 1, new TerminationConfig().withSpentLimit(Duration.ofMillis(100)), phase);
      var result =
          SolverFactory.<TestdataSolution>create(config).buildSolver().solve(problem(harness));
      assertThat(harness.scoringEntered.getCount()).isZero();
      assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(result.getEntityList().getFirst().getValue()).isNotNull();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"steps", "moves"})
  void everyIslandSharesItsWorkQuotaAcrossInnerPhases(String work) {
    try (var harness = new Harness()) {
      var outer = workLimit(work, 5);
      var config =
          config(
              harness,
              2,
              outer,
              alns(harness, "first", 2, AlnsMoveThreadingMode.PROBES, "NONE"),
              alns(harness, "second", null, AlnsMoveThreadingMode.PROBES, "NONE"));
      SolverFactory.<TestdataSolution>create(config).buildSolver().solve(problem(harness));
      assertThat(harness.trials).hasSize(2);
      assertThat(harness.trials.values())
          .allSatisfy(
              phases ->
                  assertThat(phases)
                      .containsExactly("first", "first", "second", "second", "second"));
    }
  }

  @ParameterizedTest
  @EnumSource(TerminationCompositionStyle.class)
  void outerWorkCompositionKeepsAndOrSemantics(TerminationCompositionStyle style) {
    try (var harness = new Harness()) {
      var outer =
          new TerminationConfig()
              .withTerminationCompositionStyle(style)
              .withTerminationConfigList(List.of(workLimit("steps", 3), workLimit("moves", 5)));
      var config =
          config(
              harness,
              2,
              outer,
              alns(harness, "search", null, AlnsMoveThreadingMode.PROBES, "NONE"));
      SolverFactory.<TestdataSolution>create(config).buildSolver().solve(problem(harness));
      int expected = style == TerminationCompositionStyle.AND ? 5 : 3;
      assertThat(harness.trials).hasSize(2);
      assertThat(harness.trials.values())
          .allSatisfy(phases -> assertThat(phases).hasSize(expected));
    }
  }

  @Test
  void innerLimitsRemainIndependentAndRepeatedSolvesGetFreshOuterQuotas() {
    try (var harness = new Harness()) {
      var config =
          config(
              harness,
              2,
              workLimit("steps", 5),
              alns(harness, "first", 1, AlnsMoveThreadingMode.PROBES, "NONE"),
              alns(harness, "second", 2, AlnsMoveThreadingMode.PROBES, "NONE"),
              marker(harness));
      var solver = SolverFactory.<TestdataSolution>create(config).buildSolver();
      for (int run = 0; run < 2; run++) {
        harness.trials.clear();
        harness.commands.set(0);
        solver.solve(problem(harness));
        assertThat(harness.trials).hasSize(2);
        assertThat(harness.trials.values())
            .allSatisfy(phases -> assertThat(phases).containsExactly("first", "second", "second"));
        assertThat(harness.commands).hasValue(2);
      }
    }
  }

  @Test
  void calculationWorkInAnEarlierPhaseReducesEveryIslandsRemainingQuota() {
    int baselineRemaining = remainingQueriesAfterFirstPhase(0);
    int remainingAfterThreeQueries = remainingQueriesAfterFirstPhase(3);
    // Both runs have the same phase lifecycle and its score calculations. Only the three explicit
    // queries differ, so this comparison does not freeze the framework's initialization overhead.
    assertThat(baselineRemaining).isGreaterThan(3);
    assertThat(remainingAfterThreeQueries).isEqualTo(baselineRemaining - 3);
  }

  private static int remainingQueriesAfterFirstPhase(int firstPhaseQueries) {
    try (var harness = new Harness()) {
      var config =
          config(
              harness,
              2,
              new TerminationConfig().withScoreCalculationCountLimit(20L),
              new CustomPhaseConfig()
                  .withCustomPhaseCommands(
                      (PhaseCommand<TestdataSolution>)
                          context -> {
                            for (int i = 0; i < firstPhaseQueries; i++) calculate(context);
                          }),
              new CustomPhaseConfig()
                  .withCustomPhaseCommands(
                      (PhaseCommand<TestdataSolution>)
                          context -> {
                            int count = 0;
                            while (!context.isPhaseTerminated()) {
                              calculate(context);
                              count++;
                            }
                            harness.remainingQueries.put(Thread.currentThread().threadId(), count);
                          }),
              marker(harness));
      SolverFactory.<TestdataSolution>create(config).buildSolver().solve(problem(harness));
      assertThat(harness.remainingQueries).hasSize(2);
      assertThat(harness.remainingQueries.values())
          .containsOnly(harness.remainingQueries.values().iterator().next());
      assertThat(harness.commands).hasValue(0);
      return harness.remainingQueries.values().iterator().next();
    }
  }

  private static void calculate(PhaseCommandContext<TestdataSolution> context) {
    var entity = context.getWorkingSolution().getEntityList().getFirst();
    var variable =
        context
            .getSolutionMetaModel()
            .genuineEntity(TestdataEntity.class)
            .basicVariable("value", TestdataValue.class);
    context.executeAndCalculateScore(Moves.change(variable, entity, entity.getValue()));
  }

  @Test
  void customPreparationDoesNotStartIdleAndLaterPhaseTransitionsDoNotResetIt() {
    try (var harness = new Harness()) {
      harness.advanceAfterTrial.put("first", 50L);
      var config =
          config(
              harness,
              1,
              new TerminationConfig().withUnimprovedSpentLimit(Duration.ofMillis(100)),
              new CustomPhaseConfig()
                  .withCustomPhaseCommands(
                      (PhaseCommand<TestdataSolution>) context -> harness.clock.advance(1_000)),
              alns(harness, "first", 1, AlnsMoveThreadingMode.PROBES, "NONE"),
              new CustomPhaseConfig()
                  .withCustomPhaseCommands(
                      (PhaseCommand<TestdataSolution>) context -> harness.clock.advance(50)),
              alns(harness, "second", 1, AlnsMoveThreadingMode.PROBES, "NONE"));
      SolverFactory.<TestdataSolution>create(config).buildSolver().solve(problem(harness));
      assertThat(harness.trials).hasSize(1);
      assertThat(harness.trials.values())
          .allSatisfy(phases -> assertThat(phases).containsExactly("first"));
    }
  }

  @ParameterizedTest
  @EnumSource(AlnsMoveThreadingMode.class)
  void threadedAlnsObservesOuterLogicalAndElapsedLimits(AlnsMoveThreadingMode mode) {
    try (var harness = new Harness()) {
      var config =
          config(harness, 2, workLimit("moves", 3), alns(harness, "search", null, mode, "2"));
      var result =
          SolverFactory.<TestdataSolution>create(config).buildSolver().solve(problem(harness));
      assertThat(harness.trials).hasSize(2);
      assertThat(harness.trials.values()).allSatisfy(phases -> assertThat(phases).hasSize(3));
      assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
    }
    try (var harness = new Harness()) {
      harness.advanceOnScoring = true;
      var config =
          config(
              harness,
              1,
              new TerminationConfig().withSpentLimit(Duration.ofMillis(100)),
              alns(harness, "search", null, mode, "2"));
      var result =
          SolverFactory.<TestdataSolution>create(config).buildSolver().solve(problem(harness));
      assertThat(harness.scoringEntered.getCount()).isZero();
      assertThat(result.getEntityList().getFirst().getValue().getCode()).isEqualTo("0");
      assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
    }
  }

  @ParameterizedTest
  @EnumSource(AlnsMoveThreadingMode.class)
  void outerCalculationLimitStopsTheSameCompletedTrialPrefixWithWorkers(
      AlnsMoveThreadingMode mode) {
    List<String> expected = null;
    for (String workers : List.of("NONE", "2")) {
      try (var harness = new Harness()) {
        var config =
            config(
                harness,
                1,
                new TerminationConfig().withScoreCalculationCountLimit(100L),
                alns(harness, "first", 1, mode, workers),
                alns(harness, "second", null, mode, workers));
        var result =
            SolverFactory.<TestdataSolution>create(config).buildSolver().solve(problem(harness));
        assertThat(harness.trials).hasSize(1);
        var phases = List.copyOf(harness.trials.values().iterator().next());
        assertThat(phases).startsWith("first").contains("second");
        if (expected == null) expected = phases;
        else assertThat(phases).isEqualTo(expected);
        assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
      }
    }
  }

  @ParameterizedTest
  @EnumSource(AlnsMoveThreadingMode.class)
  void terminateEarlyWhileAnAlnsWorkerScoresUnwindsNormally(AlnsMoveThreadingMode mode)
      throws Exception {
    try (var harness = new Harness();
        var executor = Executors.newSingleThreadExecutor()) {
      harness.blockScoring = true;
      var config =
          config(harness, 1, workLimit("moves", 100), alns(harness, "search", null, mode, "2"));
      var solver = SolverFactory.<TestdataSolution>create(config).buildSolver();
      var future = executor.submit(() -> solver.solve(problem(harness)));
      try {
        await(harness.scoringEntered);
        assertThat(solver.terminateEarly()).isTrue();
        harness.releaseScoring.countDown();
        var result = future.get(10, TimeUnit.SECONDS);
        assertThat(result.getEntityList().getFirst().getValue().getCode()).isEqualTo("0");
        assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
        assertThat(solver.isSolving()).isFalse();
      } finally {
        harness.releaseScoring.countDown();
        solver.terminateEarly();
      }
    }
  }

  private static TerminationConfig workLimit(String work, int limit) {
    return work.equals("steps")
        ? new TerminationConfig().withStepCountLimit(limit)
        : new TerminationConfig().withMoveCountLimit((long) limit);
  }

  @Test
  void exhaustedOuterWorkSkipsThePhaseAfterAFinitelyBoundedSearch() {
    try (var harness = new Harness()) {
      var config =
          config(
              harness,
              2,
              workLimit("steps", 2),
              alns(harness, "search", 100, AlnsMoveThreadingMode.PROBES, "NONE"),
              marker(harness));
      SolverFactory.<TestdataSolution>create(config).buildSolver().solve(problem(harness));
      assertThat(harness.trials).hasSize(2);
      assertThat(harness.trials.values()).allSatisfy(phases -> assertThat(phases).hasSize(2));
      assertThat(harness.commands).hasValue(0);
    }
  }

  private static SolverConfig config(
      Harness harness, int islands, TerminationConfig outer, PhaseConfig<?>... phases) {
    return new SolverConfig(harness.clock)
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withConstraintProviderClass(ObservingConstraints.class)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withMoveThreadCount("NONE")
        .withPhases(
            new IslandModelPhaseConfig()
                .withIslandCount(islands)
                .withCompareGlobalEnabled(false)
                .withMigrationFrequency(Integer.MAX_VALUE)
                .withTerminationConfig(outer)
                .withPhaseConfigList(List.of(phases)));
  }

  private static AlnsPhaseConfig alns(
      Harness harness,
      String phase,
      Integer localSteps,
      AlnsMoveThreadingMode mode,
      String workers) {
    var config =
        new AlnsPhaseConfig()
            .withMoveThreadCount(workers)
            .withMoveThreadingMode(mode)
            .withSelectionPolicyClass(RecordingSelection.class)
            .withSelectionPolicyCustomProperties(Map.of("runId", harness.id, "phase", phase))
            .withDestroyOperators(
                new AlnsDestroyOperatorConfig()
                    .withCustomClass(FirstDestroy.class)
                    .withMinimumDestroyedCount(1)
                    .withMaximumDestroyedCount(1))
            .withRepairOperators(
                new AlnsRepairOperatorConfig().withType(AlnsRepairOperatorType.RANDOMIZED_GREEDY));
    if (localSteps != null)
      config.withTerminationConfig(new TerminationConfig().withStepCountLimit(localSteps));
    if (mode == AlnsMoveThreadingMode.REPAIR_ATTEMPTS) config.withRepairAttemptCount(2);
    return config;
  }

  private static CustomPhaseConfig marker(Harness harness) {
    return new CustomPhaseConfig()
        .withCustomPhaseCommands(
            (PhaseCommand<TestdataSolution>) context -> harness.commands.incrementAndGet());
  }

  private static TestdataSolution problem(Harness harness) {
    var solution = new TestdataSolution(harness.id);
    var values = new ArrayList<TestdataValue>();
    for (int i = 0; i < 8; i++) values.add(new TestdataValue(Integer.toString(i)));
    solution.setValueList(values);
    solution.setEntityList(List.of(new TestdataEntity(harness.id + ":entity", values.getFirst())));
    return solution;
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  public static final class FirstDestroy
      implements AlnsDestroyOperator<TestdataSolution, SimpleScore> {
    @Override
    public List<AlnsTarget<TestdataSolution>> select(
        AlnsContext<TestdataSolution, SimpleScore> context, int size) {
      return context.targets().subList(0, Math.min(size, 1));
    }
  }

  public static final class RecordingSelection implements AlnsSelectionPolicy<SimpleScore> {
    private String runId;
    private String phase;

    public void setRunId(String runId) {
      this.runId = runId;
    }

    public void setPhase(String phase) {
      this.phase = phase;
    }

    @Override
    public AlnsOperatorPair select(List<AlnsOperatorPair> eligiblePairs, RandomGenerator random) {
      return eligiblePairs.getFirst();
    }

    @Override
    public void update(AlnsTrialResult<SimpleScore> result) {
      var harness = HARNESSES.get(runId);
      harness
          .trials
          .computeIfAbsent(
              Thread.currentThread().threadId(), ignored -> new CopyOnWriteArrayList<>())
          .add(phase);
      harness.clock.advance(harness.advanceAfterTrial.getOrDefault(phase, 0L));
    }
  }

  public static final class ObservingConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .reward(
                SimpleScore.ONE,
                entity -> {
                  var harness = HARNESSES.get(entity.getCode().split(":", 2)[0]);
                  if (!entity.getValue().getCode().equals("0")
                      && harness.scoringTriggered.compareAndSet(false, true)) {
                    harness.scoringEntered.countDown();
                    if (harness.advanceOnScoring) harness.clock.advance(100);
                    if (harness.blockScoring) await(harness.releaseScoring);
                  }
                  return 0;
                })
            .asConstraint("Constant score with a coordinated probe")
      };
    }
  }

  private static final class Harness implements AutoCloseable {
    private final String id = UUID.randomUUID().toString();
    private final ManualClock clock = new ManualClock();
    private final AtomicInteger commands = new AtomicInteger();
    private final Map<Long, List<String>> trials = new ConcurrentHashMap<>();
    private final Map<Long, Integer> remainingQueries = new ConcurrentHashMap<>();
    private final Map<String, Long> advanceAfterTrial = new ConcurrentHashMap<>();
    private final AtomicBoolean scoringTriggered = new AtomicBoolean();
    private final CountDownLatch scoringEntered = new CountDownLatch(1);
    private final CountDownLatch releaseScoring = new CountDownLatch(1);
    private boolean advanceOnScoring;
    private boolean blockScoring;

    private Harness() {
      HARNESSES.put(id, this);
    }

    @Override
    public void close() {
      releaseScoring.countDown();
      HARNESSES.remove(id);
    }
  }

  private static final class ManualClock extends Clock {
    private final AtomicLong millis = new AtomicLong(1_000_000);

    private void advance(long amount) {
      millis.addAndGet(amount);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis());
    }

    @Override
    public long millis() {
      return millis.get();
    }
  }
}
