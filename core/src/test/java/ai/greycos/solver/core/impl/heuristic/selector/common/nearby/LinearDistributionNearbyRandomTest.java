package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import org.junit.jupiter.api.Test;

class LinearDistributionNearbyRandomTest {

  @Test
  void validConstruction() {
    LinearDistributionNearbyRandom random = new LinearDistributionNearbyRandom(100);
    assertEquals(100, random.getOverallSizeMaximum());
  }

  @Test
  void sizeMaximumTooSmall() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new LinearDistributionNearbyRandom(0));
    assertEquals("The maximum (0) must be at least 1.", exception.getMessage());
  }

  @Test
  void nextIntWithLargeNearbySize() {
    LinearDistributionNearbyRandom random = new LinearDistributionNearbyRandom(100);
    Random rng = new Random(42);
    int nearbySize = 200;
    // With nearbySize > sizeMaximum, m = sizeMaximum = 100
    for (int i = 0; i < 100; i++) {
      int result = random.nextInt(rng, nearbySize);
      if (result < 0 || result >= 100) {
        throw new AssertionError("Result " + result + " is out of range [0, 100)");
      }
    }
  }

  @Test
  void nextIntWithSmallNearbySize() {
    LinearDistributionNearbyRandom random = new LinearDistributionNearbyRandom(100);
    Random rng = new Random(42);
    int nearbySize = 50;
    // With nearbySize < sizeMaximum, m = nearbySize = 50
    for (int i = 0; i < 100; i++) {
      int result = random.nextInt(rng, nearbySize);
      if (result < 0 || result >= 50) {
        throw new AssertionError("Result " + result + " is out of range [0, 50)");
      }
    }
  }

  @Test
  void equalsAndHashCode() {
    LinearDistributionNearbyRandom random1 = new LinearDistributionNearbyRandom(100);
    LinearDistributionNearbyRandom random2 = new LinearDistributionNearbyRandom(100);
    LinearDistributionNearbyRandom random3 = new LinearDistributionNearbyRandom(50);

    assertEquals(random1, random2);
    assertEquals(random1.hashCode(), random2.hashCode());
    // Different sizeMaximum
    if (random1.equals(random3)) {
      throw new AssertionError("random1 should not equal random3");
    }
  }

  @Test
  void getOverallSizeMaximum() {
    LinearDistributionNearbyRandom random = new LinearDistributionNearbyRandom(100);
    assertEquals(100, random.getOverallSizeMaximum());
  }
}
