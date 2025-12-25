package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.junit.jupiter.api.Test;

/** Tests for NodeSharingValidator. */
class NodeSharingValidatorTest {

  static final class FinalConstraintProvider implements ConstraintProvider {

    @Override
    public ai.greycos.solver.core.api.score.stream.Constraint[] defineConstraints(
        ai.greycos.solver.core.api.score.stream.ConstraintFactory factory) {
      return new ai.greycos.solver.core.api.score.stream.Constraint[0];
    }
  }

  static class NonFinalConstraintProvider implements ConstraintProvider {

    @Override
    public ai.greycos.solver.core.api.score.stream.Constraint[] defineConstraints(
        ai.greycos.solver.core.api.score.stream.ConstraintFactory factory) {
      return new ai.greycos.solver.core.api.score.stream.Constraint[0];
    }
  }

  static final class FinalMethodConstraintProvider implements ConstraintProvider {

    @Override
    public ai.greycos.solver.core.api.score.stream.Constraint[] defineConstraints(
        ai.greycos.solver.core.api.score.stream.ConstraintFactory factory) {
      return new ai.greycos.solver.core.api.score.stream.Constraint[0];
    }

    public final void finalMethod() {}
  }

  @Test
  void shouldAcceptNonFinalClass() {
    // Should not throw exception
    NodeSharingValidator.validate(NonFinalConstraintProvider.class);
  }

  @Test
  void shouldRejectFinalClass() {
    assertThatThrownBy(() -> NodeSharingValidator.validate(FinalConstraintProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be final");
  }

  @Test
  void shouldRejectClassWithFinalMethod() {
    assertThatThrownBy(() -> NodeSharingValidator.validate(FinalMethodConstraintProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be final");
  }
}
