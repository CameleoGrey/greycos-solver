package greycos.solver.core.impl.alns;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.alns.AlnsAcceptancePolicy;
import greycos.solver.core.config.alns.AlnsAcceptanceType;

/** Acceptance over immutable complete candidate scores, independently of local-search foraging. */
public final class DefaultAlnsAcceptance<Score_ extends Score<Score_>>
    implements AlnsAcceptancePolicy<Score_> {
  private final AlnsAcceptanceType type;
  private final int historySize;
  private final Score_ startingTemperature;
  private final double coolingRate;
  private List<Score_> history;
  private int historyIndex;
  private BigDecimal[] temperature;

  public DefaultAlnsAcceptance(
      AlnsAcceptanceType type, int historySize, Score_ startingTemperature, double coolingRate) {
    if (historySize < 1) {
      throw new IllegalArgumentException("ALNS lateAcceptanceSize must be positive.");
    }
    if (!Double.isFinite(coolingRate) || coolingRate <= 0.0 || coolingRate > 1.0) {
      throw new IllegalArgumentException("ALNS coolingRate must be in (0, 1].");
    }
    if (type == AlnsAcceptanceType.SIMULATED_ANNEALING && startingTemperature == null) {
      throw new IllegalArgumentException("ALNS simulated annealing requires startingTemperature.");
    }
    this.type = type;
    this.historySize = historySize;
    this.startingTemperature = startingTemperature;
    this.coolingRate = coolingRate;
  }

  @Override
  public void initialize(Score_ incumbent) {
    history = new ArrayList<>(Collections.nCopies(historySize, incumbent));
    historyIndex = 0;
    if (startingTemperature != null) {
      var levels = startingTemperature.toLevelNumbers();
      if (levels.length != incumbent.toLevelNumbers().length) {
        throw new IllegalArgumentException(
            "ALNS temperature must have the incumbent's score levels.");
      }
      temperature = new BigDecimal[levels.length];
      for (int i = 0; i < levels.length; i++) {
        temperature[i] = AlnsScoreMath.decimal(levels[i]);
        if (temperature[i].signum() < 0) {
          throw new IllegalArgumentException(
              "ALNS startingTemperature levels must be nonnegative.");
        }
      }
    }
  }

  @Override
  public boolean isAccepted(Score_ current, Score_ candidate, RandomGenerator random) {
    if (candidate.compareTo(current) >= 0) {
      return true;
    }
    return switch (type) {
      case HILL_CLIMBING -> false;
      case LATE_ACCEPTANCE -> candidate.compareTo(history.get(historyIndex)) >= 0;
      case SIMULATED_ANNEALING -> annealingAccepts(current, candidate, random);
    };
  }

  private boolean annealingAccepts(Score_ current, Score_ candidate, RandomGenerator random) {
    var loss = AlnsScoreMath.difference(current, candidate);
    double logProbability = 0.0;
    for (int i = 0; i < loss.length; i++) {
      if (loss[i].signum() > 0) {
        if (temperature[i].signum() == 0) {
          return false;
        }
        logProbability -= loss[i].divide(temperature[i], MathContext.DECIMAL128).doubleValue();
      }
    }
    return random.nextDouble() < Math.exp(logProbability);
  }

  @Override
  public void stepEnded(Score_ incumbent) {
    history.set(historyIndex, incumbent);
    historyIndex = (historyIndex + 1) % historySize;
    if (temperature != null) {
      var factor = BigDecimal.valueOf(coolingRate);
      var minimum = new BigDecimal("1E-100");
      for (int i = 0; i < temperature.length; i++) {
        if (temperature[i].signum() > 0) {
          temperature[i] =
              temperature[i]
                  .multiply(factor, MathContext.DECIMAL128)
                  .max(minimum.min(temperature[i]));
        }
      }
    }
  }

  @Override
  public void incumbentChanged(Score_ incumbent) {
    Collections.fill(history, incumbent);
    historyIndex = 0;
  }
}
