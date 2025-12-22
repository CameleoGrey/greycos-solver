package ai.greycos.solver.core.enterprise;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class GreycosSolverEnterpriseServiceTest {

  @Test
  void failsOnLoad() {
    Assertions.assertThatThrownBy(GreycosSolverEnterpriseService::load)
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void solverVersion() {
    Assertions.assertThat(GreycosSolverEnterpriseService.identifySolverVersion())
        .contains("Community Edition");
  }
}
