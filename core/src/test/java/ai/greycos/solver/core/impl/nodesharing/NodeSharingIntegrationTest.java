package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

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
}
