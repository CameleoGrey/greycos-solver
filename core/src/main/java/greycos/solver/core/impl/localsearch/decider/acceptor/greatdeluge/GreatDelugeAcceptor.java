package greycos.solver.core.impl.localsearch.decider.acceptor.greatdeluge;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
  private double currentWaterLevelRatio;
  // A water bound is an internal comparison value, and may exceed the finite public score range.
  private BigDecimal[] floatingStartingWaterLevels;
  private BigDecimal[] floatingCurrentWaterLevels;
  private BigDecimal[] floatingWaterLevelIncrements;
  private BigDecimal floatingWaterLevelRatio;
  private long floatingWaterStructuralScore;
  private IntegralWaterLevel integralWaterLevel;
  private BigDecimal extendedWaterLevelRatio;
  private BigDecimal[] decimalCurrentWaterLevels;

  public Score getWaterLevelIncrementScore() {
    return this.waterLevelIncrementScore;
  }

  @SuppressWarnings("unchecked")
  public void setWaterLevelIncrementScore(Score waterLevelIncrementScore) {
    if (waterLevelIncrementScore != null
        && (waterLevelIncrementScore.structuralScore() != 0L
            || waterLevelIncrementScore.compareTo(waterLevelIncrementScore.zero()) <= 0)) {
      throw new IllegalArgumentException(
          "The waterLevelIncrementScore ("
              + waterLevelIncrementScore
              + ") must be positive with a zero structural score because the water level should increase.");
    }
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
    if (waterLevelIncrementRatio != null
        && (!Double.isFinite(waterLevelIncrementRatio) || waterLevelIncrementRatio <= 0.0)) {
      throw new IllegalArgumentException(
          "The waterLevelIncrementRatio ("
              + waterLevelIncrementRatio
              + ") must be finite and positive because the water level should increase.");
    }
    this.waterLevelIncrementRatio = waterLevelIncrementRatio;
  }

  @Override
  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    if ((waterLevelIncrementScore == null) == (waterLevelIncrementRatio == null)) {
      throw new IllegalArgumentException(
          "Great Deluge requires exactly one of waterLevelIncrementScore ("
              + waterLevelIncrementScore
              + ") and waterLevelIncrementRatio ("
              + waterLevelIncrementRatio
              + ").");
    }
    startingWaterLevel =
        initialWaterLevel != null ? initialWaterLevel : phaseScope.getBestScore().raw();
    if (waterLevelIncrementRatio != null) {
      currentWaterLevelRatio = 0.0;
    }
    currentWaterLevel = startingWaterLevel;
    if (waterLevelIncrementScore != null) {
      // This also checks compatible score families and bendable dimensions without changing state.
      validateCompatible(startingWaterLevel, waterLevelIncrementScore);
    }
    integralWaterLevel = IntegralWaterLevel.create(startingWaterLevel, waterLevelIncrementScore);
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
      currentWaterLevelRatio = 0.0;
    }
    currentWaterLevel = null;
    floatingStartingWaterLevels = null;
    floatingCurrentWaterLevels = null;
    floatingWaterLevelIncrements = null;
    floatingWaterLevelRatio = null;
    integralWaterLevel = null;
    extendedWaterLevelRatio = null;
    decimalCurrentWaterLevels = null;
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
  private static void validateCompatible(Score left, Score right) {
    left.compareTo(right);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private int compareToWaterLevel(Score score) {
    if (integralWaterLevel != null && integralWaterLevel.isWidened()) {
      validateCompatible(startingWaterLevel, score);
      var structuralComparison = Long.compare(score.structuralScore(), 0L);
      return structuralComparison != 0 ? structuralComparison : integralWaterLevel.compare(score);
    }
    if (decimalCurrentWaterLevels != null) {
      validateCompatible(startingWaterLevel, score);
      var structuralComparison = Long.compare(score.structuralScore(), 0L);
      if (structuralComparison != 0) {
        return structuralComparison;
      }
      var levels = score.toLevelNumbers();
      for (int i = 0; i < levels.length; i++) {
        int comparison =
            FloatingScoreSupport.exact(levels[i]).compareTo(decimalCurrentWaterLevels[i]);
        if (comparison != 0) {
          return comparison;
        }
      }
      return 0;
    }
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
      if (integralWaterLevel != null) {
        integralWaterLevel.increment();
        if (!integralWaterLevel.isWidened()) {
          currentWaterLevel = integralWaterLevel.toScore();
        }
      } else {
        currentWaterLevel = currentWaterLevel.add(waterLevelIncrementScore);
      }
    } else {
      // Avoid numerical instability: SimpleScore.of(500).multiply(0.000_001) underflows to zero
      advanceRatio();
      if (integralWaterLevel != null) {
        integralWaterLevel.applyRatio(currentWaterLevelRatio, extendedWaterLevelRatio);
        if (!integralWaterLevel.isWidened()) {
          currentWaterLevel = integralWaterLevel.toScore();
        }
        return;
      } else if (extendedWaterLevelRatio != null) {
        var starts = exactLevels(startingWaterLevel);
        decimalCurrentWaterLevels = new BigDecimal[starts.length];
        for (int i = 0; i < starts.length; i++) {
          var start = starts[i];
          decimalCurrentWaterLevels[i] =
              start.add(
                  start
                      .abs()
                      .multiply(extendedWaterLevelRatio)
                      .setScale(Math.max(0, start.scale()), RoundingMode.FLOOR));
        }
        return;
      }
      currentWaterLevel =
          startingWaterLevel.add(
              // TODO
              // targetWaterLevel.subtract(startingWaterLevel).multiply(waterLevelIncrementRatio);
              // Use startingWaterLevel.abs() to keep the number being positive.
              startingWaterLevel.abs().multiply(currentWaterLevelRatio));
    }
  }

  private void advanceRatio() {
    if (extendedWaterLevelRatio != null) {
      extendedWaterLevelRatio =
          extendedWaterLevelRatio.add(BigDecimal.valueOf(waterLevelIncrementRatio));
      return;
    }
    double nextRatio = currentWaterLevelRatio + waterLevelIncrementRatio;
    if (Double.isFinite(nextRatio)) {
      currentWaterLevelRatio = nextRatio;
    } else {
      extendedWaterLevelRatio =
          BigDecimal.valueOf(currentWaterLevelRatio)
              .add(BigDecimal.valueOf(waterLevelIncrementRatio));
    }
  }
}
