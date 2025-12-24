package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Random;

import org.jspecify.annotations.NonNull;

/**
 * Strategy pattern to select a index of a nearby ordered value range according to a probability
 * distribution.
 *
 * <p>It is recommended that instances be {@link Object#equals equal} if they represent the same
 * random function, in order to support nearby entity selector equality.
 */
public interface NearbyRandom {

  /**
   * Selects a nearby index according to the probability distribution.
   *
   * @param random never null
   * @param nearbySize never negative. The number of available values to select from. Normally this
   *     is the size of the value range for a non-chained variable and the size of the value range
   *     (= size of the entity list) minus 1 for a chained variable.
   * @return {@code 0 <= x < nearbySize}
   */
  int nextInt(@NonNull Random random, int nearbySize);

  /**
   * Used to limit the RAM memory size of the nearby distance matrix.
   *
   * @return one more than the maximum number that {@link #nextInt(Random, int)} can return, {@link
   *     Integer#MAX_VALUE} if there is none
   */
  int getOverallSizeMaximum();
}
