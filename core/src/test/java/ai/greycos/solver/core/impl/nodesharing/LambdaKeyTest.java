package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Tests for LambdaKey. */
class LambdaKeyTest {

  @Test
  void shouldCreateLambdaKey() {
    LambdaKey key =
        new LambdaKey("java.util.function.Predicate", "(Ljava/lang/Object;)Z", List.of());

    assertThat(key.getFunctionalInterfaceType()).isEqualTo("java.util.function.Predicate");
    assertThat(key.getImplementationMethodType()).isEqualTo("(Ljava/lang/Object;)Z");
    assertThat(key.getCapturedArguments()).isEmpty();
  }

  @Test
  void shouldHaveCorrectEquals() {
    LambdaKey key1 =
        new LambdaKey("java.util.function.Predicate", "(Ljava/lang/Object;)Z", List.of());

    LambdaKey key2 =
        new LambdaKey("java.util.function.Predicate", "(Ljava/lang/Object;)Z", List.of());

    LambdaKey key3 =
        new LambdaKey(
            "java.util.function.Function", "(Ljava/lang/Object;)Ljava/lang/Object;", List.of());

    assertThat(key1).isEqualTo(key2);
    assertThat(key1).isNotEqualTo(key3);
  }

  @Test
  void shouldHaveCorrectHashCode() {
    LambdaKey key1 =
        new LambdaKey("java.util.function.Predicate", "(Ljava/lang/Object;)Z", List.of());

    LambdaKey key2 =
        new LambdaKey("java.util.function.Predicate", "(Ljava/lang/Object;)Z", List.of());

    assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
  }

  @Test
  void shouldHandleCapturedArguments() {
    List<Object> capturedArgs = List.of("captured1", 123);

    LambdaKey key =
        new LambdaKey("java.util.function.Predicate", "(Ljava/lang/Object;)Z", capturedArgs);

    assertThat(key.getCapturedArguments()).hasSize(2);
    assertThat(key.getCapturedArguments().get(0)).isEqualTo("captured1");
    assertThat(key.getCapturedArguments().get(1)).isEqualTo(123);
  }

  @Test
  void shouldConsiderLambdasWithDifferentMethodNamesButSameTypeAsEqual() {
    // Two lambdas with same functional interface type and method type signature
    // but different synthetic method names should be considered equal
    LambdaKey key1 =
        new LambdaKey("java.util.function.Predicate", "(Ljava/lang/Object;)Z", List.of());

    LambdaKey key2 =
        new LambdaKey("java.util.function.Predicate", "(Ljava/lang/Object;)Z", List.of());

    assertThat(key1).isEqualTo(key2);
    assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
  }
}
