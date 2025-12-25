package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;

import org.junit.jupiter.api.Test;

/** Integration test for automatic node sharing via ASM bytecode transformation. */
class AutomaticNodeSharingIntegrationTest {

  @Test
  void shouldTransformConstraintProvider() {
    var nodeSharer = new DefaultConstraintProviderNodeSharer();

    Class<TestConstraintProvider> transformedClass =
        nodeSharer.buildNodeSharedConstraintProvider(TestConstraintProvider.class);

    // Verify transformed class has same name as original (transformed in-place)
    assertThat(transformedClass.getName()).isEqualTo(TestConstraintProvider.class.getName());

    // Verify it's a different class object than original
    assertThat(transformedClass).isNotSameAs(TestConstraintProvider.class);

    // Verify it's still a ConstraintProvider
    assertThat(
            ai.greycos.solver.core.api.score.stream.ConstraintProvider.class.isAssignableFrom(
                transformedClass))
        .isTrue();
  }

  @Test
  void shouldRejectFinalConstraintProvider() {
    var nodeSharer = new DefaultConstraintProviderNodeSharer();

    assertThatThrownBy(
            () -> nodeSharer.buildNodeSharedConstraintProvider(FinalConstraintProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be final");
  }

  @Test
  void shouldRejectConstraintProviderWithFinalMethod() {
    var nodeSharer = new DefaultConstraintProviderNodeSharer();

    assertThatThrownBy(
            () -> nodeSharer.buildNodeSharedConstraintProvider(FinalMethodConstraintProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be final");
  }

  @Test
  void shouldHandleConstraintProviderWithoutLambdas() {
    var nodeSharer = new DefaultConstraintProviderNodeSharer();

    // Should work fine even with no lambdas
    Class<NoLambdaConstraintProvider> transformedClass =
        nodeSharer.buildNodeSharedConstraintProvider(NoLambdaConstraintProvider.class);

    assertThat(transformedClass).isNotNull();
    assertThat(
            ai.greycos.solver.core.api.score.stream.ConstraintProvider.class.isAssignableFrom(
                transformedClass))
        .isTrue();
  }

  @Test
  void shouldUseTransformedClassWhenConfigured() {
    // Test that configuration is properly read
    var config =
        new ScoreDirectorFactoryConfig()
            .withConstraintProviderClass(TestConstraintProvider.class)
            .withConstraintStreamAutomaticNodeSharing(true);

    assertThat(config.getConstraintStreamAutomaticNodeSharing()).isTrue();
    assertThat(config.getConstraintProviderClass()).isEqualTo(TestConstraintProvider.class);
  }

  @Test
  void shouldNotTransformWhenDisabled() {
    var config =
        new ScoreDirectorFactoryConfig()
            .withConstraintProviderClass(TestConstraintProvider.class)
            .withConstraintStreamAutomaticNodeSharing(false);

    assertThat(config.getConstraintStreamAutomaticNodeSharing()).isFalse();
  }

  @Test
  void shouldHandleNullConfigurationGracefully() {
    var config = new ScoreDirectorFactoryConfig();

    // Null means disabled (not enabled)
    assertThat(config.getConstraintStreamAutomaticNodeSharing()).isNull();
  }

  /**
   * Test that identical lambdas in same class are shared. This is core value proposition of node
   * sharing.
   */
  @Test
  void shouldShareIdenticalLambdas() {
    var nodeSharer = new DefaultConstraintProviderNodeSharer();

    // Transform class
    Class<TestConstraintProvider> transformedClass =
        nodeSharer.buildNodeSharedConstraintProvider(TestConstraintProvider.class);

    // Verify it's a different class object than original (transformed in separate loader)
    assertThat(transformedClass).isNotSameAs(TestConstraintProvider.class);

    // The transformed class should have static final fields for shared lambdas
    // We can verify this by checking for fields
    java.lang.reflect.Field[] fields = transformedClass.getDeclaredFields();

    // Should have at least one static final field for shared lambda
    boolean hasSharedLambdaField = false;
    for (java.lang.reflect.Field field : fields) {
      int modifiers = field.getModifiers();
      if (java.lang.reflect.Modifier.isStatic(modifiers)
          && java.lang.reflect.Modifier.isFinal(modifiers)
          && field.getName().startsWith("$")) {
        hasSharedLambdaField = true;
        break;
      }
    }

    assertThat(hasSharedLambdaField)
        .as("Transformed class should have static final fields for shared lambdas")
        .isTrue();
  }

  /** Test that transformation is idempotent - transforming twice should work. */
  @Test
  void transformationShouldBeIdempotent() {
    var nodeSharer = new DefaultConstraintProviderNodeSharer();

    // Transform once
    Class<TestConstraintProvider> firstTransform =
        nodeSharer.buildNodeSharedConstraintProvider(TestConstraintProvider.class);

    // Transform again (should work without issues)
    Class<TestConstraintProvider> secondTransform =
        nodeSharer.buildNodeSharedConstraintProvider(TestConstraintProvider.class);

    assertThat(firstTransform.getName()).isEqualTo(secondTransform.getName());
  }

  /** Test with a more complex ConstraintProvider. */
  @Test
  void shouldHandleComplexConstraintProvider() {
    var nodeSharer = new DefaultConstraintProviderNodeSharer();

    Class<ComplexConstraintProvider> transformedClass =
        nodeSharer.buildNodeSharedConstraintProvider(ComplexConstraintProvider.class);

    assertThat(transformedClass).isNotNull();
    assertThat(
            ai.greycos.solver.core.api.score.stream.ConstraintProvider.class.isAssignableFrom(
                transformedClass))
        .isTrue();

    // Should have multiple shared lambda fields
    java.lang.reflect.Field[] fields = transformedClass.getDeclaredFields();
    long sharedLambdaCount = 0;
    for (java.lang.reflect.Field field : fields) {
      int modifiers = field.getModifiers();
      if (java.lang.reflect.Modifier.isStatic(modifiers)
          && java.lang.reflect.Modifier.isFinal(modifiers)
          && field.getName().startsWith("$")) {
        sharedLambdaCount++;
      }
    }

    // Should have at least one shared lambda (for three identical filters)
    assertThat(sharedLambdaCount)
        .as("Should have at least one shared lambda field")
        .isGreaterThan(0);
  }
}
