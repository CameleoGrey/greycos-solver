package greycos.solver.core.impl.localsearch.scope;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptLedger;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.random.RandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public final class LocalSearchPhaseScope<Solution_> extends AbstractPhaseScope<Solution_> {

  private LocalSearchStepScope<Solution_> lastCompletedStepScope;
  private final boolean episode;
  private InnerScore<?> episodeBestScore;
  private long episodeBestTimeMillis;
  private RandomSource episodeRandom;
  private SelectionAttemptLedger selectionAttemptLedger;
  private AbstractPhaseScope<Solution_> childThreadAccountingScope;

  public LocalSearchPhaseScope(SolverScope<Solution_> solverScope, int phaseIndex) {
    super(solverScope, phaseIndex);
    episode = false;
    lastCompletedStepScope = new LocalSearchStepScope<>(this, -1);
    lastCompletedStepScope.setTimeGradient(0.0);
  }

  /** Creates an internal episode view without changing the enclosing solver's best or counters. */
  public LocalSearchPhaseScope(
      SolverScope<Solution_> solverScope,
      int phaseIndex,
      InnerScore<?> workingScore,
      RandomSource random,
      SelectionAttemptLedger attempts) {
    super(solverScope, phaseIndex);
    episode = true;
    startingScore = workingScore;
    episodeBestScore = workingScore;
    episodeBestTimeMillis = solverScope.getClock().millis();
    episodeRandom = random;
    selectionAttemptLedger = attempts;
    lastCompletedStepScope = new LocalSearchStepScope<>(this, -1);
    lastCompletedStepScope.setScore(workingScore);
    lastCompletedStepScope.setTimeGradient(0.0);
  }

  public boolean isEpisode() {
    return episode;
  }

  public SelectionAttemptLedger getSelectionAttemptLedger() {
    return selectionAttemptLedger;
  }

  public void setChildThreadAccountingScope(AbstractPhaseScope<Solution_> accountingScope) {
    childThreadAccountingScope = accountingScope;
  }

  @Override
  public void addChildThreadsScoreCalculationCount(long addition) {
    if (childThreadAccountingScope == null) super.addChildThreadsScoreCalculationCount(addition);
    else childThreadAccountingScope.addChildThreadsScoreCalculationCount(addition);
  }

  @Override
  public void reset() {
    if (!episode) {
      super.reset();
      return;
    }
    bestSolutionStepIndex = -1;
    lastCompletedStepScope.setScore(startingScore);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Score_ extends Score<Score_>> InnerScore<Score_> getBestScore() {
    return episode ? (InnerScore<Score_>) episodeBestScore : super.getBestScore();
  }

  @Override
  public long getPhaseBestSolutionTimeMillis() {
    return episode ? episodeBestTimeMillis : super.getPhaseBestSolutionTimeMillis();
  }

  @Override
  public RandomSource getWorkingRandom() {
    return episode ? episodeRandom : super.getWorkingRandom();
  }

  public <Score_ extends Score<Score_>> boolean recordEpisodeBest(
      LocalSearchStepScope<Solution_> stepScope) {
    if (!episode) throw new IllegalStateException("Episode best requires an episode scope.");
    InnerScore<Score_> score = stepScope.getScore();
    boolean improved = score.compareTo(this.<Score_>getBestScore()) > 0;
    stepScope.setBestScoreImproved(improved);
    if (improved) {
      episodeBestScore = stepScope.getScore();
      episodeBestTimeMillis = solverScope.getClock().millis();
      bestSolutionStepIndex = stepScope.getStepIndex();
    }
    return improved;
  }

  @Override
  public LocalSearchStepScope<Solution_> getLastCompletedStepScope() {
    return lastCompletedStepScope;
  }

  public void setLastCompletedStepScope(LocalSearchStepScope<Solution_> lastCompletedStepScope) {
    this.lastCompletedStepScope = lastCompletedStepScope;
  }

  // ************************************************************************
  // Calculated methods
  // ************************************************************************

}
