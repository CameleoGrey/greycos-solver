package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.uni.UniConstraintBuilder;
import ai.greycos.solver.core.api.score.stream.uni.UniConstraintStream;

import org.junit.jupiter.api.Test;

class AutomaticNodeSharingBehaviorTest {

  @Test
  void analyzerFindsShareableInlineLambdas() {
    LambdaAnalysis simpleAnalysis =
        new ConstraintProviderAnalyzer(SimpleConstraintProvider.class).analyze();
    LambdaAnalysis complexAnalysis =
        new ConstraintProviderAnalyzer(ComplexConstraintProvider.class).analyze();
    LambdaAnalysis capturedAnalysis =
        new ConstraintProviderAnalyzer(CapturedArgumentProvider.class).analyze();

    assertThat(simpleAnalysis.getShareableLambdaGroupCount()).isEqualTo(1);
    assertThat(simpleAnalysis.getShareableLambdaCount()).isEqualTo(2);
    assertThat(complexAnalysis.getShareableLambdaGroupCount()).isEqualTo(1);
    assertThat(complexAnalysis.getShareableLambdaCount()).isEqualTo(3);
    assertThat(capturedAnalysis.hasShareableLambdas()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void transformationReusesStatelessInlineLambdaInstances() throws ReflectiveOperationException {
    SimpleConstraintProvider original = new SimpleConstraintProvider();
    List<Predicate<String>> originalPredicates = new ArrayList<>();
    original.defineConstraints(newCollectingFactory(originalPredicates));

    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();
    Class<? extends ai.greycos.solver.core.api.score.stream.ConstraintProvider> transformedClass =
        sharer.buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);
    var transformed =
        (ai.greycos.solver.core.api.score.stream.ConstraintProvider)
            transformedClass.getDeclaredConstructor().newInstance();
    List<Predicate<String>> transformedPredicates = new ArrayList<>();
    transformed.defineConstraints(newCollectingFactory(transformedPredicates));

    assertThat(originalPredicates).hasSize(2);
    assertThat(originalPredicates.get(0)).isNotSameAs(originalPredicates.get(1));
    assertThat(transformedPredicates).hasSize(2);
    assertThat(transformedPredicates.get(0)).isSameAs(transformedPredicates.get(1));
    assertThat(transformedClass.getDeclaredFields())
        .filteredOn(field -> field.getName().startsWith("$"))
        .hasSize(1);
    assertThat(transformedClass.getDeclaredFields())
        .filteredOn(field -> field.getName().startsWith("$"))
        .allSatisfy(
            field -> {
              assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
              assertThat(Modifier.isStatic(field.getModifiers())).isTrue();
              assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
            });
    assertThat(transformedPredicates.get(0).test("x")).isTrue();
    assertThat(transformedPredicates.get(0).test("")).isFalse();
  }

  @Test
  void transformationDoesNotShareCapturedLambdas() throws ReflectiveOperationException {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();
    Class<? extends ai.greycos.solver.core.api.score.stream.ConstraintProvider> transformedClass =
        sharer.buildNodeSharedConstraintProvider(CapturedArgumentProvider.class);
    var transformed = transformedClass.getDeclaredConstructor(int.class).newInstance(5);
    List<Predicate<String>> predicates = new ArrayList<>();
    ((ai.greycos.solver.core.api.score.stream.ConstraintProvider) transformed)
        .defineConstraints(newCollectingFactory(predicates));

    assertThat(predicates).hasSize(3);
    assertThat(transformedClass.getDeclaredFields())
        .filteredOn(field -> field.getName().startsWith("$"))
        .isEmpty();
    assertThat(predicates.get(0).test("123456")).isTrue();
    assertThat(predicates.get(0).test("12345")).isFalse();
  }

  @Test
  void transformationPreservesExistingStaticInitializer() throws ReflectiveOperationException {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();
    Class<? extends ai.greycos.solver.core.api.score.stream.ConstraintProvider> transformedClass =
        sharer.buildNodeSharedConstraintProvider(StaticInitializerConstraintProvider.class);
    var constructor = transformedClass.getDeclaredConstructor();
    constructor.setAccessible(true);
    var transformed =
        (ai.greycos.solver.core.api.score.stream.ConstraintProvider) constructor.newInstance();
    List<Predicate<String>> predicates = new ArrayList<>();
    transformed.defineConstraints(newCollectingFactory(predicates));

    assertThat(predicates).hasSize(2);
    assertThat(predicates.get(0)).isSameAs(predicates.get(1));
    assertThat(predicates.get(0).test("A1")).isTrue();
    assertThat(predicates.get(0).test("B1")).isFalse();
  }

  @SuppressWarnings("unchecked")
  private static ConstraintFactory newCollectingFactory(List<Predicate<String>> predicates) {
    UniConstraintBuilder<?, ?> builder =
        (UniConstraintBuilder<?, ?>)
            Proxy.newProxyInstance(
                UniConstraintBuilder.class.getClassLoader(),
                new Class<?>[] {UniConstraintBuilder.class},
                (proxy, method, args) -> {
                  if ("asConstraint".equals(method.getName())) {
                    return Proxy.newProxyInstance(
                        Constraint.class.getClassLoader(),
                        new Class<?>[] {Constraint.class},
                        (constraintProxy, constraintMethod, constraintArgs) -> null);
                  }
                  return proxy;
                });

    UniConstraintStream<?> stream =
        (UniConstraintStream<?>)
            Proxy.newProxyInstance(
                UniConstraintStream.class.getClassLoader(),
                new Class<?>[] {UniConstraintStream.class},
                (proxy, method, args) -> {
                  if ("filter".equals(method.getName())) {
                    predicates.add((Predicate<String>) args[0]);
                    return proxy;
                  }
                  if ("penalize".equals(method.getName())) {
                    return builder;
                  }
                  return proxy;
                });

    return (ConstraintFactory)
        Proxy.newProxyInstance(
            ConstraintFactory.class.getClassLoader(),
            new Class<?>[] {ConstraintFactory.class},
            (proxy, method, args) -> {
              if ("forEach".equals(method.getName())) {
                return stream;
              }
              return null;
            });
  }
}
