package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.api.solver.event.FinalBestSolutionEvent;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.util.MathUtils;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.MockClock;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class DefaultSolverRunResultTest {

  @Test
  void resultMatchesSolveAndDoesNotAccumulateAcrossReuse() {
    var solver = solver(config());
    var first = solver.solveAndSummarize(TestdataSolution.generateSolution(3, 4));
    var second = solver.solveAndSummarize(TestdataSolution.generateSolution(3, 4));
    var third = solver.solve(TestdataSolution.generateSolution(3, 4));

    assertThat(first.solution().getScore()).isEqualTo(third.getScore());
    assertThat(second.solution().getEntityList())
        .extracting(entity -> entity.getValue().getCode())
        .containsExactlyElementsOf(
            third.getEntityList().stream().map(entity -> entity.getValue().getCode()).toList());
    assertThat(first.scoreCalculationCount())
        .isPositive()
        .isEqualTo(second.scoreCalculationCount());
    assertThat(first.moveEvaluationCount()).isPositive().isEqualTo(second.moveEvaluationCount());
    assertThat(first.isSolutionInitialized()).isTrue();
    assertThat(first.problemSizeStatistics().entityCount()).isEqualTo(4);
  }

  @Test
  void zeroDurationUsesOneMillisecondForSpeeds() {
    var clock = new MockClock(Clock.systemUTC());
    var solver = solver(new SolverConfig(clock).inherit(config()));
    var result = solver.solveAndSummarize(TestdataSolution.generateSolution(3, 4));
    assertThat(result.solvingDuration()).isEqualTo(Duration.ZERO);
    assertThat(result.scoreCalculationSpeed()).isEqualTo(result.scoreCalculationCount() * 1000L);
    assertThat(result.moveEvaluationSpeed()).isEqualTo(result.moveEvaluationCount() * 1000L);
    assertThat(MathUtils.getSpeed(7L, -1L)).isEqualTo(7000L);
  }

  @Test
  void problemSizeSummaryIsIndependentOfLiveStatisticsAndCannotBeMutated() {
    var solver = solver(config());
    var result = solver.solveAndSummarize(TestdataSolution.generateSolution(3, 4));
    var snapshot = result.problemSizeStatistics();
    var live = solver.getSolverScope().getProblemSizeStatistics();
    assertThat(snapshot).isEqualTo(live).isNotSameAs(live);
    assertThatThrownBy(() -> snapshot.genuineEntityClassToEntityCount().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.genuineEntityClassToVariableToValueCount().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () ->
                snapshot.genuineEntityClassToVariableToValueCount().firstEntry().getValue().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    live.genuineEntityClassToEntityCount().clear();
    live.genuineEntityClassToVariableToValueCount().firstEntry().getValue().clear();
    live.genuineEntityClassToVariableToValueCount().clear();
    assertThat(snapshot.genuineEntityClassToEntityCount()).isNotEmpty();
    assertThat(snapshot.genuineEntityClassToVariableToValueCount()).isNotEmpty();
    assertThat(snapshot.genuineEntityClassToVariableToValueCount().firstEntry().getValue())
        .isNotEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void totalsIncludeAllSegmentsAndExcludeProblemChangeProcessing(String workers) {
    var clock = new MockClock(Clock.systemUTC());
    var solver = solver(new SolverConfig(clock).inherit(config()).withMoveThreadCount(workers));
    var scores = new ArrayList<Long>();
    var moves = new ArrayList<Long>();
    var times = new ArrayList<Long>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> stepScope) {
            clock.tick(Duration.ofMillis(10));
          }

          @Override
          public void solvingEnded(SolverScope<TestdataSolution> scope) {
            scores.add(scope.getReportedScoreCalculationCount());
            moves.add(scope.getReportedMoveEvaluationCount());
            times.add(scope.calculateTimeMillisSpentUpToNow());
            if (scores.size() == 1) {
              solver.addProblemChange(
                  (solution, director) -> {
                    clock.tick(Duration.ofSeconds(10));
                    director.addEntity(new TestdataEntity("added"), solution.getEntityList()::add);
                  });
            }
          }
        });
    var result = solver.solveAndSummarize(TestdataSolution.generateSolution(3, 4));
    assertThat(scores).hasSize(2);
    assertThat(result.scoreCalculationCount())
        .isEqualTo(scores.stream().mapToLong(Long::longValue).sum());
    assertThat(result.moveEvaluationCount())
        .isEqualTo(moves.stream().mapToLong(Long::longValue).sum());
    assertThat(result.solvingDuration())
        .isEqualTo(Duration.ofMillis(times.stream().mapToLong(Long::longValue).sum()));
    assertThat(result.scoreCalculationCount()).isGreaterThan(solver.getScoreCalculationCount());
    assertThat(result.problemSizeStatistics().entityCount()).isEqualTo(5);
  }

  @Test
  void uninitializedAndFailedRuns() {
    var problem = TestdataSolution.generateSolution(3, 4);
    problem.getEntityList().forEach(entity -> entity.setValue(null));
    var solver =
        solver(
            config()
                .withPhases(new ConstructionHeuristicPhaseConfig())
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
    assertThat(solver.solveAndSummarize(problem).isSolutionInitialized()).isFalse();

    var failing =
        solver(
            config()
                .withPhases(
                    new CustomPhaseConfig()
                        .withCustomPhaseCommands(
                            (PhaseCommand<TestdataSolution>)
                                context -> {
                                  throw new IllegalStateException("Expected failure.");
                                })));
    assertThatThrownBy(() -> failing.solveAndSummarize(TestdataSolution.generateSolution(3, 4)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Expected failure.");
  }

  @Test
  void jobAndFinalEventShareTheCapturedResult() throws Exception {
    var consumed = new AtomicReference<FinalBestSolutionEvent<TestdataSolution>>();
    try (var manager = SolverManager.<TestdataSolution>create(config())) {
      var job =
          manager
              .solveBuilder()
              .withProblemId(1L)
              .withProblem(TestdataSolution.generateSolution(3, 4))
              .withFinalBestSolutionEventConsumer(consumed::set)
              .run();
      var result = job.getResult();
      assertThat(job.getFinalBestSolution()).isSameAs(result.solution());
      assertThat(consumed.get()).isSameAs(result);
      assertThat(consumed.get().scoreCalculationCount()).isPositive();
      assertThat(consumed.get().moveEvaluationCount()).isPositive();
      assertThat(consumed.get().problemSizeStatistics()).isEqualTo(result.problemSizeStatistics());
    }
  }

  @Test
  void restartedJobPublishesFreshStartedSnapshotsAndWholeRunTotals() throws Exception {
    var firstStarted = new CountDownLatch(1);
    var restarted = new CountDownLatch(2);
    var startedSolutions = new CopyOnWriteArrayList<TestdataSolution>();
    try (var manager = SolverManager.<TestdataSolution>create(config().withDaemon(true))) {
      var job =
          manager
              .solveBuilder()
              .withProblemId(1L)
              .withProblem(TestdataSolution.generateSolution(3, 4))
              .withBestSolutionEventConsumer(event -> {})
              .withSolverJobStartedEventConsumer(
                  event -> {
                    startedSolutions.add(event.solution());
                    firstStarted.countDown();
                    restarted.countDown();
                  })
              .run();
      assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
      var change =
          job.addProblemChange(
              (solution, director) ->
                  director.addEntity(new TestdataEntity("added"), solution.getEntityList()::add));
      assertThat(restarted.await(5, TimeUnit.SECONDS)).isTrue();
      // A restart-started event precedes initialization of the newly added entity. Wait until an
      // initialized best containing the change is consumed before terminating the daemon solver.
      change.get(5, TimeUnit.SECONDS);
      job.terminateEarly();
      var result = job.getResult();
      assertThat(startedSolutions).hasSize(2);
      assertThat(startedSolutions.getFirst().getEntityList()).hasSize(4);
      assertThat(startedSolutions.getLast().getEntityList()).hasSize(5);
      assertThat(startedSolutions.getFirst()).isNotSameAs(startedSolutions.getLast());
      assertThat(result.scoreCalculationCount()).isGreaterThan(job.getScoreCalculationCount());
      assertThat(result.problemSizeStatistics().entityCount()).isEqualTo(5);
    }
  }

  private static SolverConfig config() {
    return PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
        .withPhases(
            new ConstructionHeuristicPhaseConfig(),
            new LocalSearchPhaseConfig()
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(3)));
  }

  private static DefaultSolver<TestdataSolution> solver(SolverConfig config) {
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }
}
