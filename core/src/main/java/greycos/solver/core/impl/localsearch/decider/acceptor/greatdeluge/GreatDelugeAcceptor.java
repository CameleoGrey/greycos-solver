package greycos.solver.core.impl.localsearch.decider.acceptor.greatdeluge;

import java.math.BigDecimal;
import java.util.Arrays;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.localsearch.decider.acceptor.AbstractAcceptor;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.FloatingScoreSupport;

public class GreatDelugeAcceptor<Solution_> extends AbstractAcceptor<Solution_> {

  // Guaranteed inside local search, therefore no need for InnerScore.
  private Score initialWaterLevel;
  private Score waterLevelIncrementScore;
  private Double waterLevelIncrementRatio;
  private Score startingWaterLevel = null;
  private Score currentWaterLevel = null;
  private Double currentWaterLevelRatio = null;
  // A water bound is an internal comparison value, and may exceed the finite public score range.
  private BigDecimal[] floatingStartingWaterLevels;
  private BigDecimal[] floatingCurrentWaterLevels;
  private BigDecimal[] floatingWaterLevelIncrements;
  private BigDecimal floatingWaterLevelRatio;
  private long floatingWaterStructuralScore;

  public Score getWaterLevelIncrementScore() {
    return this.waterLevelIncrementScore;
  }

  public void setWaterLevelIncrementScore(Score waterLevelIncrementScore) {
    this.waterLevelIncrementScore = waterLevelIncrementScore;
  }

  public Score getInitialWaterLevel() {
    return this.initialWaterLevel;
  }

  public void setInitialWaterLevel(Score initialLevel) {
    this.initialWaterLevel = initialLevel;
  }

  public Double getWaterLevelIncrementRatio() {
    return this.waterLevelIncrementRatio;
  }

  public void setWaterLevelIncrementRatio(Double waterLevelIncrementRatio) {
    this.waterLevelIncrementRatio = waterLevelIncrementRatio;
  }

  @Override
  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    startingWaterLevel =
        initialWaterLevel != null ? initialWaterLevel : phaseScope.getBestScore().raw();
    if (waterLevelIncrementRatio != null) {
      currentWaterLevelRatio = 0.0;
    }
    currentWaterLevel = startingWaterLevel;
    if (FloatingScoreSupport.isFloatingScore(startingWaterLevel)) {
      floatingStartingWaterLevels = exactLevels(startingWaterLevel);
      floatingCurrentWaterLevels = floatingStartingWaterLevels.clone();
      floatingWaterStructuralScore = startingWaterLevel.structuralScore();
      floatingWaterLevelRatio = BigDecimal.ZERO;
      if (waterLevelIncrementScore != null) {
        FloatingScoreSupport.validateCompatible(startingWaterLevel, waterLevelIncrementScore);
        floatingWaterLevelIncrements = exactLevels(waterLevelIncrementScore);
      }
    }
  }

  @Override
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    startingWaterLevel = null;
    if (waterLevelIncrementRatio != null) {
      currentWaterLevelRatio = null;
    }
    currentWaterLevel = null;
    floatingStartingWaterLevels = null;
    floatingCurrentWaterLevels = null;
    floatingWaterLevelIncrements = null;
    floatingWaterLevelRatio = null;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public boolean isStructurallyValidSolutionAccepted(LocalSearchMoveScope moveScope) {
    var moveScore = moveScope.getScore().raw();
    if (compareToWaterLevel(moveScore) >= 0) {
      return true;
    }
    var lastStepScore =
        moveScope.getStepScope().getPhaseScope().getLastCompletedStepScope().getScore().raw();
    return moveScore.compareTo(lastStepScore) > 0; // Aspiration
  }

  private static BigDecimal[] exactLevels(Score<?> score) {
    return Arrays.stream(score.toLevelNumbers())
        .map(FloatingScoreSupport::exact)
        .toArray(BigDecimal[]::new);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private int compareToWaterLevel(Score score) {
    if (floatingCurrentWaterLevels == null) {
      return score.compareTo(currentWaterLevel);
    }
    FloatingScoreSupport.validateCompatible(startingWaterLevel, score);
    var comparison = Long.compare(score.structuralScore(), floatingWaterStructuralScore);
    if (comparison != 0) {
      return comparison;
    }
    for (int i = 0; i < floatingCurrentWaterLevels.length; i++) {
      comparison =
          FloatingScoreSupport.exact(FloatingScoreSupport.level(score, i))
              .compareTo(floatingCurrentWaterLevels[i]);
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }

  @Override
  public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
    super.stepEnded(stepScope);
    if (floatingCurrentWaterLevels != null) {
      if (floatingWaterLevelIncrements != null) {
        for (int i = 0; i < floatingCurrentWaterLevels.length; i++) {
          floatingCurrentWaterLevels[i] =
              floatingCurrentWaterLevels[i].add(floatingWaterLevelIncrements[i]);
        }
      } else {
        floatingWaterLevelRatio =
            floatingWaterLevelRatio.add(FloatingScoreSupport.exact(waterLevelIncrementRatio));
        for (int i = 0; i < floatingCurrentWaterLevels.length; i++) {
          var start = floatingStartingWaterLevels[i];
          floatingCurrentWaterLevels[i] = start.add(start.abs().multiply(floatingWaterLevelRatio));
        }
      }
      floatingWaterStructuralScore = 0L;
    } else if (waterLevelIncrementScore != null) {
      currentWaterLevel = currentWaterLevel.add(waterLevelIncrementScore);
    } else {
      // Avoid numerical instability: SimpleScore.of(500).multiply(0.000_001) underflows to zero
      currentWaterLevelRatio += waterLevelIncrementRatio;
      currentWaterLevel =
          startingWaterLevel.add(
              // TODO
              // targetWaterLevel.subtract(startingWaterLevel).multiply(waterLevelIncrementRatio);
              // Use startingWaterLevel.abs() to keep the number being positive.
              startingWaterLevel.abs().multiply(currentWaterLevelRatio));
    }
  }
}
