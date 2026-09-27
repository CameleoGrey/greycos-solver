package greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.random.RandomGenerator;

import org.jspecify.annotations.NonNull;

/**
 * Selects nearby indices according to a probability distribution. Implementations should be equal
 * if they represent the same distribution.
 */
public interface NearbyRandom {

  int nextInt(@NonNull RandomGenerator random, int nearbySize);

  /**
   * Samples a retained nearby rank. The {@code populationSize} is the eligible destination count
   * before sorting is limited; {@code nearbySize} is the number of retained ranks available for
   * selection, with {@code 0 < nearbySize <= populationSize}. A ratio-based distribution uses the
   * full population to calculate its support, then intersects it with the retained ranks. An
   * explicit sorting limit therefore still limits which destinations may be selected. When {@link
   * #requiresPopulationSize()} is false, the retained count may be supplied as {@code
   * populationSize} instead of counting the complete eligible population.
   */
  default int nextInt(@NonNull RandomGenerator random, int populationSize, int nearbySize) {
    return nextInt(random, nearbySize);
  }

  /**
   * Whether sampling needs the complete eligible population. Returning false guarantees that
   * replacing {@code populationSize} with {@code nearbySize} preserves both the selected rank and
   * random-generator consumption. The conservative default keeps population-dependent
   * implementations correct.
   */
  default boolean requiresPopulationSize() {
    return true;
  }

  int getOverallSizeMaximum();
}
