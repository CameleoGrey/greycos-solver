package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.junit.jupiter.api.Test;

class DefaultConstraintProviderNodeSharerTest {

  @Test
  void buildNodeSharedConstraintProviderValidClass() {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> result =
        sharer.buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(SimpleConstraintProvider.class.getName());
  }

  @Test
  void buildNodeSharedConstraintProviderComplexClass() {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> result =
        sharer.buildNodeSharedConstraintProvider(ComplexConstraintProvider.class);

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(ComplexConstraintProvider.class.getName());
  }

  @Test
  void buildNodeSharedConstraintProviderNoLambdas() {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> result =
        sharer.buildNodeSharedConstraintProvider(NoLambdaConstraintProvider.class);

    // Should return a class (even though no transformation occurred)
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(NoLambdaConstraintProvider.class.getName());
  }

  @Test
  void buildNodeSharedConstraintProviderFinalClass() {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    assertThatThrownBy(
            () -> sharer.buildNodeSharedConstraintProvider(FinalConstraintProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be final");
  }

  @Test
  void buildNodeSharedConstraintProviderFinalMethod() {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    assertThatThrownBy(
            () -> sharer.buildNodeSharedConstraintProvider(FinalMethodConstraintProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be final");
  }

  @Test
  void buildNodeSharedConstraintProviderReturnsDifferentClass() {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> result =
        sharer.buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);

    // Result should be a different class instance (loaded by different classloader)
    assertThat(result).isNotSameAs(SimpleConstraintProvider.class);
  }

  @Test
  void buildNodeSharedConstraintProviderInstantiable() throws ReflectiveOperationException {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> result =
        sharer.buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);

    // Verify the transformed class can be instantiated
    ConstraintProvider instance = result.getDeclaredConstructor().newInstance();

    assertThat(instance).isNotNull();
    assertThat(instance).isInstanceOf(ConstraintProvider.class);
  }

  @Test
  void buildNodeSharedConstraintProviderCapturedArguments() {
    DefaultConstraintProviderNodeSharer sharer = new DefaultConstraintProviderNodeSharer();

    Class<? extends ConstraintProvider> result =
        sharer.buildNodeSharedConstraintProvider(CapturedArgumentProvider.class);

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(CapturedArgumentProvider.class.getName());
  }
}
