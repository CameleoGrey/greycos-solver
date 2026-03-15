package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.uni.UniConstraintBuilder;
import ai.greycos.solver.core.api.score.stream.uni.UniConstraintStream;

import org.junit.jupiter.api.Test;

class NodeSharingIntegrationTest {

  @Test
  void endToEndTransformation() throws ReflectiveOperationException {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    // Transform the class
    Class<? extends ConstraintProvider> transformedClass =
        sharer.buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);

    // Verify the class is different from the original
    assertThat(transformedClass).isNotSameAs(SimpleConstraintProvider.class);

    // Verify the transformed class can be instantiated
    ConstraintProvider transformedInstance =
        transformedClass.getDeclaredConstructor().newInstance();

    // Verify the instance works
    assertThat(transformedInstance).isNotNull();
    assertThat(transformedInstance).isInstanceOf(ConstraintProvider.class);
  }

  @Test
  void transformedClassCanBeInstantiated() throws ReflectiveOperationException {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> transformedClass =
        sharer.buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);

    // Verify the transformed class can be instantiated
    ConstraintProvider instance = transformedClass.getDeclaredConstructor().newInstance();

    assertThat(instance).isNotNull();
    assertThat(instance).isInstanceOf(ConstraintProvider.class);
  }

  @Test
  void complexProviderTransformation() throws ReflectiveOperationException {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> transformedClass =
        sharer.buildNodeSharedConstraintProvider(ComplexConstraintProvider.class);

    ConstraintProvider transformedInstance =
        transformedClass.getDeclaredConstructor().newInstance();

    assertThat(transformedInstance).isNotNull();
  }

  @Test
  void noLambdaProviderReturnsOriginalBytecode() throws ReflectiveOperationException {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> transformedClass =
        sharer.buildNodeSharedConstraintProvider(NoLambdaConstraintProvider.class);

    // Even though no transformation occurred, a class should still be returned
    assertThat(transformedClass).isNotNull();

    ConstraintProvider transformedInstance =
        transformedClass.getDeclaredConstructor().newInstance();

    assertThat(transformedInstance).isNotNull();
  }

  @Test
  void capturedArgumentProviderTransformation() throws ReflectiveOperationException {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> transformedClass =
        sharer.buildNodeSharedConstraintProvider(CapturedArgumentProvider.class);

    ConstraintProvider transformedInstance =
        transformedClass.getDeclaredConstructor().newInstance();

    assertThat(transformedInstance).isNotNull();
  }

  @Test
  void transformationPreservesBehavior() throws ReflectiveOperationException {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> transformedClass =
        sharer.buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);

    ConstraintProvider transformedInstance =
        transformedClass.getDeclaredConstructor().newInstance();

    // Verify the transformed instance is a valid ConstraintProvider
    // (Full behavioral testing is done by ConstraintStreamNodeSharingTest)
    assertThat(transformedInstance).isInstanceOf(ConstraintProvider.class);
  }

  @Test
  void multipleTransformationsSameClass() {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> result1 =
        sharer.buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);

    Class<? extends ConstraintProvider> result2 =
        sharer.buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);

    // Each call returns the same transformed class (cached)
    assertThat(result1).isSameAs(result2);
  }

  @Test
  void differentProvidersDifferentTransformations() {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> result1 =
        sharer.buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);

    Class<? extends ConstraintProvider> result2 =
        sharer.buildNodeSharedConstraintProvider(ComplexConstraintProvider.class);

    // Different providers should result in different transformed classes
    assertThat(result1).isNotSameAs(result2);
  }

  @Test
  void transformedProviderReusesEquivalentStatelessPredicates()
      throws ReflectiveOperationException {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();
    ConstraintProvider transformedInstance =
        sharer
            .buildNodeSharedConstraintProvider(SimpleConstraintProvider.class)
            .getDeclaredConstructor()
            .newInstance();
    RecordingConstraintFactory recorder = new RecordingConstraintFactory();

    transformedInstance.defineConstraints(recorder.constraintFactory());

    assertThat(recorder.recordedPredicates).hasSize(2);
    assertThat(recorder.recordedPredicates.get(0)).isSameAs(recorder.recordedPredicates.get(1));
    assertThat(recorder.recordedPredicates.get(0).test("a")).isTrue();
    assertThat(recorder.recordedPredicates.get(0).test("")).isFalse();
  }

  @Test
  void capturedPredicatesRemainUnsharedAndPreserveInstanceState()
      throws ReflectiveOperationException {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();
    Class<? extends ConstraintProvider> transformedClass =
        sharer.buildNodeSharedConstraintProvider(CapturedArgumentProvider.class);
    ConstraintProvider minLengthFive =
        transformedClass.getDeclaredConstructor(int.class).newInstance(5);
    ConstraintProvider minLengthTen =
        transformedClass.getDeclaredConstructor(int.class).newInstance(10);
    RecordingConstraintFactory recorderFive = new RecordingConstraintFactory();
    RecordingConstraintFactory recorderTen = new RecordingConstraintFactory();

    minLengthFive.defineConstraints(recorderFive.constraintFactory());
    minLengthTen.defineConstraints(recorderTen.constraintFactory());

    assertThat(recorderFive.recordedPredicates).hasSize(3);
    assertThat(recorderFive.recordedPredicates.get(0))
        .isNotSameAs(recorderFive.recordedPredicates.get(1));
    assertThat(recorderFive.recordedPredicates.get(0).test("12345")).isFalse();
    assertThat(recorderFive.recordedPredicates.get(0).test("123456")).isTrue();
    assertThat(recorderTen.recordedPredicates.get(0).test("1234567890")).isFalse();
    assertThat(recorderTen.recordedPredicates.get(0).test("12345678901")).isTrue();
  }

  @Test
  void providerWithExistingStaticInitializerStillInitializesSharedLambdas()
      throws ReflectiveOperationException {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();
    Class<? extends ConstraintProvider> transformedClass =
        sharer.buildNodeSharedConstraintProvider(StaticInitializerConstraintProvider.class);
    ConstraintProvider transformedInstance =
        transformedClass.getDeclaredConstructor().newInstance();
    RecordingConstraintFactory recorder = new RecordingConstraintFactory();

    transformedInstance.defineConstraints(recorder.constraintFactory());

    assertThat(transformedClass.getField("INIT_MARKER").get(null)).isEqualTo("static-init");
    assertThat(recorder.recordedPredicates).hasSize(2);
    assertThat(recorder.recordedPredicates.get(0)).isSameAs(recorder.recordedPredicates.get(1));
    assertThat(recorder.recordedPredicates.get(0).test("A1")).isTrue();
    assertThat(recorder.recordedPredicates.get(0).test("B1")).isFalse();
  }

  private static final class RecordingConstraintFactory {

    private final List<Predicate<String>> recordedPredicates = new ArrayList<>();
    private final Constraint constraint =
        (Constraint)
            Proxy.newProxyInstance(
                Constraint.class.getClassLoader(),
                new Class<?>[] {Constraint.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    private final UniConstraintBuilder<String, ?> builder =
        (UniConstraintBuilder<String, ?>)
            Proxy.newProxyInstance(
                UniConstraintBuilder.class.getClassLoader(),
                new Class<?>[] {UniConstraintBuilder.class},
                (proxy, method, args) -> {
                  if ("asConstraint".equals(method.getName())) {
                    return constraint;
                  }
                  return defaultValue(method.getReturnType());
                });
    private final UniConstraintStream<String> stream =
        (UniConstraintStream<String>)
            Proxy.newProxyInstance(
                UniConstraintStream.class.getClassLoader(),
                new Class<?>[] {UniConstraintStream.class},
                (proxy, method, args) -> {
                  if ("filter".equals(method.getName())) {
                    @SuppressWarnings("unchecked")
                    Predicate<String> predicate = (Predicate<String>) args[0];
                    recordedPredicates.add(predicate);
                    return proxy;
                  }
                  if ("penalize".equals(method.getName())) {
                    return builder;
                  }
                  return defaultValue(method.getReturnType());
                });
    private final ConstraintFactory constraintFactory =
        (ConstraintFactory)
            Proxy.newProxyInstance(
                ConstraintFactory.class.getClassLoader(),
                new Class<?>[] {ConstraintFactory.class},
                (proxy, method, args) -> {
                  if ("forEach".equals(method.getName())) {
                    return stream;
                  }
                  return defaultValue(method.getReturnType());
                });

    private ConstraintFactory constraintFactory() {
      return constraintFactory;
    }

    private static Object defaultValue(Class<?> returnType) {
      if (!returnType.isPrimitive()) {
        return null;
      }
      if (returnType == boolean.class) {
        return false;
      }
      if (returnType == byte.class) {
        return (byte) 0;
      }
      if (returnType == short.class) {
        return (short) 0;
      }
      if (returnType == int.class) {
        return 0;
      }
      if (returnType == long.class) {
        return 0L;
      }
      if (returnType == float.class) {
        return 0F;
      }
      if (returnType == double.class) {
        return 0D;
      }
      if (returnType == char.class) {
        return '\0';
      }
      throw new IllegalArgumentException("Unsupported primitive return type: " + returnType);
    }
  }
}
