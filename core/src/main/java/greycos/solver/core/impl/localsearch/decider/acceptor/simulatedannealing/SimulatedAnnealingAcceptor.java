package greycos.solver.core.impl.localsearch.decider.acceptor.simulatedannealing;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.localsearch.decider.acceptor.AbstractAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.AcceptorScoreMath;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.ScoreArithmetic;

/** The time gradient implementation of simulated annealing. */
public class SimulatedAnnealingAcceptor<Solution_> extends AbstractAcceptor<Solution_> {

  protected Score startingTemperature;

  protected int levelsLength = -1;
  protected double[] startingTemperatureLevels;
  // No protected Score temperature do avoid rounding errors when using Score.multiply(double)
  protected double[] temperatureLevels;
  private Number[] originalTemperatureLevels;
  private BigDecimal[] extendedTemperatureLevels;

  public void setStartingTemperature(Score startingTemperature) {
    this.startingTemperature = startingTemperature;
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    if (startingTemperature == null) {
      throw new IllegalArgumentException("The startingTemperature must be configured.");
    }
    originalTemperatureLevels = startingTemperature.toLevelNumbers();
    for (var startingTemperatureLevel : originalTemperatureLevels) {
      if (AcceptorScoreMath.signum(startingTemperatureLevel) < 0) {
        throw new IllegalArgumentException(
            "The startingTemperature ("
                + startingTemperature
                + ") cannot have negative level ("
                + startingTemperatureLevel
                + ").");
      }
    }
    startingTemperatureLevels = startingTemperature.toLevelDoubles();
    temperatureLevels = startingTemperatureLevels;
    levelsLength = startingTemperatureLevels.length;
    extendedTemperatureLevels = new BigDecimal[levelsLength];
  }

  @Override
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    startingTemperatureLevels = null;
    temperatureLevels = null;
    originalTemperatureLevels = null;
    extendedTemperatureLevels = null;
    levelsLength = -1;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public boolean isStructurallyValidSolutionAccepted(LocalSearchMoveScope<Solution_> moveScope) {
    var phaseScope = moveScope.getStepScope().getPhaseScope();
    // Guaranteed local search; no need for InnerScore.
    Score lastStepScore = phaseScope.getLastCompletedStepScope().getScore().raw();
    Score moveScore = moveScope.getScore().raw();
    if (moveScore.compareTo(lastStepScore) >= 0) {
      return true;
    }
    var moveScoreDifferenceLevels = AcceptorScoreMath.difference(lastStepScore, moveScore);
    var floatingDifference = FloatingScoreSupport.isFloatingScore(lastStepScore);
    var acceptChance = 1.0;
    for (var i = 0; i < levelsLength; i++) {
      var moveScoreDifferenceLevel = moveScoreDifferenceLevels[i];
      var temperatureLevel = temperatureLevels[i];
      if (moveScoreDifferenceLevel instanceof Long integralLoss
          && extendedTemperatureLevels[i] == null) {
        if (integralLoss > 0L) {
          // A zero temperature gives exp(-Infinity) = 0; the proposal still consumes one draw
          // below.
          acceptChance *= Math.exp(-integralLoss.doubleValue() / temperatureLevel);
        }
        continue;
      }
      double acceptChanceLevel;
      if (AcceptorScoreMath.signum(moveScoreDifferenceLevel) <= 0) {
        // In this level, moveScore is better than the lastStepScore, so do not disrupt the
        // acceptChance
        acceptChanceLevel = 1.0;
      } else if (extendedTemperatureLevels[i] != null) {
        var lossOverTemperature =
            FloatingScoreSupport.exact(moveScoreDifferenceLevel)
                .divide(extendedTemperatureLevels[i], MathContext.DECIMAL128)
                .doubleValue();
        acceptChanceLevel = Math.exp(-lossOverTemperature);
      } else if (temperatureLevel == 0.0) {
        acceptChanceLevel = 0.0;
      } else {
        var lossAsDouble = moveScoreDifferenceLevel.doubleValue();
        var lossOverTemperature =
            floatingDifference
                    || moveScoreDifferenceLevel instanceof BigInteger
                    || !Double.isFinite(lossAsDouble)
                    || lossAsDouble == 0.0
                ? ScoreArithmetic.ratio(moveScoreDifferenceLevel, temperatureLevel)
                : lossAsDouble / temperatureLevel;
        acceptChanceLevel = Math.exp(-lossOverTemperature);
      }
      acceptChance *= acceptChanceLevel;
    }
    return moveScope.getWorkingRandom().acceptorUsage().nextDouble() < acceptChance;
  }

  @Override
  public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
    super.stepStarted(stepScope);
    // TimeGradient only refreshes at the beginning of a step, so this code is in stepStarted
    // instead of stepEnded
    var timeGradient = stepScope.getTimeGradient();
    if (!Double.isFinite(timeGradient) || timeGradient < 0.0 || timeGradient > 1.0) {
      throw new IllegalStateException(
          "The simulated annealing timeGradient (%s) in phase (%s) must be finite and in [0, 1]. "
                  .formatted(timeGradient, stepScope.getPhaseScope().getPhaseIndex())
              + "Configure a termination that provides a time gradient, such as a spent-time "
              + "or phase move/step-count limit; diminished returns or asynchronous termination alone "
              + "cannot cool simulated annealing.");
    }
    var reverseTimeGradient = 1.0 - timeGradient;
    temperatureLevels = new double[levelsLength];
    for (var i = 0; i < levelsLength; i++) {
      extendedTemperatureLevels[i] = null;
      if (reverseTimeGradient == 0.0
          || AcceptorScoreMath.signum(originalTemperatureLevels[i]) == 0) {
        temperatureLevels[i] = 0.0;
      } else {
        var temperature = startingTemperatureLevels[i] * reverseTimeGradient;
        temperatureLevels[i] = temperature;
        if (!Double.isFinite(temperature) || temperature < Double.MIN_NORMAL) {
          extendedTemperatureLevels[i] =
              FloatingScoreSupport.exact(originalTemperatureLevels[i])
                  .multiply(BigDecimal.valueOf(reverseTimeGradient));
        }
      }
    }
    // TODO implement reheating
  }
}
