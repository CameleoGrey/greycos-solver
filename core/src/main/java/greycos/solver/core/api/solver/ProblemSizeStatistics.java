package greycos.solver.core.api.solver;

import java.util.Collections;
import java.util.Locale;
import java.util.SequencedMap;

import greycos.solver.core.impl.util.MathUtils;

import org.jspecify.annotations.NullMarked;

/**
 * The statistics of a given problem submitted to a {@link Solver}.
 *
 * <p>Note: This type isn't meant to be constructed by a user.
 *
 * @param entityCount The number of genuine entities defined by the problem.
 * @param genuineEntityClassToEntityCount counts by the most specific configured entity class,
 *     including pinned entities and classes with zero instances
 * @param variableCount The number of genuine variables defined by the problem.
 * @param approximateValueCount The estimated number of values defined by the problem. Can be larger
 *     than the actual value count.
 * @param genuineEntityClassToVariableToValueCount value-range sizes for each effective variable;
 *     solution-provided ranges are counted once per variable and entity-provided ranges are summed.
 *     Inherited solution ranges appear in each class breakdown, but only once in the aggregate.
 * @param approximateProblemSizeLog The estimated log_10 of the problem's search space size.
 */
@NullMarked
public record ProblemSizeStatistics(
    long entityCount,
    SequencedMap<Class<?>, Long> genuineEntityClassToEntityCount,
    long variableCount,
    long approximateValueCount,
    SequencedMap<Class<?>, SequencedMap<String, Long>> genuineEntityClassToVariableToValueCount,
    double approximateProblemSizeLog) {

  /**
   * @deprecated this type isn't meant to be constructed by a user.
   */
  @Deprecated(forRemoval = true)
  public ProblemSizeStatistics(
      long entityCount,
      long variableCount,
      long approximateValueCount,
      double approximateProblemSizeLog) {
    this(
        entityCount,
        Collections.emptySortedMap(),
        variableCount,
        approximateValueCount,
        Collections.emptySortedMap(),
        approximateProblemSizeLog);
  }

  /** Return the {@link #approximateProblemSizeLog} as a fixed point integer. */
  public long approximateProblemScaleLogAsFixedPointLong() {
    return Math.round(approximateProblemSizeLog * MathUtils.LOG_PRECISION);
  }

  public String approximateProblemScaleAsFormattedString() {
    return MathUtils.approximateProblemScaleAsFormattedString(
        approximateProblemSizeLog, Locale.getDefault());
  }
}
