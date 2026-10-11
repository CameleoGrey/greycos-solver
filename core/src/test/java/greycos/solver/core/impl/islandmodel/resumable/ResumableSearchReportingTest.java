package greycos.solver.core.impl.islandmodel.resumable;

import static greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.sessions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.monitoring.IslandWorkAccounting;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSample;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSamples;
import greycos.solver.core.impl.solver.monitoring.SolverWorkSnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("The counting calculator independently observes score work across all resident owners.")
@Timeout(60)
class ResumableSearchReportingTest {

  @Test
  void summaryMoveTypesRemainImmutableAndRetiredEpisodesAreCountedOnce() {
    var counters = new HashMap<>(Map.of("change", 2L));
    var first = summary(2L, counters);
    counters.put("change", 5L);
    var later = summary(5L, counters);
    assertThat(first.moveCountsByType()).containsExactlyEntriesOf(Map.of("change", 2L));
    assertThatThrownBy(() -> first.moveCountsByType().put("change", 99L))
        .isInstanceOf(UnsupportedOperationException.class);
    var accounting = new IslandWorkAccounting();
    accounting.register("episode-0");
    for (var summary : List.of(first, first, later)) {
      accounting.publish(
          "episode-0",
          new SolverWorkSnapshot(
              summary.scoreCalculations(), summary.moveEvaluations(), summary.moveCountsByType()));
    }
    accounting.retire("episode-0");
    accounting.retire("episode-0");
    accounting.register("episode-1");
    accounting.publish("episode-1", new SolverWorkSnapshot(3, 3, Map.of("swap", 3L)));
    accounting.retire("episode-1");
    assertThat(accounting.snapshot())
        .isEqualTo(new SolverWorkSnapshot(8, 8, Map.of("change", 5L, "swap", 3L)));
  }

  @Test
  void reportsEveryRevisionAndProblemChangeScoreOnceAndStartsFreshOnReuse() throws Exception {
    var config = config(1_000);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    config.withPhases(resident(search, 16));
    var solver = build(config);
    var samples = new ArrayList<SolverMetricSample>();
    solver.getSolverScope().addMetricSampleListener(samples::add);
    var publications = new ArrayList<SolverWorkSnapshot>();
    solver.addEventListener(
        event -> publications.add(SolverMetricSamples.reportedWork(solver.getSolverScope())));
    SolverWorkSnapshot previousRun = null;
    for (int run = 0; run < 2; run++) {
      CountingCalculator.calls.set(0);
      samples.clear();
      publications.clear();
      var revisions = new HashSet<Long>();
      var applied = new AtomicInteger();
      try (var ignored =
          ResumableSearchRuntime.observeRounds(
              round -> {
                publications.add(SolverMetricSamples.reportedWork(solver.getSolverScope()));
                if (revisions.add(round.modelRevision()) && round.modelRevision() < 2) {
                  solver.addProblemChange((working, director) -> applied.incrementAndGet());
                }
              },
              false)) {
        solveAndReplay(solver);
      }
      assertThat(applied).hasValue(2);
      assertThat(revisions).containsExactlyInAnyOrder(0L, 1L, 2L);
      var scope = solver.getSolverScope();
      var finalWork = SolverMetricSamples.reportedWork(scope);
      assertThat(finalWork.scoreCalculationCount()).isEqualTo(CountingCalculator.calls.get());
      assertThat(finalWork.scoreCalculationCount())
          .isEqualTo(scope.getResumableSearchState().solverScoreCalculationCount(scope));
      assertThat(finalWork.moveEvaluationCount())
          .isEqualTo(scope.getResumableSearchState().solverMoveEvaluationCount(scope));
      assertMoveTypesMatchTotal(finalWork);
      assertMonotonic(publications);
      assertMonotonic(
          samples.stream()
              .filter(sample -> sample.source().equals("root"))
              .map(SolverMetricSample::work)
              .toList());
      assertThat(samples.getLast().kind()).isEqualTo(SolverMetricSample.Kind.FINAL);
      assertThat(samples.getLast().work()).isEqualTo(finalWork);
      if (previousRun != null) assertThat(finalWork).isEqualTo(previousRun);
      previousRun = finalWork;
    }
  }

  @Test
  void retainsOrdinaryLegacyAndResidentWorkAcrossTheSameRevisionBoundary() throws Exception {
    var config = config(2);
    var ordinary = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    var legacy =
        new IslandModelPhaseConfig()
            .withIslandCount(2)
            .withMoveThreadCount("NONE")
            .withPhaseConfigList(List.of(ordinary.copyConfig()));
    config.withPhases(
        ordinary,
        legacy,
        resident(
            ordinary
                .copyConfig()
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(1_000)),
            12));
    var solver = build(config);
    var submitted = new AtomicBoolean();
    var applied = new AtomicInteger();
    var publications = new ArrayList<SolverWorkSnapshot>();
    CountingCalculator.calls.set(0);
    try (var ignored =
        ResumableSearchRuntime.observeRounds(
            round -> {
              publications.add(SolverMetricSamples.reportedWork(solver.getSolverScope()));
              if (submitted.compareAndSet(false, true))
                solver.addProblemChange((working, director) -> applied.incrementAndGet());
            },
            false)) {
      solveAndReplay(solver);
    }
    assertThat(applied).hasValue(1);
    var finalWork = SolverMetricSamples.reportedWork(solver.getSolverScope());
    assertThat(finalWork.scoreCalculationCount()).isEqualTo(CountingCalculator.calls.get());
    assertMoveTypesMatchTotal(finalWork);
    assertMonotonic(publications);
  }

  @Test
  void reportsWorkWhenProblemChangePrecedesTheFirstResidentPhase() throws Exception {
    var config = config(1);
    var ordinary = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    config
        .withPhases(ordinary, resident(ordinary.copyConfig(), 4))
        .withTerminationConfig(new TerminationConfig().withMoveCountLimit(2L));
    var solver = build(config);
    var applied = requestChangeAfterFirstOrdinaryStep(solver);
    var rounds = new AtomicInteger();
    CountingCalculator.calls.set(0);
    try (var ignored =
        ResumableSearchRuntime.observeRounds(round -> rounds.incrementAndGet(), false)) {
      solveAndReplay(solver);
    }
    assertThat(applied).hasValue(1);
    assertThat(rounds).hasValue(0);
    var finalWork = SolverMetricSamples.reportedWork(solver.getSolverScope());
    assertThat(finalWork.moveEvaluationCount()).isEqualTo(2);
    assertThat(finalWork.scoreCalculationCount()).isEqualTo(CountingCalculator.calls.get());
    assertMoveTypesMatchTotal(finalWork);
  }

  @Test
  void ordinaryRestartKeepsItsExistingPerRunReporting() {
    var solver = build(config(1));
    var applied = requestChangeAfterFirstOrdinaryStep(solver);
    var movesPerRun = new ArrayList<Long>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<BasicSolution> step) {
            if (step instanceof LocalSearchStepScope<BasicSolution> localStep) {
              movesPerRun.add(localStep.getSelectedMoveCount());
            }
          }
        });
    CountingCalculator.calls.set(0);
    solveAndReplay(solver);
    assertThat(applied).hasValue(1);
    var scope = solver.getSolverScope();
    var finalWork = SolverMetricSamples.reportedWork(scope);
    assertThat(scope.isResumableSearchEnabled()).isFalse();
    assertThat(movesPerRun).hasSize(2).allMatch(count -> count > 0);
    assertThat(finalWork.moveEvaluationCount()).isEqualTo(movesPerRun.getLast());
    assertThat(finalWork.moveEvaluationCount())
        .isLessThan(movesPerRun.stream().mapToLong(Long::longValue).sum());
    assertThat(finalWork.scoreCalculationCount()).isEqualTo(scope.getScoreCalculationCount());
    assertThat(finalWork.scoreCalculationCount()).isLessThan(CountingCalculator.calls.get());
    assertMoveTypesMatchTotal(finalWork);
  }

  private static AtomicInteger requestChangeAfterFirstOrdinaryStep(
      DefaultSolver<BasicSolution> solver) {
    var submitted = new AtomicBoolean();
    var applied = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<BasicSolution> step) {
            if (step instanceof LocalSearchStepScope
                && step.getPhaseScope().getSolverScope() == solver.getSolverScope()
                && submitted.compareAndSet(false, true)) {
              solver.addProblemChange((working, director) -> applied.incrementAndGet());
            }
          }
        });
    return applied;
  }

  private static SolverConfig config(int steps) {
    return ResumableSearchRuntimeTest.config(new BasicWorkload(), "la", 37, steps)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withScoreDirectorFactory(
            new ScoreDirectorFactoryConfig().withEasyScoreCalculatorClass(CountingCalculator.class))
        .withMonitoringConfig(
            new MonitoringConfig()
                .withSolverMetricList(
                    List.of(
                        SolverMetric.SCORE_CALCULATION_COUNT,
                        SolverMetric.MOVE_EVALUATION_COUNT,
                        SolverMetric.MOVE_COUNT_PER_TYPE)));
  }

  private static ResumableSearchRuntime.SessionSummary<BasicSolution> summary(
      long moves, Map<String, Long> moveTypes) {
    return new ResumableSearchRuntime.SessionSummary<>(
        "session",
        0L,
        1L,
        moves,
        moves,
        InnerScore.fullyAssigned(SimpleScore.ZERO),
        null,
        List.of(),
        false,
        0L,
        Map.of(),
        1L,
        moveTypes);
  }

  private static IslandModelPhaseConfig resident(LocalSearchPhaseConfig search, int steps) {
    return sessions(search, 2, 2, 1, ResumableSearchCooperationPolicy.NONE)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  private static DefaultSolver<BasicSolution> build(SolverConfig config) {
    return (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
  }

  private static void solveAndReplay(DefaultSolver<BasicSolution> solver) {
    var workload = new BasicWorkload();
    var result = solver.solve(workload.createProblem(24));
    assertThat(result.getScore()).isEqualTo(workload.recompute(result));
  }

  private static void assertMoveTypesMatchTotal(SolverWorkSnapshot work) {
    assertThat(work.moveCountsByType()).isNotEmpty();
    assertThat(work.moveCountsByType().values()).allMatch(count -> count > 0);
    assertThat(work.moveCountsByType().values().stream().mapToLong(Long::longValue).sum())
        .isEqualTo(work.moveEvaluationCount());
  }

  private static void assertMonotonic(List<SolverWorkSnapshot> snapshots) {
    assertThat(snapshots).isNotEmpty();
    var previous = SolverWorkSnapshot.ZERO;
    for (var snapshot : snapshots) {
      assertThat(snapshot.scoreCalculationCount())
          .isGreaterThanOrEqualTo(previous.scoreCalculationCount());
      assertThat(snapshot.moveEvaluationCount())
          .isGreaterThanOrEqualTo(previous.moveEvaluationCount());
      for (var entry : previous.moveCountsByType().entrySet()) {
        assertThat(snapshot.moveCountsByType().getOrDefault(entry.getKey(), 0L))
            .isGreaterThanOrEqualTo(entry.getValue());
      }
      previous = snapshot;
    }
  }

  public static final class CountingCalculator
      implements EasyScoreCalculator<BasicSolution, SimpleScore> {
    private static final AtomicLong calls = new AtomicLong();

    @Override
    public SimpleScore calculateScore(BasicSolution solution) {
      calls.incrementAndGet();
      return new BasicWorkload().recompute(solution);
    }
  }
}
