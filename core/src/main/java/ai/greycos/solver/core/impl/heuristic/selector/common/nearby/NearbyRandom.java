package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.random.RandomGenerator;

import org.jspecify.annotations.NonNull;

/**
 * Selects nearby indices according to a probability distribution. Implementations should be equal
 * if they represent the same distribution.
 */
public interface NearbyRandom {

  int nextInt(@NonNull RandomGenerator random, int nearbySize);

  int getOverallSizeMaximum();
}
