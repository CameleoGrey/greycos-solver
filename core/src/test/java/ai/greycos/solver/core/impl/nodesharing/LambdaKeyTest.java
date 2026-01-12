package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class LambdaKeyTest {

  @Test
  void equalsReflexive() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(key).isEqualTo(key);
  }

  @Test
  void equalsSymmetric() {
    LambdaKey key1 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaKey key2 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(key1).isEqualTo(key2);
    assertThat(key2).isEqualTo(key1);
  }

  @Test
  void equalsTransitive() {
    LambdaKey key1 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaKey key2 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaKey key3 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(key1).isEqualTo(key2);
    assertThat(key2).isEqualTo(key3);
    assertThat(key1).isEqualTo(key3);
  }

  @Test
  void equalsConsistent() {
    LambdaKey key1 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaKey key2 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(key1).isEqualTo(key2);
    assertThat(key1).isEqualTo(key2);
    assertThat(key1).isEqualTo(key2);
  }

  @Test
  void equalsNonNull() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(key).isNotEqualTo(null);
  }

  @Test
  void equalsDifferentClass() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(key).isNotEqualTo("string");
  }

  @Test
  void equalsDifferentFunctionalInterface() {
    LambdaKey key1 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaKey key2 =
        new LambdaKey(
            "java.util.function.Function",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(key1).isNotEqualTo(key2);
  }

  @Test
  void equalsDifferentImplementationMethod() {
    LambdaKey key1 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaKey key2 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$2",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(key1).isNotEqualTo(key2);
  }

  @Test
  void equalsDifferentImplementationMethodType() {
    LambdaKey key1 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaKey key2 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/String;)Z",
            List.of());

    assertThat(key1).isNotEqualTo(key2);
  }

  @Test
  void equalsDifferentCapturedArguments() {
    LambdaKey key1 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of("arg1"));

    LambdaKey key2 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of("arg2"));

    assertThat(key1).isNotEqualTo(key2);
  }

  @Test
  void equalsDifferentCapturedArgumentsSize() {
    LambdaKey key1 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of("arg1"));

    LambdaKey key2 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of("arg1", "arg2"));

    assertThat(key1).isNotEqualTo(key2);
  }

  @Test
  void hashCodeConsistent() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    int hash1 = key.hashCode();
    int hash2 = key.hashCode();
    int hash3 = key.hashCode();

    assertThat(hash1).isEqualTo(hash2).isEqualTo(hash3);
  }

  @Test
  void hashCodeEqualObjects() {
    LambdaKey key1 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaKey key2 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
  }

  @Test
  void constructorNullFunctionalInterface() {
    assertThatThrownBy(
            () ->
                new LambdaKey(
                    null, "com/example/Class.lambda$1", "(Ljava/lang/Object;)Z", List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorNullImplementationMethod() {
    assertThatThrownBy(
            () ->
                new LambdaKey(
                    "java.util.function.Predicate", null, "(Ljava/lang/Object;)Z", List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorNullImplementationMethodType() {
    assertThatThrownBy(
            () ->
                new LambdaKey(
                    "java.util.function.Predicate", "com/example/Class.lambda$1", null, List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorNullCapturedArguments() {
    assertThatThrownBy(
            () ->
                new LambdaKey(
                    "java.util.function.Predicate",
                    "com/example/Class.lambda$1",
                    "(Ljava/lang/Object;)Z",
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void getters() {
    List<Object> capturedArgs = List.of("arg1", 42);
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            capturedArgs);

    assertThat(key.getFunctionalInterfaceType()).isEqualTo("java.util.function.Predicate");
    assertThat(key.getImplementationMethod()).isEqualTo("com/example/Class.lambda$1");
    assertThat(key.getImplementationMethodType()).isEqualTo("(Ljava/lang/Object;)Z");
    assertThat(key.getCapturedArguments()).isEqualTo(capturedArgs);
  }
}
