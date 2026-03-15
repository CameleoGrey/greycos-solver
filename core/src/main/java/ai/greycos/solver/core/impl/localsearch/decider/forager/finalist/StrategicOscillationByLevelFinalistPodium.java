package ai.greycos.solver.core.impl.localsearch.decider.forager.finalist;

import ai.greycos.solver.core.api.score.BendableBigDecimalScore;
import ai.greycos.solver.core.api.score.BendableScore;
import ai.greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
import ai.greycos.solver.core.api.score.HardMediumSoftScore;
import ai.greycos.solver.core.api.score.HardSoftBigDecimalScore;
import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.SimpleBigDecimalScore;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;

/**
 * Strategic oscillation, works well with Tabu search.
 *
 * @see FinalistPodium
 */
public final class StrategicOscillationByLevelFinalistPodium<Solution_>
    extends AbstractFinalistPodium<Solution_> {

  private final boolean referenceBestScoreInsteadOfLastStepScore;

  // Guaranteed inside local search, therefore no need for InnerScore.
  private Score<?> referenceScore;
  private Score<?> finalistScore;
  private boolean finalistImprovesUponReference;

  public StrategicOscillationByLevelFinalistPodium(
      boolean referenceBestScoreInsteadOfLastStepScore) {
    this.referenceBestScoreInsteadOfLastStepScore = referenceBestScoreInsteadOfLastStepScore;
  }

  @Override
  public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
    super.stepStarted(stepScope);
    referenceScore =
        referenceBestScoreInsteadOfLastStepScore
            ? stepScope.getPhaseScope().getBestScore().raw()
            : stepScope.getPhaseScope().getLastCompletedStepScope().getScore().raw();
    finalistScore = null;
    finalistImprovesUponReference = false;
  }

  @Override
  public void addMove(LocalSearchMoveScope<Solution_> moveScope) {
    boolean accepted = moveScope.getAccepted();
    if (finalistIsAccepted && !accepted) {
      return;
    }
    if (accepted && !finalistIsAccepted) {
      finalistIsAccepted = true;
      finalistScore = null;
    }
    Score<?> moveScore = moveScope.getScore().raw();
    var comparison = doComparison(moveScore);
    if (comparison > 0) {
      finalistScore = moveScore;
      finalistImprovesUponReference = compareScores(moveScore, referenceScore) > 0;
      clearAndAddFinalist(moveScope);
    } else if (comparison == 0) {
      addFinalist(moveScope);
    }
  }

  private int doComparison(Score<?> moveScore) {
    if (finalistScore == null) {
      return 1;
    }
    // If there is an improving move, do not oscillate
    if (!finalistImprovesUponReference && compareScores(moveScore, referenceScore) < 0) {
      var byLevelComparison =
          compareLevelsAgainstReference(moveScore, finalistScore, referenceScore);
      if (byLevelComparison != 0) {
        return byLevelComparison;
      }
    }
    return compareScores(moveScore, finalistScore);
  }

  private static int compareLevelsAgainstReference(
      Score<?> moveScore, Score<?> finalistScore, Score<?> referenceScore) {
    if (referenceScore instanceof HardSoftScore reference
        && moveScore instanceof HardSoftScore move
        && finalistScore instanceof HardSoftScore finalist) {
      return compareTwoLevels(
          move.hardScore() > reference.hardScore(),
          finalist.hardScore() > reference.hardScore(),
          move.softScore() > reference.softScore(),
          finalist.softScore() > reference.softScore());
    } else if (referenceScore instanceof HardMediumSoftScore reference
        && moveScore instanceof HardMediumSoftScore move
        && finalistScore instanceof HardMediumSoftScore finalist) {
      return compareThreeLevels(
          move.hardScore() > reference.hardScore(),
          finalist.hardScore() > reference.hardScore(),
          move.mediumScore() > reference.mediumScore(),
          finalist.mediumScore() > reference.mediumScore(),
          move.softScore() > reference.softScore(),
          finalist.softScore() > reference.softScore());
    } else if (referenceScore instanceof HardSoftBigDecimalScore reference
        && moveScore instanceof HardSoftBigDecimalScore move
        && finalistScore instanceof HardSoftBigDecimalScore finalist) {
      return compareTwoLevels(
          move.hardScore().compareTo(reference.hardScore()) > 0,
          finalist.hardScore().compareTo(reference.hardScore()) > 0,
          move.softScore().compareTo(reference.softScore()) > 0,
          finalist.softScore().compareTo(reference.softScore()) > 0);
    } else if (referenceScore instanceof HardMediumSoftBigDecimalScore reference
        && moveScore instanceof HardMediumSoftBigDecimalScore move
        && finalistScore instanceof HardMediumSoftBigDecimalScore finalist) {
      return compareThreeLevels(
          move.hardScore().compareTo(reference.hardScore()) > 0,
          finalist.hardScore().compareTo(reference.hardScore()) > 0,
          move.mediumScore().compareTo(reference.mediumScore()) > 0,
          finalist.mediumScore().compareTo(reference.mediumScore()) > 0,
          move.softScore().compareTo(reference.softScore()) > 0,
          finalist.softScore().compareTo(reference.softScore()) > 0);
    } else if (referenceScore instanceof SimpleScore reference
        && moveScore instanceof SimpleScore move
        && finalistScore instanceof SimpleScore finalist) {
      return compareSingleLevel(
          move.score() > reference.score(), finalist.score() > reference.score());
    } else if (referenceScore instanceof SimpleBigDecimalScore reference
        && moveScore instanceof SimpleBigDecimalScore move
        && finalistScore instanceof SimpleBigDecimalScore finalist) {
      return compareSingleLevel(
          move.score().compareTo(reference.score()) > 0,
          finalist.score().compareTo(reference.score()) > 0);
    } else if (referenceScore instanceof BendableScore reference
        && moveScore instanceof BendableScore move
        && finalistScore instanceof BendableScore finalist) {
      var levels = reference.levelsSize();
      for (var i = 0; i < levels; i++) {
        var moveIsHigher = move.hardOrSoftScore(i) > reference.hardOrSoftScore(i);
        var finalistIsHigher = finalist.hardOrSoftScore(i) > reference.hardOrSoftScore(i);
        if (moveIsHigher != finalistIsHigher) {
          return moveIsHigher ? 1 : -1;
        }
        if (moveIsHigher) {
          return 0;
        }
      }
      return 0;
    } else if (referenceScore instanceof BendableBigDecimalScore reference
        && moveScore instanceof BendableBigDecimalScore move
        && finalistScore instanceof BendableBigDecimalScore finalist) {
      var levels = reference.levelsSize();
      for (var i = 0; i < levels; i++) {
        var moveIsHigher = move.hardOrSoftScore(i).compareTo(reference.hardOrSoftScore(i)) > 0;
        var finalistIsHigher =
            finalist.hardOrSoftScore(i).compareTo(reference.hardOrSoftScore(i)) > 0;
        if (moveIsHigher != finalistIsHigher) {
          return moveIsHigher ? 1 : -1;
        }
        if (moveIsHigher) {
          return 0;
        }
      }
      return 0;
    }
    return compareFallbackLevelsAgainstReference(moveScore, finalistScore, referenceScore);
  }

  private static int compareSingleLevel(boolean moveIsHigher, boolean finalistIsHigher) {
    if (moveIsHigher == finalistIsHigher) {
      return 0;
    }
    return moveIsHigher ? 1 : -1;
  }

  private static int compareTwoLevels(
      boolean moveFirstLevelIsHigher,
      boolean finalistFirstLevelIsHigher,
      boolean moveSecondLevelIsHigher,
      boolean finalistSecondLevelIsHigher) {
    if (moveFirstLevelIsHigher != finalistFirstLevelIsHigher) {
      return moveFirstLevelIsHigher ? 1 : -1;
    }
    if (moveFirstLevelIsHigher) {
      return 0;
    }
    return compareSingleLevel(moveSecondLevelIsHigher, finalistSecondLevelIsHigher);
  }

  private static int compareThreeLevels(
      boolean moveFirstLevelIsHigher,
      boolean finalistFirstLevelIsHigher,
      boolean moveSecondLevelIsHigher,
      boolean finalistSecondLevelIsHigher,
      boolean moveThirdLevelIsHigher,
      boolean finalistThirdLevelIsHigher) {
    if (moveFirstLevelIsHigher != finalistFirstLevelIsHigher) {
      return moveFirstLevelIsHigher ? 1 : -1;
    }
    if (moveFirstLevelIsHigher) {
      return 0;
    }
    if (moveSecondLevelIsHigher != finalistSecondLevelIsHigher) {
      return moveSecondLevelIsHigher ? 1 : -1;
    }
    if (moveSecondLevelIsHigher) {
      return 0;
    }
    return compareSingleLevel(moveThirdLevelIsHigher, finalistThirdLevelIsHigher);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareFallbackLevelsAgainstReference(
      Score<?> moveScore, Score<?> finalistScore, Score<?> referenceScore) {
    var moveLevelNumbers = moveScore.toLevelNumbers();
    var finalistLevelNumbers = finalistScore.toLevelNumbers();
    var referenceLevelNumbers = referenceScore.toLevelNumbers();
    for (var i = 0; i < referenceLevelNumbers.length; i++) {
      var moveIsHigher = ((Comparable) moveLevelNumbers[i]).compareTo(referenceLevelNumbers[i]) > 0;
      var finalistIsHigher =
          ((Comparable) finalistLevelNumbers[i]).compareTo(referenceLevelNumbers[i]) > 0;
      if (moveIsHigher != finalistIsHigher) {
        return moveIsHigher ? 1 : -1;
      }
      if (moveIsHigher) {
        return 0;
      }
    }
    return 0;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareScores(Score<?> left, Score<?> right) {
    return ((Score) left).compareTo(right);
  }

  @Override
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    referenceScore = null;
    finalistScore = null;
  }
}
