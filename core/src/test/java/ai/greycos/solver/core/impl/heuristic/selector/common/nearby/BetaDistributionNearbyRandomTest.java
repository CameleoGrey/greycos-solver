package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import org.junit.jupiter.api.Test;

class BetaDistributionNearbyRandomTest {

  @Test
  void validConstruction() {
    BetaDistributionNearbyRandom random = new BetaDistributionNearbyRandom(1.0, 5.0);
    assertEquals(Integer.MAX_VALUE, random.getOverallSizeMaximum());
  }

  @Test
  void alphaTooSmall() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> new BetaDistributionNearbyRandom(0.0, 5.0));
    assertEquals("The alpha (0.0) must be positive.", exception.getMessage());
  }

  @Test
  void betaTooSmall() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> new BetaDistributionNearbyRandom(1.0, 0.0));
    assertEquals("The beta (0.0) must be positive.", exception.getMessage());
  }

  @Test
  void nextIntWithLargeNearbySize() {
    BetaDistributionNearbyRandom random = new BetaDistributionNearbyRandom(1.0, 5.0);
    Random rng = new Random(42);
    int nearbySize = 200;
    for (int i = 0; i < 100; i++) {
      int result = random.nextInt(rng, nearbySize);
      if (result < 0 || result >= 200) {
        throw new AssertionError("Result " + result + " is out of range [0, 200)");
      }
    }
  }

  @Test
  void nextIntWithSmallNearbySize() {
    BetaDistributionNearbyRandom random = new BetaDistributionNearbyRandom(1.0, 5.0);
    Random rng = new Random(42);
    int nearbySize = 50;
    for (int i = 0; i < 100; i++) {
      int result = random.nextInt(rng, nearbySize);
      if (result < 0 || result >= 50) {
        throw new AssertionError("Result " + result + " is out of range [0, 50)");
      }
    }
  }

  @Test
  void equalsAndHashCode() {
    BetaDistributionNearbyRandom random1 = new BetaDistributionNearbyRandom(1.0, 5.0);
    BetaDistributionNearbyRandom random2 = new BetaDistributionNearbyRandom(1.0, 5.0);
    BetaDistributionNearbyRandom random3 = new BetaDistributionNearbyRandom(2.0, 3.0);

    assertEquals(random1, random2);
    assertEquals(random1.hashCode(), random2.hashCode());
    // Different parameters
    if (random1.equals(random3)) {
      throw new AssertionError("random1 should not equal random3");
    }
  }

  @Test
  void getOverallSizeMaximum() {
    BetaDistributionNearbyRandom random = new BetaDistributionNearbyRandom(1.0, 5.0);
    assertEquals(Integer.MAX_VALUE, random.getOverallSizeMaximum());
  }
}
