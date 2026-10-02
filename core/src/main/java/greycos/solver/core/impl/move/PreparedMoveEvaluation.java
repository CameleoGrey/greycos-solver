package greycos.solver.core.impl.move;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.Nullable;

/** The outcome of preparing one candidate; all temporary state has already been restored. */
public record PreparedMoveEvaluation<Solution_, Score_ extends Score<Score_>>(
    Status status,
    @Nullable Move<Solution_> move,
    @Nullable InnerScore<Score_> score,
    long calculationCount) {

  public PreparedMoveEvaluation {
    if (status == null
        || calculationCount < 0
        || (status == Status.EVALUATED) != (move != null && score != null)
        || (status != Status.EVALUATED && (move != null || score != null))) {
      throw new IllegalArgumentException("Inconsistent prepared move evaluation (" + status + ").");
    }
  }

  public enum Status {
    EVALUATED,
    EMPTY,
    CANCELLED
  }
}
