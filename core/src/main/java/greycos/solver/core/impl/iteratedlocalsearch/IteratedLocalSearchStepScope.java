package greycos.solver.core.impl.iteratedlocalsearch;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.preview.api.move.Move;

/** One committed perturbation or improvement move; administrative transitions are not steps. */
public final class IteratedLocalSearchStepScope<Solution_> extends AbstractStepScope<Solution_> {
  public enum Origin {
    PERTURBATION,
    LOCAL_SEARCH
  }

  private final IteratedLocalSearchPhaseScope<Solution_> phaseScope;
  private final Origin origin;
  private final InnerScore<?> startingScore;
  private final InnerScore<?> startingBestScore;
  private Move<Solution_> move;
  private String stepString;
  private long selectedMoveCount;
  private long acceptedMoveCount;

  public IteratedLocalSearchStepScope(
      IteratedLocalSearchPhaseScope<Solution_> phaseScope, Origin origin) {
    this(phaseScope, phaseScope.getNextStepIndex(), origin);
  }

  public IteratedLocalSearchStepScope(
      IteratedLocalSearchPhaseScope<Solution_> phaseScope, int stepIndex, Origin origin) {
    super(stepIndex);
    this.phaseScope = phaseScope;
    this.origin = origin;
    startingScore =
        phaseScope.getLastCompletedStepScope() == null
            ? null
            : phaseScope.getLastCompletedStepScope().getScore();
    startingBestScore = phaseScope.getBestScore();
  }

  @Override
  public IteratedLocalSearchPhaseScope<Solution_> getPhaseScope() {
    return phaseScope;
  }

  public Origin getOrigin() {
    return origin;
  }

  @SuppressWarnings("unchecked")
  public <Score_ extends Score<Score_>> InnerScore<Score_> getStartingScore() {
    return (InnerScore<Score_>) startingScore;
  }

  @SuppressWarnings("unchecked")
  public <Score_ extends Score<Score_>> InnerScore<Score_> getStartingBestScore() {
    return (InnerScore<Score_>) startingBestScore;
  }

  public Move<Solution_> getMove() {
    return move;
  }

  public void setMove(Move<Solution_> move) {
    this.move = move;
  }

  public String getStepString() {
    return stepString;
  }

  public void setStepString(String stepString) {
    this.stepString = stepString;
  }

  public long getSelectedMoveCount() {
    return selectedMoveCount;
  }

  public void setSelectedMoveCount(long selectedMoveCount) {
    this.selectedMoveCount = selectedMoveCount;
  }

  public long getAcceptedMoveCount() {
    return acceptedMoveCount;
  }

  public void setAcceptedMoveCount(long acceptedMoveCount) {
    this.acceptedMoveCount = acceptedMoveCount;
  }
}
