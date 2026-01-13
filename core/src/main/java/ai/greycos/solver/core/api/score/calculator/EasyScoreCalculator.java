package ai.greycos.solver.core.api.score.calculator;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;

import org.jspecify.annotations.NonNull;

/**
 * Used for easy java {@link Score} calculation. This is non-incremental calculation, which is slow.
 *
 * <p>An implementation must be stateless.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @param <Score_> the score type to go with the solution
 */
public interface EasyScoreCalculator<Solution_, Score_ extends Score<Score_>> {

  /**
   * This method is only called if the {@link Score} cannot be predicted. The {@link Score} can be
   * predicted for example after an undo move.
   */
  @NonNull Score_ calculateScore(@NonNull Solution_ solution);
}
