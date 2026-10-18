package greycos.solver.core.impl.partitionedsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.api.solver.event.FinalBestSolutionEvent;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.FinalistPodiumType;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchPickEarlyType;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.decider.forager.AbstractLocalSearchForager;
import greycos.solver.core.impl.localsearch.decider.forager.AcceptedLocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSample;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSamples;
import greycos.solver.core.impl.solver.monitoring.SolverWorkSnapshot;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.AbstractMeterTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(30)
class PartitionWorkAccountingTest extends AbstractMeterTest {

  private static final AtomicLong EVALUATIONS = new AtomicLong();
  private static final Map<String, Long> TYPES = new ConcurrentHashMap<>();
  private static final List<List<String>> ROOT_TRACE = new CopyOnWriteArrayList<>();
  private static volatile Consumer<LocalSearchStepScope<TestdataSolution>> stepObserver =
      step -> {};

  @BeforeEach
  void resetOracle() {
    EVALUATIONS.set(0L);
    TYPES.clear();
    ROOT_TRACE.clear();
    stepObserver = step -> {};
  }

  static Stream<Arguments> topologies() {
    return Stream.of("partition", "nestedPartitions", "partitionThenIsland", "islandThenPartition")
        .flatMap(
            topology ->
                Stream.of("NONE", "2")
                    .flatMap(
                        workers ->
                            Stream.of(false, true)
                                .map(subscriber -> Arguments.of(topology, workers, subscriber))));
  }

  @ParameterizedTest
  @MethodSource("topologies")
  void countsExactlyOnceAcrossNestingAndReuse(String topology, String workers, boolean subscriber) {
    PhaseConfig<?> phase =
        switch (topology) {
          case "partition" -> partition(search(3));
          case "nestedPartitions" -> partition(partition(search(3)));
          case "partitionThenIsland" -> partition(islands(search(3)));
          case "islandThenPartition" -> islands(partition(search(3)));
          default -> throw new IllegalArgumentException(topology);
        };
    var solver = solver(config(workers, phase));
    var samples = new CopyOnWriteArrayList<SolverMetricSample>();
    if (subscriber) {
      solver.getSolverScope().addMetricSampleListener(samples::add);
    }
    for (int run = 0; run < 2; run++) {
      resetOracle();
      samples.clear();
      var result = solver.solveAndSummarize(problem());
      assertThat(EVALUATIONS.get()).isEqualTo(topology.equals("partition") ? 6L : 12L);
      assertThat(result.moveEvaluationCount()).isEqualTo(EVALUATIONS.get());
      assertThat(result.scoreCalculationCount()).isEqualTo(solver.getScoreCalculationCount());
      assertThat(solver.getSolverScope().getReportedMoveCountsByType()).isEqualTo(TYPES);
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
      if (subscriber) {
        var last = samples.getLast();
        assertThat(last.kind()).isEqualTo(SolverMetricSample.Kind.FINAL);
        assertThat(last.source()).isEqualTo("root");
        assertThat(last.work())
            .isEqualTo(
                new SolverWorkSnapshot(result.scoreCalculationCount(), EVALUATIONS.get(), TYPES));
      }
    }
  }

  @Test
  void problemChangesResetSegmentCountsAndPreserveWholeRunTotals() {
    var solver = solver(config("NONE", partition(search(3))));
    var segments = new ArrayList<SolverWorkSnapshot>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingEnded(SolverScope<TestdataSolution> scope) {
            segments.add(SolverMetricSamples.reportedWork(scope));
            if (segments.size() == 1) {
              solver.addProblemChange((solution, director) -> {});
            }
          }
        });
    var result = solver.solveAndSummarize(problem());
    assertThat(segments).hasSize(2);
    assertThat(segments)
        .extracting(SolverWorkSnapshot::moveEvaluationCount)
        .containsExactly(6L, 6L);
    assertThat(result.moveEvaluationCount()).isEqualTo(EVALUATIONS.get()).isEqualTo(12L);
    assertThat(solver.getMoveEvaluationCount()).isEqualTo(6L);
    assertThat(result.scoreCalculationCount())
        .isEqualTo(segments.stream().mapToLong(SolverWorkSnapshot::scoreCalculationCount).sum());
  }

  @Test
  void jobAndFinalEventContainPartitionWork() throws Exception {
    var consumed = new AtomicReference<FinalBestSolutionEvent<TestdataSolution>>();
    try (var manager =
        SolverManager.<TestdataSolution>create(config("NONE", partition(search(3))))) {
      var job =
          manager
              .solveBuilder()
              .withProblemId(1L)
              .withProblem(problem())
              .withFinalBestSolutionEventConsumer(consumed::set)
              .run();
      var result = job.getResult();
      assertThat(result.moveEvaluationCount()).isEqualTo(EVALUATIONS.get()).isEqualTo(6L);
      assertThat(result.scoreCalculationCount()).isEqualTo(job.getScoreCalculationCount());
      assertThat(consumed.get()).isSameAs(result);
      assertThat(job.getFinalBestSolution()).isSameAs(result.solution());
    }
  }

  @Test
  void reportingDoesNotConsumeTheFollowingRootPhasesMoveBudget() {
    var base = config("NONE", partition(search(3)), search(3));
    var unlimited = solver(base.copyConfig());
    unlimited.solve(problem());
    var expectedTrace = List.copyOf(ROOT_TRACE);
    assertThat(expectedTrace).hasSize(3);
    resetOracle();
    var limited =
        solver(
            base.copyConfig()
                .withTerminationConfig(new TerminationConfig().withMoveCountLimit(3L)));
    var result = limited.solveAndSummarize(problem());
    assertThat(ROOT_TRACE).containsExactlyElementsOf(expectedTrace);
    assertThat(limited.getSolverScope().getMoveEvaluationCount()).isEqualTo(3L);
    assertThat(result.moveEvaluationCount()).isEqualTo(EVALUATIONS.get()).isEqualTo(9L);
    assertThat(limited.getSolverScope().getReportedMoveCountsByType()).isEqualTo(TYPES);
  }

  @Test
  void cooperativeTerminationIncludesWorkCompletedDuringShutdown() throws Exception {
    var childrenStepped = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    stepObserver =
        step -> {
          if (!step.getPhaseScope().getSolverScope().isRootScope()) {
            childrenStepped.countDown();
            try {
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new AssertionError(e);
            }
          }
        };
    var solver = solver(config("NONE", partition(search(Integer.MAX_VALUE))));
    try (var executor = Executors.newSingleThreadExecutor()) {
      var solving = executor.submit(() -> solver.solveAndSummarize(problem()));
      try {
        assertThat(childrenStepped.await(5, TimeUnit.SECONDS)).isTrue();
        solver.terminateEarly();
      } finally {
        release.countDown();
      }
      var result = solving.get(5, TimeUnit.SECONDS);
      assertThat(result.moveEvaluationCount())
          .isEqualTo(EVALUATIONS.get())
          .isGreaterThanOrEqualTo(2L);
      assertThat(result.scoreCalculationCount()).isEqualTo(solver.getScoreCalculationCount());
      assertThat(solver.getSolverScope().getReportedMoveCountsByType()).isEqualTo(TYPES);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void reportStateDoesNotChangeLocalCountersOrIncludeSharedIslandsAndResets() {
    var scope = new SolverScope<TestdataSolution>();
    var director = (InnerScoreDirector<TestdataSolution, ?>) mock(InnerScoreDirector.class);
    when(director.getCalculationCount()).thenReturn(5L);
    scope.setScoreDirector(director);
    scope.addMoveEvaluationCount(2L);
    scope.addMoveEvaluationCountPerType("root", 2L);
    var types = new ConcurrentHashMap<>(Map.of("partition", 7L));
    scope.addChildThreadsScoreCalculationCount(11L);
    scope.addChildPartitionMoveWork(new SolverWorkSnapshot(11L, 7L, types));
    types.clear();
    scope.getIslandWorkAccounting().register("island");
    scope
        .getIslandWorkAccounting()
        .publish("island", new SolverWorkSnapshot(13L, 17L, Map.of("island", 17L)));
    assertThat(scope.getMoveEvaluationCount()).isEqualTo(2L);
    assertThat(scope.getMoveEvaluationCountPerType()).containsExactlyEntriesOf(Map.of("root", 2L));
    assertThat(scope.getChildPartitionMoveWork().scoreCalculationCount()).isZero();
    assertThat(SolverMetricSamples.localWork(scope))
        .isEqualTo(new SolverWorkSnapshot(16L, 9L, Map.of("root", 2L, "partition", 7L)));
    assertThat(scope.getReportedMoveEvaluationCount()).isEqualTo(26L);
    assertThat(scope.getReportedMoveCountsByType())
        .containsExactlyInAnyOrderEntriesOf(Map.of("root", 2L, "partition", 7L, "island", 17L));
    assertThatThrownBy(() -> scope.getChildPartitionMoveWork().moveCountsByType().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    scope.startingNow();
    assertThat(scope.getChildPartitionMoveWork()).isSameAs(SolverWorkSnapshot.ZERO);
    assertThat(SolverMetricSamples.localWork(scope))
        .isEqualTo(new SolverWorkSnapshot(5L, 0L, Map.of()));
  }

  private static TestdataSolution problem() {
    return TestdataSolution.generateSolution(3, 4);
  }

  private static SolverConfig config(String workers, PhaseConfig<?>... phases) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(ConstantCalculator.class)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withMoveThreadCount(workers)
        .withRandomSeed(0L)
        .withMonitoringConfig(
            new MonitoringConfig().withSolverMetricList(List.of(SolverMetric.MOVE_COUNT_PER_TYPE)))
        .withPhases(phases);
  }

  private static DefaultSolver<TestdataSolution> solver(SolverConfig config) {
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }

  private static LocalSearchPhaseConfig search(int steps) {
    return new LocalSearchPhaseConfig()
        .withAcceptorConfig(
            new LocalSearchAcceptorConfig()
                .withAcceptorTypeList(List.of(AcceptorType.HILL_CLIMBING)))
        .withForagerConfig(new LocalSearchForagerConfig().withForagerClass(CountingForager.class))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  private static PartitionedSearchPhaseConfig partition(PhaseConfig<?> child) {
    return new PartitionedSearchPhaseConfig()
        .withSolutionPartitionerClass(HalvingPartitioner.class)
        .withPhaseConfigList(List.of(child));
  }

  private static IslandModelPhaseConfig islands(PhaseConfig<?> child) {
    return new IslandModelPhaseConfig()
        .withIslandCount(2)
        .withCompareGlobalEnabled(false)
        .withMigrationFrequency(Integer.MAX_VALUE)
        .withPhaseConfigList(List.of(child));
  }

  public static final class HalvingPartitioner implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> director, Integer limit) {
      var source = director.getWorkingSolution();
      var parts = new ArrayList<TestdataSolution>();
      int partSize = Math.max(1, (source.getEntityList().size() + 1) / 2);
      for (int start = 0; start < source.getEntityList().size(); start += partSize) {
        var part = new TestdataSolution();
        part.setValueList(new ArrayList<>(source.getValueList()));
        part.setEntityList(
            new ArrayList<>(
                source
                    .getEntityList()
                    .subList(start, Math.min(start + partSize, source.getEntityList().size()))
                    .stream()
                    .map(entity -> new TestdataEntity(entity.getCode(), entity.getValue()))
                    .toList()));
        parts.add(part);
      }
      return parts;
    }
  }

  public static final class ConstantCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  /** Counts independently at the evaluated-move boundary, without reading solver work counters. */
  public static final class CountingForager extends AbstractLocalSearchForager<TestdataSolution> {
    private final AcceptedLocalSearchForager<TestdataSolution> delegate =
        new AcceptedLocalSearchForager<>(
            FinalistPodiumType.HIGHEST_SCORE.buildFinalistPodium(),
            LocalSearchPickEarlyType.NEVER,
            1,
            false);

    @Override
    public void solvingStarted(SolverScope<TestdataSolution> scope) {
      delegate.solvingStarted(scope);
    }

    @Override
    public void solvingEnded(SolverScope<TestdataSolution> scope) {
      delegate.solvingEnded(scope);
    }

    @Override
    public void solvingError(SolverScope<TestdataSolution> scope, Throwable failure) {
      delegate.solvingError(scope, failure);
    }

    @Override
    public void phaseStarted(LocalSearchPhaseScope<TestdataSolution> scope) {
      delegate.phaseStarted(scope);
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<TestdataSolution> scope) {
      delegate.phaseEnded(scope);
    }

    @Override
    public void stepStarted(LocalSearchStepScope<TestdataSolution> scope) {
      delegate.stepStarted(scope);
    }

    @Override
    public void stepEnded(LocalSearchStepScope<TestdataSolution> scope) {
      delegate.stepEnded(scope);
      if (scope.getPhaseScope().getSolverScope().isRootScope()) {
        ROOT_TRACE.add(
            scope.getWorkingSolution().getEntityList().stream()
                .map(entity -> entity.getValue().getCode())
                .toList());
      }
      stepObserver.accept(scope);
    }

    @Override
    public boolean supportsNeverEndingMoveSelector() {
      return delegate.supportsNeverEndingMoveSelector();
    }

    @Override
    public void addMove(LocalSearchMoveScope<TestdataSolution> scope) {
      EVALUATIONS.incrementAndGet();
      TYPES.merge(scope.getMove().describe(), 1L, Long::sum);
      delegate.addMove(scope);
    }

    @Override
    public boolean isQuitEarly() {
      return delegate.isQuitEarly();
    }

    @Override
    public LocalSearchMoveScope<TestdataSolution> pickMove(
        LocalSearchStepScope<TestdataSolution> scope) {
      return delegate.pickMove(scope);
    }
  }
}
