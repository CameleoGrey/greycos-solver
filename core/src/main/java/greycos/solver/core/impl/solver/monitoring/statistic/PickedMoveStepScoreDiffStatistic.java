package greycos.solver.core.impl.solver.monitoring.statistic;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.alns.AlnsStepScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.definition.ScoreDefinition;
import greycos.solver.core.impl.solver.AbstractSolver;
import greycos.solver.core.impl.solver.monitoring.ScoreLevels;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSupport;
import greycos.solver.core.impl.solver.monitoring.SolverMetricUtil;

import io.micrometer.core.instrument.Tags;

public class PickedMoveStepScoreDiffStatistic<Solution_> implements SolverStatistic<Solution_> {

  private final Map<Solver<Solution_>, PhaseLifecycleListenerAdapter<Solution_>>
      solverToPhaseLifecycleListenerMap = Collections.synchronizedMap(new WeakHashMap<>());

  @Override
  public void unregister(Solver<Solution_> solver) {
    var listener = solverToPhaseLifecycleListenerMap.remove(solver);
    if (listener != null) {
      ((AbstractSolver<Solution_>) solver).removePhaseLifecycleListener(listener);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public void register(Solver<Solution_> solver) {
    var defaultSolver = (AbstractSolver<Solution_>) solver;
    var scoreDirectorFactory = defaultSolver.getScoreDirectorFactory();
    var solutionDescriptor = scoreDirectorFactory.getSolutionDescriptor();
    var listener =
        new PickedMoveStepScoreDiffStatisticListener(solutionDescriptor.getScoreDefinition());
    var guardedListener =
        SolverMetricSupport.guardedPhaseListener(SolverMetricSupport.scope(solver), listener);
    solverToPhaseLifecycleListenerMap.put(solver, guardedListener);
    defaultSolver.addPhaseLifecycleListener(guardedListener);
  }

  private static class PickedMoveStepScoreDiffStatisticListener<
          Solution_, Score_ extends Score<Score_>>
      extends PhaseLifecycleListenerAdapter<Solution_> {

    private Score_ oldStepScore = null; // Guaranteed local search; no need for InnerScore.
    private final ScoreDefinition<Score_> scoreDefinition;
    private final Map<Tags, ScoreLevels> tagsToMoveScoreMap = new ConcurrentHashMap<>();

    public PickedMoveStepScoreDiffStatisticListener(ScoreDefinition<Score_> scoreDefinition) {
      this.scoreDefinition = scoreDefinition;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
      if (phaseScope instanceof LocalSearchPhaseScope) {
        oldStepScore = (Score_) phaseScope.getStartingScore().raw();
      }
    }

    @Override
    public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
      if (phaseScope instanceof LocalSearchPhaseScope) {
        oldStepScore = null;
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void stepEnded(AbstractStepScope<Solution_> stepScope) {
      if (stepScope instanceof LocalSearchStepScope) {
        recordStep(stepScope, ((LocalSearchStepScope<Solution_>) stepScope).getStep().describe());
      } else if (stepScope instanceof AlnsStepScope<Solution_> alnsStepScope) {
        var trial = (AlnsTrialResult<Score_>) alnsStepScope.getTrialResult();
        recordDifference(
            stepScope, alnsStepScope.getOperatorPairId(), trial.afterScore(), trial.beforeScore());
      }
    }

    private void recordStep(AbstractStepScope<Solution_> stepScope, String moveType) {
      var newStepScore = stepScope.<Score_>getScore().raw();
      var previousScore = oldStepScore;
      oldStepScore = newStepScore;
      recordDifference(stepScope, moveType, newStepScore, previousScore);
    }

    private void recordDifference(
        AbstractStepScope<Solution_> stepScope, String moveType, Score_ after, Score_ before) {
      var tags =
          stepScope.getPhaseScope().getSolverScope().getMonitoringTags().and("move.type", moveType);
      SolverMetricUtil.registerScoreDifference(
          SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF,
          tags,
          scoreDefinition,
          tagsToMoveScoreMap,
          after,
          before);
    }
  }
}
