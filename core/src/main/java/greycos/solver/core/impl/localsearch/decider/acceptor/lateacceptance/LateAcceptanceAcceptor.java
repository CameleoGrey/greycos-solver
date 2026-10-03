package greycos.solver.core.impl.localsearch.decider.acceptor.lateacceptance;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.localsearch.decider.acceptor.AbstractAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.AcceptorMigrationState;
import greycos.solver.core.impl.localsearch.decider.acceptor.LateAcceptanceHistory;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.director.InnerScore;

public class LateAcceptanceAcceptor<Solution_> extends AbstractAcceptor<Solution_> {

  protected int lateAcceptanceSize = -1;
  protected boolean hillClimbingEnabled = true;

  private LateAcceptanceScoreBuffer scoreBuffer;
  private LevelScoreState<Solution_> bestScoreState;

  public void setLateAcceptanceSize(int lateAcceptanceSize) {
    this.lateAcceptanceSize = lateAcceptanceSize;
  }

  public void setHillClimbingEnabled(boolean hillClimbingEnabled) {
    this.hillClimbingEnabled = hillClimbingEnabled;
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    validate();
    var initialScore = phaseScope.getBestScore();
    scoreBuffer = new LateAcceptanceScoreBuffer(lateAcceptanceSize, initialScore);
    var scoreDefinition = phaseScope.getSolverScope().getScoreDefinition();
    bestScoreState =
        scoreDefinition.getLevelsSize() > 1
            ? new DefaultLevelScoreState<>(initialScore, scoreDefinition)
            : new NoOpLevelScoreState<>();
  }

  private void validate() {
    if (lateAcceptanceSize <= 0) {
      throw new IllegalArgumentException(
          "The lateAcceptanceSize (%d) cannot be negative or zero.".formatted(lateAcceptanceSize));
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public boolean isStructurallyValidSolutionAccepted(LocalSearchMoveScope<Solution_> moveScope) {
    var moveScore = (InnerScore) moveScope.getScore();
    var lateScore = scoreBuffer.getCurrent();
    if (moveScore.compareTo(lateScore) >= 0) {
      return true;
    }
    if (hillClimbingEnabled) {
      var lastStepScore =
          moveScope.getStepScope().getPhaseScope().getLastCompletedStepScope().getScore();
      return moveScore.compareTo(lastStepScore) >= 0;
    }
    return false;
  }

  @Override
  public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
    super.stepStarted(stepScope);
    bestScoreState.update(stepScope);
  }

  @Override
  public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
    super.stepEnded(stepScope);
    scoreBuffer.update(stepScope.getScore());
    if (bestScoreState.isNonDominatedLevelChanged(stepScope)) {
      scoreBuffer.tryReset(stepScope.getPhaseScope().getBestScore());
    }
  }

  @Override
  public void migrationStepEnded(LocalSearchStepScope<Solution_> stepScope) {
    // A migrant contributes one score, including when it improves a hard or medium level.
    scoreBuffer.update(stepScope.getScore());
    bestScoreState.update(stepScope);
  }

  @Override
  public void migrationStepEnded(
      LocalSearchStepScope<Solution_> stepScope, AcceptorMigrationState state) {
    var phaseScope = stepScope.getPhaseScope();
    var definition = phaseScope.getSolverScope().getScoreDefinition();
    if (state instanceof LateAcceptanceHistory history && history.isCompatible(definition)) {
      scoreBuffer = new LateAcceptanceScoreBuffer(history);
      bestScoreState =
          definition.getLevelsSize() > 1
              ? new DefaultLevelScoreState<>(phaseScope.getBestScore(), definition)
              : new NoOpLevelScoreState<>();
      bestScoreState.update(stepScope);
    } else {
      migrationStepEnded(stepScope);
    }
  }

  @Override
  public AcceptorMigrationState snapshotMigrationState(
      LocalSearchPhaseScope<Solution_> phaseScope) {
    return scoreBuffer.snapshot(phaseScope.getSolverScope().getScoreDefinition());
  }

  @Override
  public void resetAfterMigration(LocalSearchPhaseScope<Solution_> phaseScope) {
    // Keep the island's score history and its current position in the circular buffer.
  }

  @Override
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    scoreBuffer = null;
    bestScoreState = null;
  }

  protected <Score_ extends Score<Score_>> InnerScore<Score_> getScore(int i) {
    return scoreBuffer.get(i);
  }
}
