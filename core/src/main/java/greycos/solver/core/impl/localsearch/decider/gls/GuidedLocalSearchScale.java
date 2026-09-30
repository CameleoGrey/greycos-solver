package greycos.solver.core.impl.localsearch.decider.gls;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Exact score units per automatic feature, including non-terminating rational medians. */
public record GuidedLocalSearchScale(
    GuidedLocalSearchNumber numerator, GuidedLocalSearchNumber denominator)
    implements Comparable<GuidedLocalSearchScale> {

  public static final GuidedLocalSearchScale ONE =
      new GuidedLocalSearchScale(GuidedLocalSearchNumber.ONE, GuidedLocalSearchNumber.ONE);

  public GuidedLocalSearchScale {
    if (numerator.signum() <= 0 || denominator.signum() <= 0) {
      throw new IllegalArgumentException("GLS scale numerator and denominator must be positive.");
    }
  }

  public static GuidedLocalSearchScale of(BigDecimal value) {
    return of(GuidedLocalSearchNumber.of(value), 1);
  }

  public static GuidedLocalSearchScale of(GuidedLocalSearchNumber value, int divisor) {
    var decimal = value.toBigDecimal();
    var numerator = decimal.unscaledValue();
    var denominator = BigInteger.valueOf(divisor);
    if (decimal.scale() > 0) {
      denominator = denominator.multiply(BigInteger.TEN.pow(decimal.scale()));
    } else if (decimal.scale() < 0) {
      numerator = numerator.multiply(BigInteger.TEN.pow(-decimal.scale()));
    }
    var gcd = numerator.gcd(denominator);
    return new GuidedLocalSearchScale(
        GuidedLocalSearchNumber.of(numerator.divide(gcd)),
        GuidedLocalSearchNumber.of(denominator.divide(gcd)));
  }

  @Override
  public int compareTo(GuidedLocalSearchScale other) {
    return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
  }

  static GuidedLocalSearchScale median(List<GuidedLocalSearchScale> values) {
    var sorted = new ArrayList<>(values);
    sorted.sort(null);
    int middle = sorted.size() / 2;
    if (sorted.size() % 2 != 0) return sorted.get(middle);
    var left = sorted.get(middle - 1);
    var right = sorted.get(middle);
    return new GuidedLocalSearchScale(
        left.numerator.multiply(right.denominator).add(right.numerator.multiply(left.denominator)),
        left.denominator.multiply(right.denominator).multiply(2));
  }

  /** Collects at most the first 128 informative, coordinator-consumed candidate observations. */
  static final class Calibration {
    private static final int SAMPLE_LIMIT = 128;
    private final List<GuidedLocalSearchScale> observations = new ArrayList<>();
    private GuidedLocalSearchScale scale;
    private boolean frozen;

    Calibration(BigDecimal override) {
      scale = override == null ? ONE : of(override);
      frozen = override != null;
    }

    void observe(GuidedLocalSearchNumber scoreDifference, int automaticDifferenceCount) {
      if (frozen || observations.size() == SAMPLE_LIMIT || scoreDifference.signum() == 0) return;
      if (scoreDifference.signum() < 0)
        scoreDifference = GuidedLocalSearchNumber.ZERO.subtract(scoreDifference);
      observations.add(of(scoreDifference, Math.max(1, automaticDifferenceCount)));
    }

    GuidedLocalSearchScale freezeAtPenaltyUpdate() {
      if (!frozen && !observations.isEmpty()) {
        scale = median(observations);
        frozen = true;
        observations.clear();
      }
      return scale;
    }

    GuidedLocalSearchScale scale() {
      return scale;
    }
  }
}
