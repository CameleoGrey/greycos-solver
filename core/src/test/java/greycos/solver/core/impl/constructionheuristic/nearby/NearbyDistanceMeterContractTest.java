package greycos.solver.core.impl.constructionheuristic.nearby;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NearbyDistanceMeterContractTest {

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void unresolvedTypeVariablesRetainTheirDeclaredUpperBounds() {
    var contract = NearbyDistanceMeterContract.of((Class) BoundedMeter.class);
    assertThat(contract.originType()).isSameAs(Number.class);
    assertThat(contract.destinationType()).isSameAs(CharSequence.class);
    assertThat(contract.accepts(1, "destination")).isTrue();
    assertThat(contract.accepts("unsupported", "destination")).isFalse();
    assertThat(contract.accepts(1, 2)).isFalse();
  }

  public static class BoundedMeter<O extends Number, D extends CharSequence>
      implements NearbyDistanceMeter<O, D> {
    @Override
    public double getNearbyDistance(O origin, D destination) {
      return 0;
    }

    public double getNearbyDistance(String origin, String destination) {
      return 0;
    }
  }

  @Test
  void unrelatedOverloadsDoNotBroadenGenericInterfaceContract() {
    var contract = NearbyDistanceMeterContract.of(OverloadedMeter.class);

    assertThat(contract.originType()).isSameAs(String.class);
    assertThat(contract.destinationType()).isSameAs(Integer.class);
    assertThat(contract.accepts("origin", 1)).isTrue();
    assertThat(contract.accepts("origin", "unsupported destination")).isFalse();
    assertThat(contract.accepts(1, 1)).isFalse();
    assertThat(NearbyDistanceMeterContract.of(OverloadedMeter.class)).isSameAs(contract);
  }

  @Test
  void inheritedGenericSuperclassAndIntermediateInterfaceResolveTypeBindings() {
    var contract = NearbyDistanceMeterContract.of(InheritedMeter.class);

    assertThat(contract.originType()).isSameAs(String.class);
    assertThat(contract.destinationType()).isSameAs(Integer.class);
    assertThat(contract.accepts("origin", 1)).isTrue();
    assertThat(contract.accepts("origin", "unsupported destination")).isFalse();
  }

  @Test
  void parameterizedArgumentUsesItsRuntimeRawClass() {
    var contract = NearbyDistanceMeterContract.of(ParameterizedMeter.class);

    assertThat(contract.originType()).isSameAs(String.class);
    assertThat(contract.destinationType()).isSameAs(java.util.List.class);
    assertThat(contract.accepts("origin", java.util.List.of(1))).isTrue();
    assertThat(contract.accepts("origin", 1)).isFalse();
  }

  @Test
  void genericArrayArgumentResolvesSuperclassBinding() {
    var contract = NearbyDistanceMeterContract.of(ArrayMeter.class);

    assertThat(contract.originType()).isSameAs(String.class);
    assertThat(contract.destinationType()).isSameAs(String[].class);
    assertThat(contract.accepts("origin", new String[0])).isTrue();
    assertThat(contract.accepts("origin", new Integer[0])).isFalse();
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void rawImplementationKeepsErasedTypesEvenWithUnrelatedTypedOverload() {
    var contract = NearbyDistanceMeterContract.of((Class) RawMeter.class);

    assertThat(contract.originType()).isSameAs(Object.class);
    assertThat(contract.destinationType()).isSameAs(Object.class);
    assertThat(contract.accepts("origin", 1)).isTrue();
    assertThat(contract.accepts(1, "destination")).isTrue();
  }

  @Test
  void broadDeclaredVariableTypeCanContainSupportedConcreteCandidates() {
    var contract = NearbyDistanceMeterContract.of(OverloadedMeter.class);

    assertThat(contract.canApplyTo(Object.class, Number.class)).isTrue();
    assertThat(contract.canApplyTo(CharSequence.class, Integer.class)).isTrue();
    assertThat(contract.canApplyTo(Integer.class, Integer.class)).isFalse();
    assertThat(contract.canApplyTo(String.class, String.class)).isFalse();
    assertThat(contract.accepts("origin", 1L)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("runtimeIntersections")
  void runtimeIntersectionRespectsOpenFinalSealedAndArrayTypes(
      Class<?> first, Class<?> second, boolean expected) {
    assertThat(NearbyDistanceMeterContract.mayShareRuntimeType(first, second)).isEqualTo(expected);
    assertThat(NearbyDistanceMeterContract.mayShareRuntimeType(second, first)).isEqualTo(expected);
  }

  static Stream<Arguments> runtimeIntersections() {
    return Stream.of(
        Arguments.of(OpenBase.class, Object.class, true),
        Arguments.of(OpenBase.class, Marker.class, true),
        Arguments.of(FinalBase.class, Marker.class, false),
        Arguments.of(MarkedFinal.class, Marker.class, true),
        Arguments.of(OpenBase.class, OtherOpenBase.class, false),
        Arguments.of(Marker.class, OtherMarker.class, true),
        Arguments.of(ClosedUnmarkedBase.class, Marker.class, false),
        Arguments.of(ClosedMarkedBase.class, Marker.class, true),
        Arguments.of(ClosedNestedBase.class, Marker.class, true),
        Arguments.of(ClosedOpenBase.class, Marker.class, true),
        Arguments.of(ClosedMarker.class, OpenBase.class, false),
        Arguments.of(ClosedMarker.class, Marker.class, false),
        Arguments.of(OpenPermittedMarker.class, OtherMarker.class, true),
        Arguments.of(OpenBase[].class, Marker[].class, true),
        Arguments.of(FinalBase[].class, Marker[].class, false),
        Arguments.of(String[].class, Integer[].class, false),
        Arguments.of(int[].class, long[].class, false),
        Arguments.of(Object[].class, int[].class, false),
        Arguments.of(Object[].class, int[][].class, true),
        Arguments.of(int[].class, Object.class, true),
        Arguments.of(int[].class, Cloneable.class, true),
        Arguments.of(int[].class, java.io.Serializable.class, true),
        Arguments.of(int[].class, Marker.class, false),
        Arguments.of(int.class, int.class, true),
        Arguments.of(int.class, long.class, false));
  }

  interface Marker {}

  interface OtherMarker {}

  static class OpenBase {}

  static class OtherOpenBase {}

  static final class FinalBase {}

  static final class MarkedFinal implements Marker {}

  abstract static sealed class ClosedUnmarkedBase permits UnmarkedLeaf {}

  static final class UnmarkedLeaf extends ClosedUnmarkedBase {}

  abstract static sealed class ClosedMarkedBase permits MarkedLeaf {}

  static final class MarkedLeaf extends ClosedMarkedBase implements Marker {}

  abstract static sealed class ClosedNestedBase permits NestedBranch {}

  abstract static sealed class NestedBranch extends ClosedNestedBase permits NestedMarkedLeaf {}

  static final class NestedMarkedLeaf extends NestedBranch implements Marker {}

  abstract static sealed class ClosedOpenBase permits OpenBranch {}

  static non-sealed class OpenBranch extends ClosedOpenBase {}

  sealed interface ClosedMarker permits ClosedMarkerLeaf {}

  static final class ClosedMarkerLeaf implements ClosedMarker {}

  sealed interface OpenPermittedMarker permits OpenMarkerBranch {}

  non-sealed interface OpenMarkerBranch extends OpenPermittedMarker {}

  public static class OverloadedMeter implements NearbyDistanceMeter<String, Integer> {
    @Override
    public double getNearbyDistance(String origin, Integer destination) {
      throw new AssertionError("Contract discovery must not invoke the meter.");
    }

    public double getNearbyDistance(String origin, String destination) {
      throw new AssertionError("Unrelated overload.");
    }
  }

  public interface IntermediateMeter<O, D> extends NearbyDistanceMeter<O, D> {}

  public abstract static class GenericMeter<O, D> implements IntermediateMeter<O, D> {
    @Override
    public double getNearbyDistance(O origin, D destination) {
      throw new AssertionError("Contract discovery must not invoke the meter.");
    }
  }

  public abstract static class PartiallyBoundMeter<D> extends GenericMeter<String, D> {}

  public static class InheritedMeter extends PartiallyBoundMeter<Integer> {}

  public static class ParameterizedMeter extends PartiallyBoundMeter<java.util.List<Integer>> {}

  public abstract static class GenericArrayMeter<T> implements NearbyDistanceMeter<T, T[]> {
    @Override
    public double getNearbyDistance(T origin, T[] destination) {
      throw new AssertionError("Contract discovery must not invoke the meter.");
    }
  }

  public static class ArrayMeter extends GenericArrayMeter<String> {}

  @SuppressWarnings("rawtypes")
  public static class RawMeter implements NearbyDistanceMeter {
    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      throw new AssertionError("Contract discovery must not invoke the meter.");
    }

    public double getNearbyDistance(String origin, Integer destination) {
      throw new AssertionError("Unrelated overload.");
    }
  }
}
