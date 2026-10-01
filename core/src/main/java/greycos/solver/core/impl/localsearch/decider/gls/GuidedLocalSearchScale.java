package greycos.solver.core.impl.localsearch.decider.gls;

import java.math.BigDecimal;
import java.util.ArrayDeque;
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
    // Both components may arrive as native decimal scores. Normalize to one integral fraction.
    var n = numerator.toBigDecimal();
    var d = denominator.toBigDecimal();
    int scale = Math.max(n.scale(), d.scale());
    var ni = n.scaleByPowerOfTen(scale).toBigIntegerExact();
    var di = d.scaleByPowerOfTen(scale).toBigIntegerExact();
    var common = ni.gcd(di);
    numerator = GuidedLocalSearchNumber.of(ni.divide(common));
    denominator = GuidedLocalSearchNumber.of(di.divide(common));
  }

  public static GuidedLocalSearchScale of(BigDecimal value) {
    return of(GuidedLocalSearchNumber.of(value), 1);
  }

  public static GuidedLocalSearchScale of(GuidedLocalSearchNumber value, int divisor) {
    var fraction = GuidedLocalSearchRational.of(value, divisor);
    return new GuidedLocalSearchScale(
        GuidedLocalSearchNumber.of(fraction.numerator()),
        GuidedLocalSearchNumber.of(fraction.denominator()));
  }

  public GuidedLocalSearchScale dividedBy(int divisor) {
    if (divisor <= 0) throw new IllegalArgumentException("The GLS scale divisor must be positive.");
    return new GuidedLocalSearchScale(numerator, denominator.multiply(divisor));
  }

  public GuidedLocalSearchScale multipliedBy(int factor) {
    if (factor <= 0) throw new IllegalArgumentException("The GLS scale factor must be positive.");
    return new GuidedLocalSearchScale(numerator.multiply(factor), denominator);
  }

  @Override
  public int compareTo(GuidedLocalSearchScale other) {
    return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
  }

  static GuidedLocalSearchScale median(List<GuidedLocalSearchScale> values) {
    if (values.isEmpty()) throw new IllegalArgumentException("GLS median requires observations.");
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

  /** Rolling scale data is learned only from coordinator-consumed eligible observations. */
  static final class Calibration {
    private static final int SAMPLE_LIMIT = 256;
    private final ArrayDeque<GuidedLocalSearchScale> observations = new ArrayDeque<>();
    private final boolean fixed;
    private GuidedLocalSearchScale scale;
    private boolean calibrated;
    private boolean changed;

    Calibration(BigDecimal override) {
      scale = override == null ? ONE : of(override);
      fixed = override != null;
      calibrated = fixed;
    }

    void observe(GuidedLocalSearchNumber scoreDifference, int automaticDifferenceCount) {
      if (fixed || automaticDifferenceCount == 0 || scoreDifference.signum() == 0) return;
      if (scoreDifference.signum() < 0)
        scoreDifference = GuidedLocalSearchNumber.ZERO.subtract(scoreDifference);
      if (observations.size() == SAMPLE_LIMIT) observations.removeFirst();
      observations.addLast(of(scoreDifference, automaticDifferenceCount));
      changed = true;
    }

    GuidedLocalSearchScale publishAtPenaltyUpdate() {
      if (!fixed && changed) {
        var next = median(new ArrayList<>(observations));
        if (calibrated) {
          var lower = scale.dividedBy(2);
          var upper = scale.multipliedBy(2);
          if (next.compareTo(lower) < 0) next = lower;
          else if (next.compareTo(upper) > 0) next = upper;
        }
        scale = next;
        calibrated = true;
        changed = false;
      }
      return scale;
    }

    GuidedLocalSearchScale scale() {
      return scale;
    }

    boolean calibrated() {
      return calibrated;
    }

    int observationCount() {
      return observations.size();
    }
  }
}
