package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSamples;
import greycos.solver.core.impl.solver.monitoring.SolverTags;
import greycos.solver.core.impl.solver.monitoring.statistic.PickedMoveBestScoreDiffStatistic;
import greycos.solver.core.impl.solver.monitoring.statistic.PickedMoveStepScoreDiffStatistic;
import greycos.solver.core.impl.solver.monitoring.statistic.SolverStatistic;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class IteratedLocalSearchMetricsTest {

  @Test
  void cumulativeOutcomesAndAttemptsUseFixedPhaseAndIslandTags() {
    try (var fixture = new StatisticsFixture(true)) {
      var phase = fixture.phase;
      var metrics = fixture.metrics;
      phase.completedIterations = 3;
      phase.acceptedIterations = 1;
      phase.rejectedIterations = 1;
      phase.failedPerturbations = 1;
      phase.noChangeCount = 1;
      phase.interruptedIterations = 2;
      phase.migrantRestarts = 1;
      phase.episodes = 6;
      phase.initialEpisodes = 2;
      phase.perturbationAttempts = 11;
      phase.episodeAttempts = 29;
      phase.speculativeSelectionAttempts = 7;
      metrics.update(phase);
      assertThat(fixture.values())
          .containsExactlyInAnyOrderEntriesOf(
              Map.ofEntries(
                  Map.entry("iterations.completed", 3.0), Map.entry("iterations.accepted", 1.0),
                  Map.entry("iterations.rejected", 1.0), Map.entry("iterations.interrupted", 2.0),
                  Map.entry("perturbations.failed", 1.0), Map.entry("perturbations.no.change", 1.0),
                  Map.entry("migrants", 1.0), Map.entry("episodes", 6.0),
                  Map.entry("episodes.initial", 2.0), Map.entry("attempts.perturbation", 11.0),
                  Map.entry("attempts.episode", 29.0), Map.entry("attempts.speculative", 7.0)));
      fixture.registry.getMeters().stream()
          .filter(meter -> meter.getId().getName().startsWith(fixture.prefix))
          .filter(meter -> fixture.problemId.equals(meter.getId().getTag("problem.id")))
          .forEach(
              meter -> assertThat(meter.getId().getTags()).containsExactlyElementsOf(fixture.tags));

      // Island samples retain immutable values even after the owner advances its counters.
      fixture.scope.addMetricSampleListener(ignored -> {});
      var sample = SolverMetricSamples.captureFinal(fixture.scope, "phase-0/island-1");
      phase.episodeAttempts++;
      metrics.phaseEnded(phase);
      assertThat(fixture.values()).containsEntry("attempts.episode", 30.0);
      assertThat(sample.gaugeValue(fixture.prefix + "attempts.episode", fixture.tags))
          .isEqualTo(29.0);
    }
  }

  @Test
  void zeroMoveAdoptionPublishesStatisticsWithoutPrimitiveStepEvents() {
    try (var fixture = new StatisticsFixture(true)) {
      var samples = new AtomicInteger();
      fixture.scope.addMetricSampleListener(ignored -> samples.incrementAndGet());
      fixture.phase.migrantRestarts = 1;
      fixture.phase.initialEpisodes = 2;
      fixture.phase.episodes = 2;
      fixture.metrics.incumbentChanged(fixture.phase, true);
      assertThat(fixture.values())
          .containsEntry("migrants", 1.0)
          .containsEntry("episodes.initial", 2.0)
          .containsEntry("iterations.completed", 0.0);
      assertThat(fixture.phase.getLastCompletedStepScope().getStepIndex()).isEqualTo(-1);
      assertThat(samples).hasValue(0);
      var sample = SolverMetricSamples.captureFinal(fixture.scope, "phase-0/island-1");
      assertThat(sample.gaugeValue(fixture.prefix + "migrants", fixture.tags)).isEqualTo(1.0);
    }
  }

  @Test
  void disabledStatisticsDoNotRegisterMeters() {
    try (var fixture = new StatisticsFixture(false)) {
      fixture.phase.acceptedIterations = 1;
      fixture.metrics.update(fixture.phase);
      fixture.metrics.incumbentChanged(fixture.phase, true);
      fixture.metrics.phaseEnded(fixture.phase);
      assertThat(fixture.values()).isEmpty();
    }
  }

  @Test
  void newPhaseRuntimeRebindsCountersAndSealedRunsCannotUpdateThem() {
    try (var fixture = new StatisticsFixture(true)) {
      fixture.phase.acceptedIterations = 4;
      fixture.metrics.update(fixture.phase);
      var nextPhase = new IteratedLocalSearchPhaseScope<>(fixture.scope, 2);
      nextPhase.getLastCompletedStepScope().setScore(InnerScore.fullyAssigned(SimpleScore.ZERO));
      var nextMetrics = new IteratedLocalSearchMetrics<TestdataSolution>();
      nextMetrics.phaseStarted(nextPhase);
      assertThat(fixture.values()).containsEntry("iterations.accepted", 0.0).hasSize(12);
      fixture.phase.acceptedIterations = 5;
      fixture.metrics.update(fixture.phase);
      assertThat(fixture.values()).containsEntry("iterations.accepted", 0.0);
      nextPhase.acceptedIterations = 2;
      nextMetrics.update(nextPhase);
      fixture.scope.getMetricRun().seal();
      nextPhase.acceptedIterations = 3;
      nextMetrics.update(nextPhase);
      assertThat(fixture.values()).containsEntry("iterations.accepted", 2.0);
    }
  }

  private static final class StatisticsFixture implements AutoCloseable {
    private final String problemId = UUID.randomUUID().toString();
    private final String prefix = SolverMetric.ITERATED_LOCAL_SEARCH_STATISTICS.getMeterId() + ".";
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SolverScope<TestdataSolution> scope = new SolverScope<>();
    private final IteratedLocalSearchPhaseScope<TestdataSolution> phase;
    private final IteratedLocalSearchMetrics<TestdataSolution> metrics =
        new IteratedLocalSearchMetrics<>();
    private final Tags tags;

    @SuppressWarnings("unchecked")
    private StatisticsFixture(boolean enabled) {
      Metrics.addRegistry(registry);
      scope.setScoreDirector(mock(InnerScoreDirector.class));
      scope.setMonitoringTags(SolverTags.withProblemId(problemId).asTags().and("island.id", "1"));
      scope.setSolverMetricSet(
          enabled
              ? EnumSet.of(SolverMetric.ITERATED_LOCAL_SEARCH_STATISTICS)
              : EnumSet.noneOf(SolverMetric.class));
      scope.setBestScore(InnerScore.fullyAssigned(SimpleScore.ZERO));
      scope.startingNow();
      phase = new IteratedLocalSearchPhaseScope<>(scope, 2);
      phase.getLastCompletedStepScope().setScore(InnerScore.fullyAssigned(SimpleScore.ZERO));
      tags = scope.getMonitoringTags().and("phase.index", "2");
      metrics.phaseStarted(phase);
    }

    private Map<String, Double> values() {
      var values = new LinkedHashMap<String, Double>();
      registry.getMeters().stream()
          .filter(meter -> meter.getId().getName().startsWith(prefix))
          .filter(meter -> problemId.equals(meter.getId().getTag("problem.id")))
          .forEach(
              meter ->
                  values.put(
                      meter.getId().getName().substring(prefix.length()),
                      registry.get(meter.getId().getName()).tags(tags).gauge().value()));
      return values;
    }

    @Override
    public void close() {
      Metrics.removeRegistry(registry);
      Metrics.globalRegistry.getMeters().stream()
          .filter(meter -> problemId.equals(meter.getId().getTag("problem.id")))
          .toList()
          .forEach(Metrics.globalRegistry::remove);
      registry.close();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void primitiveMetricsUseStartingWorkingAndBestScoresAfterAdministrativeChanges() {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
    var realSolver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var scope = realSolver.getSolverScope();
    var tag = UUID.randomUUID().toString();
    scope.setMonitoringTags(SolverTags.withProblemId(tag).asTags());
    scope.setSolverMetricSet(EnumSet.of(SolverMetric.MOVE_COUNT_PER_STEP));
    scope.startingNow();
    scope.setInitialSolution(TestdataSolution.generateSolution(2, 3));
    var mockSolver = (DefaultSolver<TestdataSolution>) mock(DefaultSolver.class);
    when(mockSolver.getSolverScope()).thenReturn(scope);
    when(mockSolver.getScoreDirectorFactory()).thenReturn(realSolver.getScoreDirectorFactory());
    var stepStatistic = new PickedMoveStepScoreDiffStatistic<TestdataSolution>();
    var bestStatistic = new PickedMoveBestScoreDiffStatistic<TestdataSolution, SimpleScore>();
    var stepListener = register(stepStatistic, mockSolver);
    // A separate mock makes each captured listener unambiguous.
    var otherSolver = (DefaultSolver<TestdataSolution>) mock(DefaultSolver.class);
    when(otherSolver.getSolverScope()).thenReturn(scope);
    when(otherSolver.getScoreDirectorFactory()).thenReturn(realSolver.getScoreDirectorFactory());
    var bestListener = register(bestStatistic, otherSolver);
    var registry = new SimpleMeterRegistry();
    Metrics.addRegistry(registry);
    try (var ignored = scope.getScoreDirector()) {
      scope.setBestScore(InnerScore.fullyAssigned(SimpleScore.of(-4)));
      var phase = new IteratedLocalSearchPhaseScope<>(scope, 0);
      phase.getLastCompletedStepScope().setScore(InnerScore.fullyAssigned(SimpleScore.of(-8)));
      var metrics = new IteratedLocalSearchMetrics<TestdataSolution>();
      metrics.phaseStarted(phase);
      var move = (Move<TestdataSolution>) mock(Move.class);
      when(move.describe()).thenReturn("TestMove");
      var step =
          new IteratedLocalSearchStepScope<>(
              phase, IteratedLocalSearchStepScope.Origin.PERTURBATION);
      step.setMove(move);
      step.setScore(InnerScore.fullyAssigned(SimpleScore.of(-6)));
      step.setBestScoreImproved(false);
      step.setSelectedMoveCount(3);
      step.setAcceptedMoveCount(1);
      metrics.record(step);
      stepListener.stepEnded(step);
      bestListener.stepEnded(step);
      assertThat(value(registry, SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF, tag)).isEqualTo(2);
      assertThat(
              registry
                  .find(SolverMetric.PICKED_MOVE_TYPE_BEST_SCORE_DIFF.getMeterId() + ".score")
                  .tag("problem.id", tag)
                  .gauge())
          .isNull();

      // Migration/restoration changes the next step baseline without emitting a move statistic.
      scope.setBestScore(InnerScore.fullyAssigned(SimpleScore.ZERO));
      phase.getLastCompletedStepScope().setScore(InnerScore.fullyAssigned(SimpleScore.of(-1)));
      metrics.incumbentChanged(phase, true);
      assertThat(
              registry
                  .get(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".selected")
                  .tag("problem.id", tag)
                  .gauge()
                  .value())
          .isEqualTo(3);
      var next =
          new IteratedLocalSearchStepScope<>(
              phase, IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH);
      next.setMove(move);
      next.setScore(InnerScore.fullyAssigned(SimpleScore.of(2)));
      next.setBestScoreImproved(true);
      next.setSelectedMoveCount(5);
      next.setAcceptedMoveCount(2);
      metrics.record(next);
      stepListener.stepEnded(next);
      bestListener.stepEnded(next);
      assertThat(value(registry, SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF, tag)).isEqualTo(3);
      assertThat(value(registry, SolverMetric.PICKED_MOVE_TYPE_BEST_SCORE_DIFF, tag)).isEqualTo(2);
      assertThat(
              registry
                  .get(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".selected")
                  .tag("problem.id", tag)
                  .gauge()
                  .value())
          .isEqualTo(5);
      assertThat(
              registry
                  .get(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".accepted")
                  .tag("problem.id", tag)
                  .gauge()
                  .value())
          .isEqualTo(2);
      var sample = SolverMetricSamples.captureStep(scope, next, "phase-0/island-0");
      assertThat(sample.moveType()).isEqualTo("TestMove");
      assertThat(sample.stepScore()).isEqualTo(next.getScore());
      metrics.phaseEnded(phase);
    } finally {
      stepStatistic.unregister(mockSolver);
      bestStatistic.unregister(otherSolver);
      Metrics.removeRegistry(registry);
      Metrics.globalRegistry.getMeters().stream()
          .filter(meter -> tag.equals(meter.getId().getTag("problem.id")))
          .toList()
          .forEach(Metrics.globalRegistry::remove);
      registry.close();
    }
  }

  private static double value(SimpleMeterRegistry registry, SolverMetric metric, String tag) {
    return registry
        .get(metric.getMeterId() + ".score")
        .tag("problem.id", tag)
        .tag("move.type", "TestMove")
        .gauge()
        .value();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static PhaseLifecycleListener<TestdataSolution> register(
      SolverStatistic<TestdataSolution> statistic, DefaultSolver<TestdataSolution> solver) {
    statistic.register(solver);
    var captor = ArgumentCaptor.forClass(PhaseLifecycleListener.class);
    verify(solver).addPhaseLifecycleListener(captor.capture());
    return captor.getValue();
  }
}
