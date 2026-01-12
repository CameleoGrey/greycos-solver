package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NodeSharingValidatorTest {

  @Test
  void validateNonFinalClass() {
    assertThatThrownBy(() -> NodeSharingValidator.validate(FinalConstraintProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be final")
        .hasMessageContaining(FinalConstraintProvider.class.getName());
  }

  @Test
  void validateFinalMethod() {
    assertThatThrownBy(() -> NodeSharingValidator.validate(FinalMethodConstraintProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be final")
        .hasMessageContaining(FinalMethodConstraintProvider.class.getName())
        .hasMessageContaining("defineConstraints");
  }

  @Test
  void validateValidClass() {
    // Should not throw
    NodeSharingValidator.validate(SimpleConstraintProvider.class);
    NodeSharingValidator.validate(ComplexConstraintProvider.class);
    NodeSharingValidator.validate(NoLambdaConstraintProvider.class);
    NodeSharingValidator.validate(CapturedArgumentProvider.class);
  }

  @Test
  void validateErrorMessageFormat() {
    var exception =
        assertThatThrownBy(() -> NodeSharingValidator.validate(FinalConstraintProvider.class))
            .isInstanceOf(IllegalArgumentException.class);

    exception
        .extracting(Throwable::getMessage)
        .asString()
        .contains("ConstraintProvider class")
        .contains("must not be final")
        .contains("for automatic node sharing");
  }

  @Test
  void validateFinalMethodErrorMessageFormat() {
    var exception =
        assertThatThrownBy(() -> NodeSharingValidator.validate(FinalMethodConstraintProvider.class))
            .isInstanceOf(IllegalArgumentException.class);

    exception
        .extracting(Throwable::getMessage)
        .asString()
        .contains("ConstraintProvider method")
        .contains("must not be final")
        .contains("for automatic node sharing");
  }
}
