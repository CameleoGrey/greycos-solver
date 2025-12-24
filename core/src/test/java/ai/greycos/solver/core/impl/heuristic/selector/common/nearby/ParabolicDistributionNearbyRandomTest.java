package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import org.junit.jupiter.api.Test;

class ParabolicDistributionNearbyRandomTest {

  @Test
  void validConstruction() {
    ParabolicDistributionNearbyRandom random = new ParabolicDistributionNearbyRandom(100);
    assertEquals(100, random.getOverallSizeMaximum());
  }

  @Test
  void sizeMaximumTooSmall() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> new ParabolicDistributionNearbyRandom(0));
    assertEquals("The maximum (0) must be at least 1.", exception.getMessage());
  }

  @Test
  void nextIntWithLargeNearbySize() {
    ParabolicDistributionNearbyRandom random = new ParabolicDistributionNearbyRandom(100);
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
    ParabolicDistributionNearbyRandom random = new ParabolicDistributionNearbyRandom(100);
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
    ParabolicDistributionNearbyRandom random1 = new ParabolicDistributionNearbyRandom(100);
    ParabolicDistributionNearbyRandom random2 = new ParabolicDistributionNearbyRandom(100);
    ParabolicDistributionNearbyRandom random3 = new ParabolicDistributionNearbyRandom(50);

    assertEquals(random1, random2);
    assertEquals(random1.hashCode(), random2.hashCode());
    // Different sizeMaximum
    if (random1.equals(random3)) {
      throw new AssertionError("random1 should not equal random3");
    }
  }

  @Test
  void getOverallSizeMaximum() {
    ParabolicDistributionNearbyRandom random = new ParabolicDistributionNearbyRandom(100);
    assertEquals(100, random.getOverallSizeMaximum());
  }
}
