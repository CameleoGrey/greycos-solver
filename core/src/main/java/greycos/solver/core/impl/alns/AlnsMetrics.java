package greycos.solver.core.impl.alns;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.monitoring.ScoreLevels;
import greycos.solver.core.impl.solver.monitoring.SolverMetricUtil;
import greycos.solver.core.impl.solver.scope.SolverScope;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;

/** Per-phase metric state. Repair probes are reported separately from outer proposals. */
public final class AlnsMetrics<Solution_> {
  private final AtomicLong selectedCount = new AtomicLong();
  private final AtomicLong acceptedCount = new AtomicLong();
  private final Map<Tags, ScoreLevels> stepConstraintScores = new HashMap<>();
  private final Map<Tags, ScoreLevels> bestConstraintScores = new HashMap<>();
  private final Map<String, AtomicLong> constraintCounts = new HashMap<>();
  private final Map<Tags, AtomicReference<Double>> operatorWeights = new HashMap<>();
  private Tags phaseTags;

  public void phaseStarted(AlnsPhaseScope<Solution_> phaseScope) {
    var solverScope = phaseScope.getSolverScope();
    phaseTags =
        solverScope
            .getMonitoringTags()
            .and("phase.index", Integer.toString(phaseScope.getPhaseIndex()));
    selectedCount.set(0L);
    acceptedCount.set(0L);
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

  public void record(AlnsStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var solverScope = phaseScope.getSolverScope();
    selectedCount.set(1L);
    acceptedCount.set(stepScope.isAccepted() ? 1L : 0L);
    if (solverScope.isMetricEnabled(SolverMetric.ALNS_STATISTICS)) {
      var tags =
          phaseTags
              .and("operator.pair", stepScope.getOperatorPairId())
              .and("destroy.id", stepScope.getTrialResult().destroyId())
              .and("repair.id", stepScope.getTrialResult().repairId());
      Metrics.counter(
              SolverMetric.ALNS_STATISTICS.getMeterId() + ".trials",
              tags.and("outcome", stepScope.getOutcome().name()))
          .increment();
      Metrics.counter(SolverMetric.ALNS_STATISTICS.getMeterId() + ".probes", tags)
          .increment(stepScope.getProbeCount());
      Metrics.counter(SolverMetric.ALNS_STATISTICS.getMeterId() + ".destroyed", tags)
          .increment(stepScope.getDestroyedCount());
      Metrics.counter(SolverMetric.ALNS_STATISTICS.getMeterId() + ".recovered", tags)
          .increment(stepScope.getRecoveryCount());
      Metrics.timer(SolverMetric.ALNS_STATISTICS.getMeterId() + ".repair.duration", tags)
          .record(stepScope.getElapsedNanos(), TimeUnit.NANOSECONDS);
    }
    var interval = solverScope.getConstraintMatchMetricSampleInterval();
    var sampleStep = interval <= 1 || (stepScope.getStepIndex() + 1) % interval == 0;
    sampleConstraints(
        phaseScope, sampleStep, Boolean.TRUE.equals(stepScope.getBestScoreImproved()));
  }

  public void recordWeights(SolverScope<Solution_> solverScope, Map<String, Double> weights) {
    if (!solverScope.isMetricEnabled(SolverMetric.ALNS_STATISTICS)) {
      return;
    }
    weights.forEach(
        (id, weight) -> {
          var tags = phaseTags.and("operator.id", id);
          var value =
              operatorWeights.computeIfAbsent(
                  tags,
                  key -> {
                    var reference = new AtomicReference<>(weight);
                    var name = SolverMetric.ALNS_STATISTICS.getMeterId() + ".operator.weight";
                    var existing = Metrics.globalRegistry.find(name).tags(key).gauge();
                    if (existing != null) {
                      Metrics.globalRegistry.remove(existing);
                    }
                    Metrics.gauge(name, key, reference, AtomicReference::get);
                    return reference;
                  });
          value.set(weight);
        });
  }

  public void phaseEnded(AlnsPhaseScope<Solution_> phaseScope) {
    sampleConstraints(phaseScope, true, false);
  }

  /**
   * Refreshes score summaries after migration without attributing work or reward to an operator.
   */
  public void incumbentChanged(AlnsPhaseScope<Solution_> phaseScope, boolean bestImproved) {
    sampleConstraints(phaseScope, true, bestImproved);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void sampleConstraints(
      AlnsPhaseScope<Solution_> phaseScope, boolean sampleStep, boolean sampleBest) {
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
