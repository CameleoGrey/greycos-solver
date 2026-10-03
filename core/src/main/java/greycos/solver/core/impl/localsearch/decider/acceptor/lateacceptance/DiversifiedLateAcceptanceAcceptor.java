package greycos.solver.core.impl.localsearch.decider.acceptor.lateacceptance;

import java.util.Arrays;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.localsearch.decider.acceptor.AbstractAcceptor;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.director.InnerScore;

/**
 * Keeps history for final rejected proposals and executed transitions. Accepted proposals remain
 * provisional until the forager chooses a step, so their scores must not enter history during
 * acceptance checks. With one accepted move per step, this preserves the paper's iteration order.
 */
public class DiversifiedLateAcceptanceAcceptor<Solution_> extends AbstractAcceptor<Solution_> {

  // The worst score in the late elements list
  protected InnerScore<?> lateWorseScore;
  // Number of occurrences of lateWorse in the late elements
  protected int lateWorseOccurrences = -1;

  protected int lateAcceptanceSize = -1;

  protected InnerScore<?>[] previousScores;
  protected int lateScoreIndex = -1;

  public void setLateAcceptanceSize(int lateAcceptanceSize) {
    this.lateAcceptanceSize = lateAcceptanceSize;
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    validate();
    previousScores = new InnerScore[lateAcceptanceSize];
    var initialScore = phaseScope.getBestScore();
    Arrays.fill(previousScores, initialScore);
    lateScoreIndex = 0;
    lateWorseOccurrences = lateAcceptanceSize;
    lateWorseScore = initialScore;
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
    // The acceptance and replacement strategies are based on the work:
    // Diversified Late Acceptance Search by M. Namazi, C. Sanderson, M. A. H. Newton, M. M. A.
    // Polash, and A. Sattar
    var moveScore = moveScope.getScore();
    var current =
        (InnerScore)
            moveScope.getStepScope().getPhaseScope().getLastCompletedStepScope().getScore();
    return moveScore.compareTo(current) == 0 || moveScore.compareTo(getLateWorseScore()) > 0;
  }

  @Override
  public void moveEvaluated(LocalSearchMoveScope<Solution_> moveScope) {
    if (!moveScope.getScore().isStructurallyFlawed() && !moveScope.getAccepted()) {
      var incumbent =
          moveScope.getStepScope().getPhaseScope().getLastCompletedStepScope().getScore();
      recordIteration(incumbent, incumbent);
    }
  }

  @Override
  public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
    super.stepEnded(stepScope);
    var previous = stepScope.getPhaseScope().getLastCompletedStepScope().getScore();
    // This also records a rejected fallback or an injected move that was actually executed.
    recordIteration(stepScope.getScore(), previous);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void recordIteration(InnerScore<?> currentScore, InnerScore<?> previous) {
    var current = (InnerScore) currentScore;
    var lateScore = getPreviousScore(lateScoreIndex);
    // Improves the diversification to allow the next iterations to find a better solution
    var currentScoreCmp = current.compareTo(lateScore);
    var currentScoreWorse = currentScoreCmp < 0;
    // Improves the intensification but avoids replacing values when the search falls into a plateau
    // or local minima
    var currentScoreBetter = currentScoreCmp > 0 && current.compareTo(previous) > 0;
    if (currentScoreWorse || currentScoreBetter) {
      updateLateScore(current);
    }
    lateScoreIndex = (lateScoreIndex + 1) % lateAcceptanceSize;
  }

  private <Score_ extends Score<Score_>> void updateLateScore(InnerScore<Score_> newScore) {
    var castLateWorse = this.<Score_>getLateWorseScore();
    var newScoreWorseCmp = newScore.compareTo(castLateWorse);
    var lateScore = this.<Score_>getPreviousScore(lateScoreIndex);
    var lateScoreEqual = lateScore.compareTo(castLateWorse) == 0;
    if (newScoreWorseCmp < 0) {
      castLateWorse = newScore;
      this.lateWorseOccurrences = 1;
    } else if (lateScoreEqual && newScoreWorseCmp != 0) {
      this.lateWorseOccurrences--;
    } else if (!lateScoreEqual && newScoreWorseCmp == 0) {
      this.lateWorseOccurrences++;
    }
    previousScores[lateScoreIndex] = newScore;
    // Recompute the new lateWorse and the number of occurrences
    if (lateWorseOccurrences == 0) {
      castLateWorse = getPreviousScore(0);
      lateWorseOccurrences = 1;
      for (var i = 1; i < lateAcceptanceSize; i++) {
        var previousScore = this.<Score_>getPreviousScore(i);
        var scoreCmp = previousScore.compareTo(castLateWorse);
        if (scoreCmp < 0) {
          castLateWorse = previousScore;
          lateWorseOccurrences = 1;
        } else if (scoreCmp == 0) {
          lateWorseOccurrences++;
        }
      }
    }
    lateWorseScore = castLateWorse;
  }

  @SuppressWarnings("unchecked")
  private <Score_ extends Score<Score_>> InnerScore<Score_> getLateWorseScore() {
    return (InnerScore<Score_>) lateWorseScore;
  }

  @SuppressWarnings("unchecked")
  private <Score_ extends Score<Score_>> InnerScore<Score_> getPreviousScore(int lateScoreIndex) {
    return (InnerScore<Score_>) previousScores[lateScoreIndex];
  }

  @Override
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    previousScores = null;
    lateScoreIndex = -1;
    lateWorseScore = null;
    lateWorseOccurrences = -1;
  }
}
