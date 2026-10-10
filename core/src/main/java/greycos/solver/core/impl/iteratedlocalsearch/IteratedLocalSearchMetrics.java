package greycos.solver.core.impl.iteratedlocalsearch;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.monitoring.ScoreLevels;
import greycos.solver.core.impl.solver.monitoring.SolverMetricUtil;
import greycos.solver.core.impl.solver.scope.SolverScope;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;

/** Metrics for committed primitive steps; restoration and adoption do not fabricate move counts. */
public final class IteratedLocalSearchMetrics<Solution_> {
  private enum Statistic {
    COMPLETED("iterations.completed", scope -> scope.completedIterations),
    ACCEPTED("iterations.accepted", scope -> scope.acceptedIterations),
    REJECTED("iterations.rejected", scope -> scope.rejectedIterations),
    INTERRUPTED("iterations.interrupted", scope -> scope.interruptedIterations),
    FAILED("perturbations.failed", scope -> scope.failedPerturbations),
    NO_CHANGE("perturbations.no.change", scope -> scope.noChangeCount),
    MIGRANTS("migrants", scope -> scope.migrantRestarts),
    EPISODES("episodes", scope -> scope.episodes),
    INITIAL_EPISODES("episodes.initial", scope -> scope.initialEpisodes),
    PERTURBATION_ATTEMPTS("attempts.perturbation", scope -> scope.perturbationAttempts),
    EPISODE_ATTEMPTS("attempts.episode", scope -> scope.episodeAttempts),
    SPECULATIVE_ATTEMPTS("attempts.speculative", scope -> scope.speculativeSelectionAttempts);

    private static final Statistic[] VALUES = values();
    private final String suffix;
    private final ToLongFunction<IteratedLocalSearchPhaseScope<?>> value;

    Statistic(String suffix, ToLongFunction<IteratedLocalSearchPhaseScope<?>> value) {
      this.suffix = suffix;
      this.value = value;
    }
  }

  private final AtomicLong selectedCount = new AtomicLong();
  private final AtomicLong acceptedCount = new AtomicLong();
  private final Map<Tags, ScoreLevels> stepConstraintScores = new HashMap<>();
  private final Map<Tags, ScoreLevels> bestConstraintScores = new HashMap<>();
  private final Map<String, AtomicLong> constraintCounts = new HashMap<>();
  private Map<Statistic, AtomicLong> statistics;

  public void phaseStarted(IteratedLocalSearchPhaseScope<Solution_> phaseScope) {
    phaseScope.getSolverScope().getMetricRun().publish(() -> phaseStartedActive(phaseScope));
  }

  private void phaseStartedActive(IteratedLocalSearchPhaseScope<Solution_> phaseScope) {
    var solverScope = phaseScope.getSolverScope();
    selectedCount.set(0L);
    acceptedCount.set(0L);
    statistics = null;
    if (solverScope.isMetricEnabled(SolverMetric.ITERATED_LOCAL_SEARCH_STATISTICS)) {
      statistics = new EnumMap<>(Statistic.class);
      var tags =
          solverScope
              .getMonitoringTags()
              .and("phase.index", Integer.toString(phaseScope.getPhaseIndex()));
      for (var statistic : Statistic.VALUES) {
        var value = new AtomicLong();
        statistics.put(statistic, value);
        var name =
            SolverMetric.ITERATED_LOCAL_SEARCH_STATISTICS.getMeterId() + "." + statistic.suffix;
        Metrics.globalRegistry.removeByPreFilterId(
            new Meter.Id(name, tags, null, null, Meter.Type.GAUGE));
        // Retain only the immutable meter identity and its counter after an island is released.
        Gauge.builder(name, value, AtomicLong::doubleValue)
            .tags(tags)
            .strongReference(true)
            .register(Metrics.globalRegistry);
      }
      updateActive(phaseScope);
    }
    if (solverScope.isMetricEnabled(SolverMetric.MOVE_COUNT_PER_STEP)) {
      SolverMetricUtil.rebindGauge(
          SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".selected",
          solverScope.getMonitoringTags(),
          selectedCount);
      SolverMetricUtil.rebindGauge(
          SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".accepted",
          solverScope.getMonitoringTags(),
          acceptedCount);
    }
    sampleConstraints(
        phaseScope,
        true,
        phaseScope.getLastCompletedStepScope().getScore().equals(phaseScope.getBestScore()));
  }

  public void record(IteratedLocalSearchStepScope<Solution_> stepScope) {
    stepScope
        .getPhaseScope()
        .getSolverScope()
        .getMetricRun()
        .publish(() -> recordActive(stepScope));
  }

  private void recordActive(IteratedLocalSearchStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var solverScope = phaseScope.getSolverScope();
    selectedCount.set(stepScope.getSelectedMoveCount());
    acceptedCount.set(stepScope.getAcceptedMoveCount());
    updateActive(phaseScope);
    var interval = solverScope.getConstraintMatchMetricSampleInterval();
    var sampleStep = interval <= 1 || (stepScope.getStepIndex() + 1) % interval == 0;
    sampleConstraints(
        phaseScope, sampleStep, Boolean.TRUE.equals(stepScope.getBestScoreImproved()));
  }

  public void phaseEnded(IteratedLocalSearchPhaseScope<Solution_> phaseScope) {
    phaseScope.getSolverScope().getMetricRun().publish(() -> phaseEndedActive(phaseScope));
  }

  private void phaseEndedActive(IteratedLocalSearchPhaseScope<Solution_> phaseScope) {
    updateActive(phaseScope);
    sampleConstraints(phaseScope, true, false);
  }

  /**
   * Publishes cumulative outcomes after an episode, iteration, or migration updates its counters.
   * This administrative publication emits no primitive step and is allocation-free when disabled.
   */
  public void update(IteratedLocalSearchPhaseScope<Solution_> phaseScope) {
    if (statistics == null) {
      return;
    }
    phaseScope.getSolverScope().getMetricRun().publish(() -> updateActive(phaseScope));
  }

  private void updateActive(IteratedLocalSearchPhaseScope<Solution_> phaseScope) {
    if (statistics != null) {
      for (var statistic : Statistic.VALUES) {
        statistics.get(statistic).set(statistic.value.applyAsLong(phaseScope));
      }
    }
  }

  /**
   * Refreshes score summaries after migration without attributing work or reward to an operator.
   */
  public void incumbentChanged(
      IteratedLocalSearchPhaseScope<Solution_> phaseScope, boolean bestImproved) {
    phaseScope
        .getSolverScope()
        .getMetricRun()
        .publish(() -> incumbentChangedActive(phaseScope, bestImproved));
  }

  private void incumbentChangedActive(
      IteratedLocalSearchPhaseScope<Solution_> phaseScope, boolean bestImproved) {
    updateActive(phaseScope);
    sampleConstraints(phaseScope, true, bestImproved);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void sampleConstraints(
      IteratedLocalSearchPhaseScope<Solution_> phaseScope, boolean sampleStep, boolean sampleBest) {
    var solverScope = phaseScope.getSolverScope();
    var stepEnabled =
        sampleStep && solverScope.isMetricEnabled(SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE);
    var bestEnabled =
        sampleBest && solverScope.isMetricEnabled(SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE);
    if ((!stepEnabled && !bestEnabled)
        || !phaseScope.getScoreDirector().getConstraintMatchPolicy().isEnabled()) {
      return;
    }
    var scoreDefinition = solverScope.getScoreDefinition();
    for (var total : phaseScope.getScoreDirector().getConstraintMatchTotalMap().values()) {
      var tags =
          solverScope.getMonitoringTags().and("constraint.id", total.getConstraintRef().id());
      if (stepEnabled) {
        registerConstraint(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE, tags, total.getConstraintMatchCount());
        prepareConstraintScoreMeters(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE,
            tags,
            stepConstraintScores,
            solverScope);
        SolverMetricUtil.registerScore(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE,
            tags,
            scoreDefinition,
            stepConstraintScores,
            InnerScore.fullyAssigned((Score) total.getScore()));
      }
      if (bestEnabled) {
        registerConstraint(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE, tags, total.getConstraintMatchCount());
        prepareConstraintScoreMeters(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE,
            tags,
            bestConstraintScores,
            solverScope);
        SolverMetricUtil.registerScore(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE,
            tags,
            scoreDefinition,
            bestConstraintScores,
            InnerScore.fullyAssigned((Score) total.getScore()));
      }
    }
  }

  private void prepareConstraintScoreMeters(
      SolverMetric metric, Tags tags, Map<Tags, ScoreLevels> scores, SolverScope<Solution_> scope) {
    if (scores.containsKey(tags)) {
      return;
    }
    var labels = new java.util.ArrayList<String>();
    labels.add("unassigned.count");
    for (var label : scope.getScoreDefinition().getLevelLabels()) {
      labels.add(label.replace(' ', '.'));
    }
    for (var label : labels) {
      var existing =
          Metrics.globalRegistry
              .find(SolverMetricUtil.getGaugeName(metric, label))
              .tags(tags)
              .gauge();
      if (existing != null) {
        Metrics.globalRegistry.remove(existing);
      }
    }
  }

  private void registerConstraint(SolverMetric metric, Tags tags, long count) {
    var name = metric.getMeterId() + ".count";
    var key = name + tags;
    var value =
        constraintCounts.computeIfAbsent(
            key,
            ignored -> {
              var counter = new AtomicLong();
              SolverMetricUtil.rebindGauge(name, tags, counter);
              return counter;
            });
    value.set(count);
  }
}
