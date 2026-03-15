package ai.greycos.solver.core.impl.localsearch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.config.solver.monitoring.SolverMetric;
import ai.greycos.solver.core.impl.localsearch.decider.LocalSearchDecider;
import ai.greycos.solver.core.impl.localsearch.event.LocalSearchPhaseLifecycleListener;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.phase.AbstractPhase;
import ai.greycos.solver.core.impl.phase.PhaseType;
import ai.greycos.solver.core.impl.score.definition.ScoreDefinition;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.impl.solver.monitoring.ScoreLevels;
import ai.greycos.solver.core.impl.solver.monitoring.SolverMetricUtil;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;

/**
 * Default implementation of {@link LocalSearchPhase}.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class DefaultLocalSearchPhase<Solution_> extends AbstractPhase<Solution_>
    implements LocalSearchPhase<Solution_>, LocalSearchPhaseLifecycleListener<Solution_> {
  protected final LocalSearchDecider<Solution_> decider;
  protected final AtomicLong acceptedMoveCountPerStep = new AtomicLong(0);
  protected final AtomicLong selectedMoveCountPerStep = new AtomicLong(0);
  protected final Map<String, ConstraintMatchMetricHandle> constraintIdToMetricHandleMap =
      new ConcurrentHashMap<>();
  protected final Map<Tags, ScoreLevels> constraintMatchTotalStepScoreMap =
      new ConcurrentHashMap<>();
  protected final Map<Tags, ScoreLevels> constraintMatchTotalBestScoreMap =
      new ConcurrentHashMap<>();

  private DefaultLocalSearchPhase(Builder<Solution_> builder) {
    super(builder);
    decider = builder.decider;
  }

  @Override
  public PhaseType getPhaseType() {
    return PhaseType.LOCAL_SEARCH;
  }

  @Override
  public IntFunction<EventProducerId> getEventProducerIdSupplier() {
    return EventProducerId::localSearch;
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  public void solve(SolverScope<Solution_> solverScope) {
    var hasAnythingToImprove =
        solverScope.getProblemSizeStatistics().approximateProblemSizeLog() != 0.0;
    if (!hasAnythingToImprove) {
      // Reaching local search means that the solution is already fully initialized.
      // Yet the problem size indicates there is only 1 possible solution.
      // Therefore, this solution must be it and there is nothing to improve.
      logger.info(
          "{}Local Search phase ({}) has no entities or values to move.",
          logIndentation,
          phaseIndex);
      return;
    }

    var phaseScope = new LocalSearchPhaseScope<>(solverScope, phaseIndex);
    phaseStarted(phaseScope);

    if (solverScope.isMetricEnabled(SolverMetric.MOVE_COUNT_PER_STEP)) {
      Metrics.gauge(
          SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".accepted",
          solverScope.getMonitoringTags(),
          acceptedMoveCountPerStep);
      Metrics.gauge(
          SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".selected",
          solverScope.getMonitoringTags(),
          selectedMoveCountPerStep);
    }

    while (!phaseTermination.isPhaseTerminated(phaseScope)) {
      var stepScope = new LocalSearchStepScope<>(phaseScope);
      stepScope.setTimeGradient(phaseTermination.calculatePhaseTimeGradient(phaseScope));
      stepStarted(stepScope);
      decider.decideNextStep(stepScope);
      if (stepScope.getStep() == null) {
        if (phaseTermination.isPhaseTerminated(phaseScope)) {
          logger.trace(
              "{}    Step index ({}), time spent ({}) terminated without picking a nextStep.",
              logIndentation,
              stepScope.getStepIndex(),
              stepScope.getPhaseScope().calculateSolverTimeMillisSpentUpToNow());
        } else if (stepScope.getSelectedMoveCount() == 0L) {
          logger.warn(
              "{}    No doable selected move at step index ({}), time spent ({})."
                  + " Terminating phase early.",
              logIndentation,
              stepScope.getStepIndex(),
              stepScope.getPhaseScope().calculateSolverTimeMillisSpentUpToNow());
        } else {
          throw new IllegalStateException(
              "The step index ("
                  + stepScope.getStepIndex()
                  + ") has accepted/selected move count ("
                  + stepScope.getAcceptedMoveCount()
                  + "/"
                  + stepScope.getSelectedMoveCount()
                  + ") but failed to pick a nextStep ("
                  + stepScope.getStep()
                  + ").");
        }
        // Although stepStarted has been called, stepEnded is not called for this step
        break;
      }
      doStep(stepScope);
      stepEnded(stepScope);
      phaseScope.setLastCompletedStepScope(stepScope);
    }
    phaseEnded(phaseScope);
  }

  protected void doStep(LocalSearchStepScope<Solution_> stepScope) {
    var step = stepScope.getStep();
    stepScope.getScoreDirector().executeMove(step);
    predictWorkingStepScore(stepScope, step);
    var solver = stepScope.getPhaseScope().getSolverScope().getSolver();
    solver.getBestSolutionRecaller().processWorkingSolutionDuringStep(stepScope);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    super.solvingStarted(solverScope);
    decider.solvingStarted(solverScope);
  }

  @Override
  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    decider.phaseStarted(phaseScope);
    assertWorkingSolutionInitialized(phaseScope);
  }

  @Override
  public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
    super.stepStarted(stepScope);
    decider.stepStarted(stepScope);
  }

  @Override
  public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
    super.stepEnded(stepScope);
    decider.stepEnded(stepScope);
    collectMetrics(stepScope);
    var phaseScope = stepScope.getPhaseScope();
    if (logger.isDebugEnabled()) {
      if (stepScope.getAcceptedMoveCount() == 0 && phaseTermination.isPhaseTerminated(phaseScope)) {
        // Terminated early
        logger.debug(
            "{}    LS step ({}), time spent ({}), score ({}), {} best score ({}),"
                + " terminated prematurely after selecting {} moves.",
            logIndentation,
            stepScope.getStepIndex(),
            phaseScope.calculateSolverTimeMillisSpentUpToNow(),
            stepScope.getScore().raw(),
            (stepScope.getBestScoreImproved() ? "new" : "   "),
            phaseScope.getBestScore().raw(),
            stepScope.getSelectedMoveCount());
      } else {
        logger.debug(
            "{}    LS step ({}), time spent ({}), score ({}), {} best score ({}),"
                + " accepted/selected move count ({}/{}), picked move ({}).",
            logIndentation,
            stepScope.getStepIndex(),
            phaseScope.calculateSolverTimeMillisSpentUpToNow(),
            stepScope.getScore().raw(),
            (stepScope.getBestScoreImproved() ? "new" : "   "),
            phaseScope.getBestScore().raw(),
            stepScope.getAcceptedMoveCount(),
            stepScope.getSelectedMoveCount(),
            stepScope.getStepString());
      }
    }
  }

  private void collectMetrics(LocalSearchStepScope<Solution_> stepScope) {
    var solverScope = stepScope.getPhaseScope().getSolverScope();
    if (solverScope.isMetricEnabled(SolverMetric.MOVE_COUNT_PER_STEP)) {
      acceptedMoveCountPerStep.set(stepScope.getAcceptedMoveCount());
      selectedMoveCountPerStep.set(stepScope.getSelectedMoveCount());
    }
    collectConstraintMatchTotalMetrics(solverScope, stepScope.getStepIndex(), false);
  }

  private void collectConstraintMatchTotalMetrics(
      SolverScope<Solution_> solverScope, long stepIndex, boolean force) {
    if (!isConstraintMatchMetricEnabled(solverScope)) {
      return;
    }
    if (!force
        && !isConstraintMatchMetricSamplingStep(
            stepIndex, solverScope.getConstraintMatchMetricSampleInterval())) {
      return;
    }
    var scoreDirector = solverScope.getScoreDirector();
    if (scoreDirector.getConstraintMatchPolicy().isEnabled()) {
      var scoreDefinition = solverScope.getScoreDefinition();
      var bestMetricEnabled =
          solverScope.isMetricEnabled(SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE);
      var stepMetricEnabled =
          solverScope.isMetricEnabled(SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE);
      for (ConstraintMatchTotal<?> constraintMatchTotal :
          scoreDirector.getConstraintMatchTotalMap().values()) {
        var handle =
            getOrCreateConstraintMatchMetricHandle(
                solverScope, constraintMatchTotal, bestMetricEnabled, stepMetricEnabled);
        if (bestMetricEnabled) {
          collectConstraintMatchTotalMetrics(
              SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE,
              handle.tags(),
              handle.bestCount(),
              constraintMatchTotalBestScoreMap,
              constraintMatchTotal,
              scoreDefinition);
        }
        if (stepMetricEnabled) {
          collectConstraintMatchTotalMetrics(
              SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE,
              handle.tags(),
              handle.stepCount(),
              constraintMatchTotalStepScoreMap,
              constraintMatchTotal,
              scoreDefinition);
        }
      }
    }
  }

  private ConstraintMatchMetricHandle getOrCreateConstraintMatchMetricHandle(
      SolverScope<Solution_> solverScope,
      ConstraintMatchTotal<?> constraintMatchTotal,
      boolean bestMetricEnabled,
      boolean stepMetricEnabled) {
    var constraintRef = constraintMatchTotal.getConstraintRef();
    return constraintIdToMetricHandleMap.computeIfAbsent(
        constraintRef.constraintId(),
        ignored ->
            new ConstraintMatchMetricHandle(
                solverScope
                    .getMonitoringTags()
                    .and(
                        "constraint.package",
                        constraintRef.packageName(),
                        "constraint.name",
                        constraintRef.constraintName()),
                bestMetricEnabled,
                stepMetricEnabled));
  }

  private <Score_ extends Score<Score_>> void collectConstraintMatchTotalMetrics(
      SolverMetric metric,
      Tags tags,
      AtomicLong count,
      Map<Tags, ScoreLevels> scoreMap,
      ConstraintMatchTotal<Score_> constraintMatchTotal,
      ScoreDefinition<Score_> scoreDefinition) {
    count.set(constraintMatchTotal.getConstraintMatchCount());
    SolverMetricUtil.registerScore(
        metric,
        tags,
        scoreDefinition,
        scoreMap,
        InnerScore.fullyAssigned(constraintMatchTotal.getScore()));
  }

  private static boolean isConstraintMatchMetricEnabled(SolverScope<?> solverScope) {
    return solverScope.isMetricEnabled(SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE)
        || solverScope.isMetricEnabled(SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE);
  }

  private static boolean isConstraintMatchMetricSamplingStep(long stepIndex, int sampleInterval) {
    return sampleInterval <= 1 || (stepIndex + 1) % sampleInterval == 0;
  }

  @Override
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    var solverScope = phaseScope.getSolverScope();
    if (solverScope.getConstraintMatchMetricSampleInterval() > 1) {
      collectConstraintMatchTotalMetrics(solverScope, phaseScope.getNextStepIndex(), true);
    }
    super.phaseEnded(phaseScope);
    decider.phaseEnded(phaseScope);
    phaseScope.endingNow();
    logger.info(
        "{}Local Search phase ({}) ended: time spent ({}), best score ({}),"
            + " move evaluation speed ({}/sec), step total ({}).",
        logIndentation,
        phaseIndex,
        phaseScope.calculateSolverTimeMillisSpentUpToNow(),
        phaseScope.getBestScore().raw(),
        phaseScope.getPhaseMoveEvaluationSpeed(),
        phaseScope.getNextStepIndex());
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    super.solvingEnded(solverScope);
    decider.solvingEnded(solverScope);
  }

  @Override
  public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
    super.solvingError(solverScope, exception);
    decider.solvingError(solverScope, exception);
  }

  private static final class ConstraintMatchMetricHandle {
    private final Tags tags;
    private final AtomicLong bestCount = new AtomicLong();
    private final AtomicLong stepCount = new AtomicLong();

    private ConstraintMatchMetricHandle(
        Tags tags, boolean bestMetricEnabled, boolean stepMetricEnabled) {
      this.tags = tags;
      if (bestMetricEnabled) {
        Metrics.gauge(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE.getMeterId() + ".count",
            tags,
            bestCount);
      }
      if (stepMetricEnabled) {
        Metrics.gauge(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE.getMeterId() + ".count",
            tags,
            stepCount);
      }
    }

    private Tags tags() {
      return tags;
    }

    private AtomicLong bestCount() {
      return bestCount;
    }

    private AtomicLong stepCount() {
      return stepCount;
    }
  }

  public static class Builder<Solution_> extends AbstractPhaseBuilder<Solution_> {

    private final LocalSearchDecider<Solution_> decider;

    public Builder(
        int phaseIndex,
        String logIndentation,
        PhaseTermination<Solution_> phaseTermination,
        LocalSearchDecider<Solution_> decider) {
      super(phaseIndex, logIndentation, phaseTermination);
      this.decider = decider;
    }

    @Override
    public Builder<Solution_> enableAssertions(EnvironmentMode environmentMode) {
      super.enableAssertions(environmentMode);
      return this;
    }

    @Override
    public DefaultLocalSearchPhase<Solution_> build() {
      return new DefaultLocalSearchPhase<>(this);
    }
  }
}
