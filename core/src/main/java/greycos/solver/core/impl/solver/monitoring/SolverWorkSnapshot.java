package greycos.solver.core.impl.solver.monitoring;

import java.util.HashMap;
import java.util.Map;

/** Immutable cumulative work, copied on its owning solver thread. */
public record SolverWorkSnapshot(
    long scoreCalculationCount, long moveEvaluationCount, Map<String, Long> moveCountsByType) {

  public static final SolverWorkSnapshot ZERO = new SolverWorkSnapshot(0L, 0L, Map.of());

  public SolverWorkSnapshot {
    if (scoreCalculationCount < 0L || moveEvaluationCount < 0L) {
      throw new IllegalArgumentException(
          "Work counts must not be negative (scoreCalculationCount="
              + scoreCalculationCount
              + ", moveEvaluationCount="
              + moveEvaluationCount
              + ").");
    }
    moveCountsByType = Map.copyOf(moveCountsByType);
  }

  public SolverWorkSnapshot plus(SolverWorkSnapshot other) {
    var counts = new HashMap<>(moveCountsByType);
    other.moveCountsByType.forEach((type, count) -> counts.merge(type, count, Long::sum));
    return new SolverWorkSnapshot(
        scoreCalculationCount + other.scoreCalculationCount,
        moveEvaluationCount + other.moveEvaluationCount,
        counts);
  }
}
