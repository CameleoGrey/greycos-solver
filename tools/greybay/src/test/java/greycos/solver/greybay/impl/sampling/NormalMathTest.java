package greycos.solver.greybay.impl.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class NormalMathTest {
  // Independent mpmath 1.3.0, 100 decimal digits. Interval fixtures integrate exp(-a*t-t*t/2)
  // on [0,width] before adding the log density at a, avoiding underflow in the oracle itself.
  @Test
  void independentLogCdfFixtures() {
    double[][] fixtures = {
      {-100, -5005.5242086942050886263},
      {-40, -804.6084420137537881666},
      {-12, -75.4106730015687959388},
      {-8, -35.0134371599145498955},
      {Math.nextUp(-8.0), -35.0134371599145426823},
      {-1, -1.84102164500926350577},
      {0, -0.69314718055994530942},
      {1, -0.17275377902344988953}
    };
    for (double[] fixture : fixtures)
      assertThat(NormalMath.logCdf(fixture[0]))
          .isCloseTo(fixture[1], within(4 * Math.ulp(fixture[1])));
  }

  @Test
  void independentNarrowAndTailIntervalFixtures() {
    double[][] fixtures = {
      {-100, 1e-10, -5023.94478945814512954136},
      {-8, 1e-12, -60.54995964912922097011},
      {-1, 0.5, -1.89790506101398205597},
      {0, 1e-18, -42.36547020709749498256},
      {8, 0.125, -35.45903787231396050043},
      {35, 2, -616.97510126192251347324},
      {99, 0.005, -4906.95457095190102653050}
    };
    for (double[] fixture : fixtures)
      assertThat(NormalMath.logInterval(fixture[0], fixture[1]))
          .isCloseTo(fixture[2], within(8 * Math.ulp(fixture[2])));
  }

  @Test
  void independentQuantileFixturesIncludeBothDeclaredProbabilityEndpoints() {
    double[][] fixtures = {
      {Math.nextUp(0.0), -38.46740561714434625078},
      {Math.nextDown(1.0), 8.20953615160138685563},
      {0.000001, -4.75342430882289894819},
      {0.001, -3.09023230616781354154},
      {0.1, -1.28155156554460046697},
      {0.5, 0},
      {0.9, 1.28155156554460046697}
    };
    for (double[] fixture : fixtures)
      assertThat(NormalMath.inverseCdf(fixture[0])).isCloseTo(fixture[1], within(2e-14));
  }

  @Test
  void validatesUnresolvedIntervalsRatherThanSubstitutingUniformMass() {
    assertThatIllegalArgumentException().isThrownBy(() -> NormalMath.logInterval(1, 0));
    assertThatIllegalArgumentException().isThrownBy(() -> NormalMath.inverseCdf(0));
    assertThatIllegalArgumentException().isThrownBy(() -> NormalMath.inverseCdf(Double.NaN));
    assertThatIllegalArgumentException().isThrownBy(() -> NormalMath.logDifference(-1, -1));
  }
}
