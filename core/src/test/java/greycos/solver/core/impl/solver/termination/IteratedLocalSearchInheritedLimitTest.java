package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.SAME_THREAD)
@Timeout(20)
class IteratedLocalSearchInheritedLimitTest {

  private static final AtomicLong TIME_MILLIS = new AtomicLong();
  private static final AtomicInteger SELECTIONS = new AtomicInteger();
  private static final Clock CLOCK =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          if (!ZoneOffset.UTC.equals(zone)) throw new UnsupportedOperationException();
          return this;
        }

        @Override
        public Instant instant() {
          return Instant.ofEpochMilli(TIME_MILLIS.get());
        }
      };

  enum Placement {
    STANDALONE,
    ISLAND,
    PARTITION,
    ISLAND_IN_PARTITION,
    PARTITION_IN_ISLAND,
    NESTED_ISLAND,
    NESTED_PARTITION
  }

  @BeforeEach
  void resetObservations() {
    TIME_MILLIS.set(1_000L);
    SELECTIONS.set(0);
  }

  @ParameterizedTest
  @EnumSource(Placement.class)
  void episodeLimitsAndCancellationPlumbingDoNotBoundTheOuterSearch(Placement placement) {
    var config = config(placement, search());
    assertThatThrownBy(() -> solve(config))
        .hasStackTraceContaining("Episode limits alone do not bound the outer search.");
    assertThat(SELECTIONS).hasValue(0);
  }

  @ParameterizedTest
  @EnumSource(Placement.class)
  void explicitIterationLimitAllowsEmptyEnclosingBudgets(Placement placement) throws Exception {
    var result = solve(config(placement, search().withIterationCountLimit(2L)));
    assertReplayable(result);
    assertThat(SELECTIONS.get()).isPositive();
  }

  static Stream<Arguments> inheritedLimits() {
    return Stream.of(Placement.values())
        .flatMap(
            placement ->
                Stream.of("time", "moves", "steps")
                    .flatMap(
                        kind ->
                            Stream.of(false, true)
                                .map(solverOrigin -> Arguments.of(placement, kind, solverOrigin))));
  }

  @ParameterizedTest
  @MethodSource("inheritedLimits")
  void inheritedDeadlineAndWorkLimitsPermitAndTerminateTheSearch(
      Placement placement, String kind, boolean solverOrigin) throws Exception {
    var config = config(placement, search());
    var termination =
        switch (kind) {
          case "time" -> new TerminationConfig().withMillisecondsSpentLimit(100L);
          case "moves" -> new TerminationConfig().withMoveCountLimit(8L);
          case "steps" -> new TerminationConfig().withStepCountLimit(3);
          default -> throw new IllegalArgumentException(kind);
        };
    if (solverOrigin) config.setTerminationConfig(termination);
    else config.getPhaseConfigList().getFirst().setTerminationConfig(termination);
    var result = solve(config);
    assertReplayable(result);
    // A deadline expiring before child ILS construction would not exercise inherited validation.
    assertThat(SELECTIONS.get()).isPositive();
    if (kind.equals("time")) assertThat(TIME_MILLIS.get()).isGreaterThanOrEqualTo(1_100L);
  }

  private static IteratedLocalSearchPhaseConfig search() {
    var selector =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig(TestdataEntity.class)
                    .withFilterClass(ObserveSelection.class));
    return new IteratedLocalSearchPhaseConfig()
        .withLocalSearch(new LocalSearchPhaseConfig().withMoveSelectorConfig(selector))
        .withPerturbationMoveSelectorConfig(selector.copyConfig())
        .withPerturbationStrengths(1, 2)
        .withPerturbationAttemptLimit(8L)
        .withEpisodeCandidateAttemptLimit(12L);
  }

  private static SolverConfig config(Placement placement, IteratedLocalSearchPhaseConfig search) {
    PhaseConfig<?> phase =
        switch (placement) {
          case STANDALONE -> search;
          case ISLAND -> island(search);
          case PARTITION -> partition(search);
          case ISLAND_IN_PARTITION -> partition(island(search));
          case PARTITION_IN_ISLAND -> island(partition(search));
          case NESTED_ISLAND -> island(island(search));
          case NESTED_PARTITION -> partition(partition(search));
        };
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_NONE)
            .withPhases(phase);
    config.setClock(CLOCK);
    return config;
  }

  private static IslandModelPhaseConfig island(PhaseConfig<?> phase) {
    return new IslandModelPhaseConfig().withIslandCount(1).withPhaseConfigList(List.of(phase));
  }

  private static PartitionedSearchPhaseConfig partition(PhaseConfig<?> phase) {
    return new PartitionedSearchPhaseConfig()
        .withSolutionPartitionerClass(OnePartition.class)
        .withRunnablePartThreadLimit(PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_UNLIMITED)
        .withPhaseConfigList(List.of(phase));
  }

  private static TestdataSolution solve(SolverConfig config) throws Exception {
    var solver = SolverFactory.<TestdataSolution>create(config).buildSolver();
    var executor = Executors.newSingleThreadExecutor();
    try {
      var future = executor.submit(() -> solver.solve(TestdataSolution.generateSolution(3, 4)));
      return future.get(5, TimeUnit.SECONDS);
    } finally {
      // A regression in startup validation must fail this test without retaining solver threads.
      solver.terminateEarly();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private static void assertReplayable(TestdataSolution solution) {
    assertThat(solution.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
    assertThat(solution.getScore())
        .isEqualTo(new TestdataEasyScoreCalculator().calculateScore(solution));
  }

  public static final class ObserveSelection
      implements SelectionFilter<TestdataSolution, TestdataEntity> {
    @Override
    public boolean accept(ScoreDirector<TestdataSolution> director, TestdataEntity entity) {
      SELECTIONS.incrementAndGet();
      TIME_MILLIS.addAndGet(10L);
      return true;
    }
  }

  public static final class OnePartition implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> director, Integer runnablePartThreadLimit) {
      return List.of(((InnerScoreDirector<TestdataSolution, ?>) director).cloneWorkingSolution());
    }
  }
}
