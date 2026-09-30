package greycos.solver.core.impl.score.stream.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import greycos.solver.core.api.function.ToFloatFunction;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.bi.BiConstraintCollector;
import greycos.solver.core.api.score.stream.quad.QuadConstraintCollector;
import greycos.solver.core.api.score.stream.tri.TriConstraintCollector;
import greycos.solver.core.api.score.stream.uni.UniConstraintCollector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FloatingPointConstraintCollectorsTest {

  static Stream<Configuration> collectors() {
    return Stream.of(1, 2, 3, 4)
        .flatMap(
            arity ->
                Stream.of(true, false)
                    .flatMap(
                        single ->
                            Stream.of(false, true)
                                .map(average -> new Configuration(arity, single, average))));
  }

  @ParameterizedTest
  @MethodSource("collectors")
  void insertionPermutationAndArbitraryRetractionPreserveExactResult(Configuration configuration) {
    var random = new Random(71812);
    double large = configuration.singlePrecision ? 0x1p100 : 0x1p900;
    double minimum = configuration.minimum();
    var inputs =
        new ArrayList<>(
            List.of(
                large,
                1.0,
                -large,
                minimum,
                -minimum,
                configuration.normalize(0.1),
                configuration.normalize(0.2),
                configuration.normalize(-0.3)));
    for (int iteration = 0; iteration < 12; iteration++) {
      Collections.shuffle(inputs, random);
      var harness = configuration.harness();
      var active = new ArrayList<Double>();
      var handles = new ArrayList<ValueHandle>();
      assertExpected(configuration, harness, active);
      for (double input : inputs) {
        var handle = harness.newHandle.get();
        handle.add(input);
        handles.add(handle);
        active.add(input);
        assertExpected(configuration, harness, active);
      }
      // Both huge contributions cancel exactly, leaving the unit contribution intact.
      assertBits(configuration, harness.result.get(), configuration.average ? 0.125 : 1.0);
      while (!handles.isEmpty()) {
        int index = random.nextInt(handles.size());
        handles.remove(index).remove();
        active.remove(index);
        assertExpected(configuration, harness, active);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("collectors")
  void replacementRemovalAndHandleReuseKeepTheCorrectCount(Configuration configuration) {
    var harness = configuration.harness();
    var first = harness.newHandle.get();
    var second = harness.newHandle.get();
    double large = configuration.singlePrecision ? 0x1p100 : 0x1p900;
    first.add(large);
    second.add(1.0);
    first.replace(-large);
    assertExpected(configuration, harness, List.of(-large, 1.0));
    first.remove();
    assertBits(configuration, harness.result.get(), 1.0);
    second.replace(3.0);
    assertBits(configuration, harness.result.get(), 3.0);
    second.remove();
    assertExpected(configuration, harness, List.of());
    first.add(2.0);
    second.add(4.0);
    assertBits(configuration, harness.result.get(), configuration.average ? 3.0 : 6.0);
    first.replace(2.0);
    second.replace(-4.0);
    assertBits(configuration, harness.result.get(), configuration.average ? -1.0 : -2.0);
    first.remove();
    second.remove();
    assertExpected(configuration, harness, List.of());
  }

  @ParameterizedTest
  @MethodSource("collectors")
  void nonFiniteAddAndReplacementAreRejectedBeforeMutation(Configuration configuration) {
    var harness = configuration.harness();
    var first = harness.newHandle.get();
    var second = harness.newHandle.get();
    first.add(1.0);
    second.add(2.0);
    for (double invalid :
        new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      var failed = harness.newHandle.get();
      assertThatThrownBy(() -> failed.add(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("finite");
      assertExpected(configuration, harness, List.of(1.0, 2.0));
      failed.add(3.0); // A failed add did not activate the handle.
      assertExpected(configuration, harness, List.of(1.0, 2.0, 3.0));
      failed.remove();
      assertThatThrownBy(() -> second.replace(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("finite");
      assertExpected(configuration, harness, List.of(1.0, 2.0));
    }
    second.remove(); // A failed replacement still retracts the previous finite contribution.
    assertBits(configuration, harness.result.get(), 1.0);
    first.replace(-0.0);
    assertBits(configuration, harness.result.get(), 0.0);
    first.remove();
    assertExpected(configuration, harness, List.of());
  }

  @ParameterizedTest
  @MethodSource("collectors")
  void exactStateMayExceedOutputRangeAndRecoverByCancellation(Configuration configuration) {
    var harness = configuration.harness();
    double maximum = configuration.maximum();
    var first = harness.newHandle.get();
    var second = harness.newHandle.get();
    first.add(maximum);
    second.add(maximum); // Allowed: extraction is the floating-point rounding boundary.
    if (configuration.average) {
      assertBits(configuration, harness.result.get(), maximum);
    } else {
      assertThatThrownBy(harness.result::get)
          .isInstanceOf(ArithmeticException.class)
          .hasMessageContaining("finite");
    }
    var opposite = harness.newHandle.get();
    opposite.add(-maximum);
    assertExpected(configuration, harness, List.of(maximum, maximum, -maximum));
    second.remove();
    assertBits(configuration, harness.result.get(), 0.0);
    opposite.remove();
    assertBits(configuration, harness.result.get(), maximum);
    first.remove();
    assertExpected(configuration, harness, List.of());
  }

  @ParameterizedTest
  @MethodSource("collectors")
  void roundsHalfwayToEvenAtTheNativePrecision(Configuration configuration) {
    double oneUp = configuration.nextUp(1.0);
    double twoUp = configuration.nextUp(oneUp);
    double halfUlp = configuration.singlePrecision ? 0x1p-24 : 0x1p-53;
    if (configuration.average) {
      assertCollected(configuration, 1.0, 1.0, oneUp);
      assertCollected(configuration, twoUp, oneUp, twoUp);
      // The exact average is 1 + half an ulp. Rounding the sum first would give nextUp(1).
      assertCollected(configuration, 1.0, 1.0, 2.0, 3.0 * halfUlp);
      assertCollected(configuration, 0.0, configuration.minimum(), 0.0);
      assertCollected(
          configuration,
          2.0 * configuration.minimum(),
          configuration.minimum(),
          2.0 * configuration.minimum());
      assertCollected(
          configuration,
          configuration.minimum(),
          configuration.minimum(),
          configuration.minimum(),
          0.0);
      assertCollected(configuration, 0.0, configuration.minimum(), 0.0, 0.0);
      double normal = configuration.singlePrecision ? Float.MIN_NORMAL : Double.MIN_NORMAL;
      assertCollected(configuration, normal, normal, configuration.nextDown(normal));
    } else {
      assertCollected(configuration, 1.0, 1.0, halfUlp);
      assertCollected(configuration, twoUp, oneUp, halfUlp);
      assertCollected(
          configuration,
          2.0 * configuration.minimum(),
          configuration.minimum(),
          configuration.minimum());
    }
  }

  @Test
  void mapperIdentityPrecisionAndOperationDetermineCollectorEquality() {
    ToFloatFunction<Double> floatMapper = Double::floatValue;
    ToDoubleFunction<Double> doubleMapper = Double::doubleValue;
    assertThat(ConstraintCollectors.sumFloat(floatMapper))
        .isEqualTo(ConstraintCollectors.sumFloat(floatMapper));
    assertThat(ConstraintCollectors.sumFloat(floatMapper).hashCode())
        .isEqualTo(ConstraintCollectors.sumFloat(floatMapper).hashCode());
    assertThat(ConstraintCollectors.sumFloat(floatMapper))
        .isNotEqualTo(ConstraintCollectors.averageFloat(floatMapper));
    assertThat(ConstraintCollectors.averageFloat(floatMapper))
        .isEqualTo(ConstraintCollectors.averageFloat(floatMapper));
    assertThat(ConstraintCollectors.sumDouble(doubleMapper))
        .isEqualTo(ConstraintCollectors.sumDouble(doubleMapper));
    assertThat(ConstraintCollectors.sumDouble(doubleMapper))
        .isNotEqualTo(ConstraintCollectors.averageDouble(doubleMapper));
    assertThat(ConstraintCollectors.averageDouble(doubleMapper))
        .isEqualTo(ConstraintCollectors.averageDouble(doubleMapper));
    assertThat(ConstraintCollectors.sumFloat(floatMapper))
        .isNotEqualTo(ConstraintCollectors.sumDouble(doubleMapper));
  }

  @Test
  void mappedContributionIsCapturedBeforeTheTupleMutates() {
    assertMutableTuple(ConstraintCollectors.sumFloat((MutableValue value) -> (float) value.value));
    assertMutableTuple(ConstraintCollectors.sumDouble((MutableValue value) -> value.value));
    assertMutableTuple(
        ConstraintCollectors.averageFloat((MutableValue value) -> (float) value.value));
    assertMutableTuple(ConstraintCollectors.averageDouble((MutableValue value) -> value.value));
  }

  private static <State_, Result_ extends Number> void assertMutableTuple(
      UniConstraintCollector<MutableValue, State_, Result_> collector) {
    var state = collector.supplier().get();
    var value = new MutableValue(0x1p100);
    var moving = collector.accumulator().intoGroup(state);
    var stable = collector.accumulator().intoGroup(state);
    moving.add(value);
    stable.add(new MutableValue(1.0));
    value.value = -0x1p100;
    moving.remove();
    assertThat(collector.finisher().apply(state).doubleValue()).isEqualTo(1.0);
    moving.add(value);
    value.value = 3.0;
    moving.replaceWith(value);
    value.value = 9.0;
    moving.remove();
    assertThat(collector.finisher().apply(state).doubleValue()).isEqualTo(1.0);
  }

  private static void assertCollected(
      Configuration configuration, double expected, double... values) {
    var harness = configuration.harness();
    for (double value : values) harness.newHandle.get().add(value);
    assertBits(configuration, harness.result.get(), expected);
  }

  private static void assertExpected(
      Configuration configuration, Harness harness, List<Double> active) {
    if (active.isEmpty()) {
      if (configuration.average) assertThat(harness.result.get()).isNull();
      else assertBits(configuration, harness.result.get(), 0.0);
      return;
    }
    // Independent decimal oracle: construct exact represented values, not decimal short strings.
    var expected =
        active.stream()
            .map(value -> new BigDecimal(configuration.normalize(value)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (configuration.average)
      expected =
          expected.divide(
              BigDecimal.valueOf(active.size()), new MathContext(2000, RoundingMode.HALF_EVEN));
    double rounded = configuration.singlePrecision ? expected.floatValue() : expected.doubleValue();
    if (rounded == 0.0) rounded = 0.0; // The collector deliberately canonicalizes negative zero.
    if (!Double.isFinite(rounded)) {
      assertThatThrownBy(harness.result::get).isInstanceOf(ArithmeticException.class);
    } else {
      assertBits(configuration, harness.result.get(), rounded);
    }
  }

  private static void assertBits(Configuration configuration, Number actual, double expected) {
    assertThat(actual).isNotNull();
    if (configuration.singlePrecision) {
      assertThat(actual).isInstanceOf(Float.class);
      assertThat(Float.floatToRawIntBits(actual.floatValue()))
          .isEqualTo(Float.floatToRawIntBits((float) expected));
    } else {
      assertThat(actual).isInstanceOf(Double.class);
      assertThat(Double.doubleToRawLongBits(actual.doubleValue()))
          .isEqualTo(Double.doubleToRawLongBits(expected));
    }
  }

  private static final class MutableValue {
    private double value;

    private MutableValue(double value) {
      this.value = value;
    }
  }

  private interface ValueHandle {
    void add(double value);

    void replace(double value);

    void remove();
  }

  private record Harness(Supplier<ValueHandle> newHandle, Supplier<? extends Number> result) {}

  private record Configuration(int arity, boolean singlePrecision, boolean average) {
    private double normalize(double value) {
      return singlePrecision ? (float) value : value;
    }

    private double minimum() {
      return singlePrecision ? Float.MIN_VALUE : Double.MIN_VALUE;
    }

    private double maximum() {
      return singlePrecision ? Float.MAX_VALUE : Double.MAX_VALUE;
    }

    private double nextUp(double value) {
      return singlePrecision ? Math.nextUp((float) value) : Math.nextUp(value);
    }

    private double nextDown(double value) {
      return singlePrecision ? Math.nextDown((float) value) : Math.nextDown(value);
    }

    private Harness harness() {
      if (singlePrecision) {
        return switch (arity) {
          case 1 ->
              average
                  ? uni(ConstraintCollectors.<Double>averageFloat((a) -> a.floatValue()))
                  : uni(ConstraintCollectors.<Double>sumFloat((a) -> a.floatValue()));
          case 2 ->
              average
                  ? bi(ConstraintCollectors.<Object, Double>averageFloat((a, b) -> b.floatValue()))
                  : bi(ConstraintCollectors.<Object, Double>sumFloat((a, b) -> b.floatValue()));
          case 3 ->
              average
                  ? tri(
                      ConstraintCollectors.<Object, Object, Double>averageFloat(
                          (a, b, c) -> c.floatValue()))
                  : tri(
                      ConstraintCollectors.<Object, Object, Double>sumFloat(
                          (a, b, c) -> c.floatValue()));
          case 4 ->
              average
                  ? quad(
                      ConstraintCollectors.<Object, Object, Object, Double>averageFloat(
                          (a, b, c, d) -> d.floatValue()))
                  : quad(
                      ConstraintCollectors.<Object, Object, Object, Double>sumFloat(
                          (a, b, c, d) -> d.floatValue()));
          default -> throw new IllegalArgumentException("Unsupported arity: " + arity);
        };
      } else {
        return switch (arity) {
          case 1 ->
              average
                  ? uni(ConstraintCollectors.<Double>averageDouble((a) -> a.doubleValue()))
                  : uni(ConstraintCollectors.<Double>sumDouble((a) -> a.doubleValue()));
          case 2 ->
              average
                  ? bi(
                      ConstraintCollectors.<Object, Double>averageDouble((a, b) -> b.doubleValue()))
                  : bi(ConstraintCollectors.<Object, Double>sumDouble((a, b) -> b.doubleValue()));
          case 3 ->
              average
                  ? tri(
                      ConstraintCollectors.<Object, Object, Double>averageDouble(
                          (a, b, c) -> c.doubleValue()))
                  : tri(
                      ConstraintCollectors.<Object, Object, Double>sumDouble(
                          (a, b, c) -> c.doubleValue()));
          case 4 ->
              average
                  ? quad(
                      ConstraintCollectors.<Object, Object, Object, Double>averageDouble(
                          (a, b, c, d) -> d.doubleValue()))
                  : quad(
                      ConstraintCollectors.<Object, Object, Object, Double>sumDouble(
                          (a, b, c, d) -> d.doubleValue()));
          default -> throw new IllegalArgumentException("Unsupported arity: " + arity);
        };
      }
    }
  }

  private static <State_, Result_ extends Number> Harness uni(
      UniConstraintCollector<Double, State_, Result_> collector) {
    var state = collector.supplier().get();
    return new Harness(
        () -> {
          var delegate = collector.accumulator().intoGroup(state);
          return new ValueHandle() {
            @Override
            public void add(double value) {
              delegate.add(value);
            }

            @Override
            public void replace(double value) {
              delegate.replaceWith(value);
            }

            @Override
            public void remove() {
              delegate.remove();
            }
          };
        },
        () -> collector.finisher().apply(state));
  }

  private static <State_, Result_ extends Number> Harness bi(
      BiConstraintCollector<Object, Double, State_, Result_> collector) {
    var state = collector.supplier().get();
    return new Harness(
        () -> {
          var delegate = collector.accumulator().intoGroup(state);
          return new ValueHandle() {
            @Override
            public void add(double value) {
              delegate.add("unused", value);
            }

            @Override
            public void replace(double value) {
              delegate.replaceWith("unused", value);
            }

            @Override
            public void remove() {
              delegate.remove();
            }
          };
        },
        () -> collector.finisher().apply(state));
  }

  private static <State_, Result_ extends Number> Harness tri(
      TriConstraintCollector<Object, Object, Double, State_, Result_> collector) {
    var state = collector.supplier().get();
    return new Harness(
        () -> {
          var delegate = collector.accumulator().intoGroup(state);
          return new ValueHandle() {
            @Override
            public void add(double value) {
              delegate.add("unused", "unused", value);
            }

            @Override
            public void replace(double value) {
              delegate.replaceWith("unused", "unused", value);
            }

            @Override
            public void remove() {
              delegate.remove();
            }
          };
        },
        () -> collector.finisher().apply(state));
  }

  private static <State_, Result_ extends Number> Harness quad(
      QuadConstraintCollector<Object, Object, Object, Double, State_, Result_> collector) {
    var state = collector.supplier().get();
    return new Harness(
        () -> {
          var delegate = collector.accumulator().intoGroup(state);
          return new ValueHandle() {
            @Override
            public void add(double value) {
              delegate.add("unused", "unused", "unused", value);
            }

            @Override
            public void replace(double value) {
              delegate.replaceWith("unused", "unused", "unused", value);
            }

            @Override
            public void remove() {
              delegate.remove();
            }
          };
        },
        () -> collector.finisher().apply(state));
  }
}
