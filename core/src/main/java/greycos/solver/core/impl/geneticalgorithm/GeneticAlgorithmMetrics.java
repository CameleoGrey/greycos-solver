package greycos.solver.core.impl.geneticalgorithm;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.monitoring.ScoreLevels;
import greycos.solver.core.impl.solver.monitoring.SolverMetricUtil;

import io.micrometer.core.instrument.Tags;

/** Standard step and constraint metrics for one invocation of the genetic algorithm. */
public final class GeneticAlgorithmMetrics<Solution_> {
  private final AtomicLong selectedCount = new AtomicLong();
  private final AtomicLong acceptedCount = new AtomicLong();
  private final Map<Tags, ScoreLevels> stepConstraintScores = new HashMap<>();
  private final Map<Tags, ScoreLevels> bestConstraintScores = new HashMap<>();
  private final Map<String, AtomicLong> constraintCounts = new HashMap<>();

  public void phaseStarted(GeneticAlgorithmPhaseScope<Solution_> phaseScope) {
    var solverScope = phaseScope.getSolverScope();
    solverScope
        .getMetricRun()
        .publish(
            () -> {
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
                  phaseScope
                      .getLastCompletedStepScope()
                      .getScore()
                      .equals(phaseScope.getBestScore()));
            });
  }

  /**
   * Records the scored workspace and population admission before completion listeners observe it.
   */
  public void record(GeneticAlgorithmStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var solverScope = phaseScope.getSolverScope();
    solverScope
        .getMetricRun()
        .publish(
            () -> {
              selectedCount.set(1L);
              acceptedCount.set(stepScope.isAdmitted() ? 1L : 0L);
              var interval = solverScope.getConstraintMatchMetricSampleInterval();
              sampleConstraints(
                  phaseScope,
                  interval <= 1 || (stepScope.getStepIndex() + 1) % interval == 0,
                  stepScope.getBestScoreImproved());
            });
  }

  /**
   * Removes tentative move-count publication if a completion listener fails. Constraint summaries
   * still describe the retained, scored workspace and any best solution already published.
   */
  public void restoreStepCounts(GeneticAlgorithmStepScope<Solution_> previousCompletedStep) {
    previousCompletedStep
        .getPhaseScope()
        .getSolverScope()
        .getMetricRun()
        .publish(
            () -> {
              selectedCount.set(previousCompletedStep.getStepIndex() < 0 ? 0L : 1L);
              acceptedCount.set(
                  previousCompletedStep.getStepIndex() >= 0 && previousCompletedStep.isAdmitted()
                      ? 1L
                      : 0L);
            });
  }

  public void phaseEnded(GeneticAlgorithmPhaseScope<Solution_> phaseScope) {
    phaseScope
        .getSolverScope()
        .getMetricRun()
        .publish(() -> sampleConstraints(phaseScope, true, false));
  }

  /** Sample while the published best is materialized, before a temporary probe is undone. */
  public void recordBest(GeneticAlgorithmPhaseScope<Solution_> phaseScope) {
    phaseScope
        .getSolverScope()
        .getMetricRun()
        .publish(() -> sampleConstraints(phaseScope, false, true));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void sampleConstraints(
      GeneticAlgorithmPhaseScope<Solution_> phaseScope, boolean sampleStep, boolean sampleBest) {
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
        registerConstraintCount(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE, tags, total.getConstraintMatchCount());
        SolverMetricUtil.registerScore(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE,
            tags,
            scoreDefinition,
            stepConstraintScores,
            InnerScore.fullyAssigned((Score) total.getScore()));
      }
      if (bestEnabled) {
        registerConstraintCount(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE, tags, total.getConstraintMatchCount());
        SolverMetricUtil.registerScore(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE,
            tags,
            scoreDefinition,
            bestConstraintScores,
            InnerScore.fullyAssigned((Score) total.getScore()));
      }
    }
  }

  private void registerConstraintCount(SolverMetric metric, Tags tags, long count) {
    var name = metric.getMeterId() + ".count";
    var value =
        constraintCounts.computeIfAbsent(
            name + tags,
            ignored -> {
              var counter = new AtomicLong();
              SolverMetricUtil.rebindGauge(name, tags, counter);
              return counter;
            });
    value.set(count);
  }
}
