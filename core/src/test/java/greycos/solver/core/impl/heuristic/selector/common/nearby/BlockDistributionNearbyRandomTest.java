package greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BlockDistributionNearbyRandomTest {

  @Test
  void validConstruction() {
    BlockDistributionNearbyRandom random = new BlockDistributionNearbyRandom(5, 50, 0.1, 0.2);
    // Any non-zero uniform probability may select from the entire destination range.
    assertEquals(Integer.MAX_VALUE, random.getOverallSizeMaximum());
  }

  @Test
  void sizeMinimumTooSmall() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BlockDistributionNearbyRandom(0, 50, 0.1, 0.2));
    assertEquals("The sizeMinimum (0) must be at least 1.", exception.getMessage());
  }

  @Test
  void sizeMaximumLessThanSizeMinimum() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BlockDistributionNearbyRandom(10, 5, 0.1, 0.2));
    assertEquals(
        "The sizeMaximum (5) must be at least the sizeMinimum (10).", exception.getMessage());
  }

  @Test
  void sizeRatioTooLow() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BlockDistributionNearbyRandom(5, 50, -0.1, 0.2));
    assertEquals(
        "The sizeRatio (-0.1) must be finite and between 0.0 and 1.0.", exception.getMessage());
  }

  @Test
  void sizeRatioTooHigh() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BlockDistributionNearbyRandom(5, 50, 1.1, 0.2));
    assertEquals(
        "The sizeRatio (1.1) must be finite and between 0.0 and 1.0.", exception.getMessage());
  }

  @Test
  void uniformDistributionProbabilityTooLow() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BlockDistributionNearbyRandom(5, 50, 0.1, -0.1));
    assertEquals(
        "The uniformDistributionProbability (-0.1) must be finite and between 0.0 and 1.0.",
        exception.getMessage());
  }

  @Test
  void uniformDistributionProbabilityTooHigh() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BlockDistributionNearbyRandom(5, 50, 0.1, 1.1));
    assertEquals(
        "The uniformDistributionProbability (1.1) must be finite and between 0.0 and 1.0.",
        exception.getMessage());
  }

  @ParameterizedTest
  @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
  void rejectsNonFiniteParametersBeforeSampling(double value) {
    var ratioException =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BlockDistributionNearbyRandom(1, 40, value, 0.0));
    assertEquals(
        "The sizeRatio (" + value + ") must be finite and between 0.0 and 1.0.",
        ratioException.getMessage());
    var probabilityException =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BlockDistributionNearbyRandom(1, 40, 1.0, value));
    assertEquals(
        "The uniformDistributionProbability ("
            + value
            + ") must be finite and between 0.0 and 1.0.",
        probabilityException.getMessage());
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 1.0})
  void acceptsProbabilityAndRatioBoundaries(double value) {
    var random = new BlockDistributionNearbyRandom(1, 40, value, value);

    int expectedMaximum = value == 0.0 ? 40 : Integer.MAX_VALUE;
    assertEquals(expectedMaximum, random.getOverallSizeMaximum());
  }

  @Test
  void factoryRejectsNonFiniteConfigurationBeforeSampling() {
    var config =
        new NearbySelectionConfig().withBlockDistributionUniformDistributionProbability(Double.NaN);

    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> NearbyRandomFactory.create(config).buildNearbyRandom(true));

    assertEquals(
        "The uniformDistributionProbability (NaN) must be finite and between 0.0 and 1.0.",
        exception.getMessage());
  }

  @Test
  void nextIntWithUniformProbability() {
    BlockDistributionNearbyRandom random = new BlockDistributionNearbyRandom(5, 10, 0.5, 1.0);
    // With uniformDistributionProbability = 1.0, should always return random from full range
    Random rng = new Random(42);
    for (int i = 0; i < 100; i++) {
      int result = random.nextInt(rng, 100);
      if (result < 0 || result >= 100) {
        throw new AssertionError("Result " + result + " is out of range [0, 100)");
      }
    }
  }

  @Test
  void nextIntWithBlockDistribution() {
    BlockDistributionNearbyRandom random = new BlockDistributionNearbyRandom(5, 10, 0.5, 0.0);
    // With sizeRatio = 0.5, nearbySize = 100, block size = min(10, max(5, 50))
    // block size should be 10
    Random rng = new Random(42);
    for (int i = 0; i < 100; i++) {
      int result = random.nextInt(rng, 100);
      if (result < 0 || result >= 10) {
        throw new AssertionError("Result " + result + " is out of range [0, 10)");
      }
    }
  }

  @Test
  void nextIntWithSmallNearbySize() {
    BlockDistributionNearbyRandom random = new BlockDistributionNearbyRandom(10, 50, 1.0, 0.0);
    // With sizeRatio = 1.0 and nearbySize = 5, block size = min(10, 5) = 5
    Random rng = new Random(42);
    for (int i = 0; i < 100; i++) {
      int result = random.nextInt(rng, 5);
      if (result < 0 || result >= 5) {
        throw new AssertionError("Result " + result + " is out of range [0, 5)");
      }
    }
  }

  @Test
  void truncatedPopulationUsesOriginalRatio() {
    var random = new BlockDistributionNearbyRandom(1, 100, 0.5, 0.0);
    var rng =
        new Random(0) {
          @Override
          public int nextInt(int bound) {
            return bound - 1;
          }
        };

    assertEquals(99, random.nextInt(rng, 1000, 100));
    assertEquals(49, random.nextInt(rng, 100, 100));
    assertEquals(24, random.nextInt(rng, 1000, 25));
  }

  @Test
  void uniformExplorationRespectsExplicitRetainedLimit() {
    var random = new BlockDistributionNearbyRandom(1, 100, 0.5, 1.0);
    var rng =
        new Random(0) {
          @Override
          public int nextInt(int bound) {
            return bound - 1;
          }
        };

    assertEquals(1999, random.nextInt(rng, 2000, 2000));
    assertEquals(249, random.nextInt(rng, 2000, 250));
  }

  @Test
  void equalsAndHashCode() {
    BlockDistributionNearbyRandom random1 = new BlockDistributionNearbyRandom(5, 50, 0.1, 0.2);
    BlockDistributionNearbyRandom random2 = new BlockDistributionNearbyRandom(5, 50, 0.1, 0.2);
    BlockDistributionNearbyRandom random3 = new BlockDistributionNearbyRandom(6, 50, 0.1, 0.2);

    assertEquals(random1, random2);
    assertEquals(random1.hashCode(), random2.hashCode());
    // Different sizeMinimum
    if (random1.equals(random3)) {
      throw new AssertionError("random1 should not equal random3");
    }
  }

  @Test
  void getOverallSizeMaximumWithUniformProbability() {
    BlockDistributionNearbyRandom random = new BlockDistributionNearbyRandom(5, 50, 0.1, 1.0);
    // With uniformDistributionProbability > 0, should return MAX_VALUE
    assertEquals(Integer.MAX_VALUE, random.getOverallSizeMaximum());
  }

  @Test
  void getOverallSizeMaximumWithoutUniformProbability() {
    BlockDistributionNearbyRandom random = new BlockDistributionNearbyRandom(5, 50, 0.1, 0.0);
    // With uniformDistributionProbability = 0, should return sizeMaximum
    assertEquals(50, random.getOverallSizeMaximum());
  }
}
