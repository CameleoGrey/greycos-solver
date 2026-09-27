package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Regression coverage for replacing the old duplicate-class loader with original-loader subclasses.
 */
class NodeSharingSubclassTest {

  @Test
  void transformedClassIsAnOriginalLoaderNestmateSubclass() {
    Class<?> result =
        new DefaultConstraintProviderNodeSharer()
            .buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);

    assertThat(result.isHidden()).isTrue();
    assertThat(result.getSuperclass()).isSameAs(SimpleConstraintProvider.class);
    assertThat(result.getClassLoader()).isSameAs(SimpleConstraintProvider.class.getClassLoader());
    assertThat(result.getModule()).isSameAs(SimpleConstraintProvider.class.getModule());
    assertThat(result.isNestmateOf(SimpleConstraintProvider.class)).isTrue();
  }

  @Test
  void transformationIsCachedAcrossSharerInstances() {
    Class<?> first =
        new DefaultConstraintProviderNodeSharer()
            .buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);
    Class<?> second =
        new DefaultConstraintProviderNodeSharer()
            .buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);

    assertThat(second).isSameAs(first);
  }

  @Test
  void differentProvidersReceiveDifferentSubclasses() {
    Class<?> first =
        new DefaultConstraintProviderNodeSharer()
            .buildNodeSharedConstraintProvider(SimpleConstraintProvider.class);
    Class<?> second =
        new DefaultConstraintProviderNodeSharer()
            .buildNodeSharedConstraintProvider(ComplexConstraintProvider.class);

    assertThat(first).isNotSameAs(second);
    assertThat(second.getSuperclass()).isSameAs(ComplexConstraintProvider.class);
  }
}
