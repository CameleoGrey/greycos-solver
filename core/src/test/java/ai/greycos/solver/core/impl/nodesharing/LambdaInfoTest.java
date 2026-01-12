package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class LambdaInfoTest {

  @Test
  void getKeyReturnsMatchingLambdaKey() {
    List<Object> capturedArgs = List.of("captured");
    LambdaInfo info =
        new LambdaInfo(
            "defineConstraints",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            capturedArgs);

    LambdaKey key = info.getKey();

    assertThat(key.getFunctionalInterfaceType()).isEqualTo("java.util.function.Predicate");
    assertThat(key.getImplementationMethod()).isEqualTo("com/example/Class.lambda$1");
    assertThat(key.getImplementationMethodType()).isEqualTo("(Ljava/lang/Object;)Z");
    assertThat(key.getCapturedArguments()).isEqualTo(capturedArgs);
  }

  @Test
  void getters() {
    List<Object> capturedArgs = List.of("arg1", 42);
    LambdaInfo info =
        new LambdaInfo(
            "defineConstraints",
            5,
            "java.util.function.Function",
            "com/example/Class.lambda$2",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            capturedArgs);

    assertThat(info.getMethodName()).isEqualTo("defineConstraints");
    assertThat(info.getInstructionOffset()).isEqualTo(5);
    assertThat(info.getFunctionalInterfaceType()).isEqualTo("java.util.function.Function");
    assertThat(info.getImplementationMethod()).isEqualTo("com/example/Class.lambda$2");
    assertThat(info.getImplementationMethodType())
        .isEqualTo("(Ljava/lang/Object;)Ljava/lang/Object;");
    assertThat(info.getCapturedArguments()).isEqualTo(capturedArgs);
  }

  @Test
  void equalsReflexive() {
    LambdaInfo info =
        new LambdaInfo(
            "defineConstraints",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(info).isEqualTo(info);
  }

  @Test
  void equalsSameValues() {
    LambdaInfo info1 =
        new LambdaInfo(
            "defineConstraints",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info2 =
        new LambdaInfo(
            "defineConstraints",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(info1).isEqualTo(info2);
  }

  @Test
  void equalsDifferentMethodName() {
    LambdaInfo info1 =
        new LambdaInfo(
            "method1",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info2 =
        new LambdaInfo(
            "method2",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(info1).isNotEqualTo(info2);
  }

  @Test
  void equalsDifferentOffset() {
    LambdaInfo info1 =
        new LambdaInfo(
            "defineConstraints",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info2 =
        new LambdaInfo(
            "defineConstraints",
            5,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(info1).isNotEqualTo(info2);
  }

  @Test
  void equalsDifferentFunctionalInterface() {
    LambdaInfo info1 =
        new LambdaInfo(
            "defineConstraints",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info2 =
        new LambdaInfo(
            "defineConstraints",
            0,
            "java.util.function.Function",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(info1).isNotEqualTo(info2);
  }

  @Test
  void hashCodeConsistent() {
    LambdaInfo info =
        new LambdaInfo(
            "defineConstraints",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    int hash1 = info.hashCode();
    int hash2 = info.hashCode();

    assertThat(hash1).isEqualTo(hash2);
  }

  @Test
  void hashCodeEqualObjects() {
    LambdaInfo info1 =
        new LambdaInfo(
            "defineConstraints",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info2 =
        new LambdaInfo(
            "defineConstraints",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
  }

  @Test
  void constructorNullMethodName() {
    assertThatThrownBy(
            () ->
                new LambdaInfo(
                    null,
                    0,
                    "java.util.function.Predicate",
                    "com/example/Class.lambda$1",
                    "(Ljava/lang/Object;)Z",
                    List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorNullFunctionalInterface() {
    assertThatThrownBy(
            () ->
                new LambdaInfo(
                    "defineConstraints",
                    0,
                    null,
                    "com/example/Class.lambda$1",
                    "(Ljava/lang/Object;)Z",
                    List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorNullImplementationMethod() {
    assertThatThrownBy(
            () ->
                new LambdaInfo(
                    "defineConstraints",
                    0,
                    "java.util.function.Predicate",
                    null,
                    "(Ljava/lang/Object;)Z",
                    List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorNullImplementationMethodType() {
    assertThatThrownBy(
            () ->
                new LambdaInfo(
                    "defineConstraints",
                    0,
                    "java.util.function.Predicate",
                    "com/example/Class.lambda$1",
                    null,
                    List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorNullCapturedArguments() {
    assertThatThrownBy(
            () ->
                new LambdaInfo(
                    "defineConstraints",
                    0,
                    "java.util.function.Predicate",
                    "com/example/Class.lambda$1",
                    "(Ljava/lang/Object;)Z",
                    null))
        .isInstanceOf(NullPointerException.class);
  }
}
