package greycos.solver.core.impl.localsearch.decider.acceptor.tabu.size;

import static greycos.solver.core.testutil.PlannerTestUtils.mockSolverScope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.when;

import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EntityRatioTabuSizeStrategyTest {

  @ParameterizedTest
  @ValueSource(
      doubles = {
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        0.0,
        1.0,
        -0.1,
        1.1
      })
  void invalidRatioFailsFast(double ratio) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EntityRatioTabuSizeStrategy<>(ratio))
        .withMessageContaining("tabuRatio (" + ratio + ")")
        .withMessageContaining("finite");
  }

  @Test
  <Solution_> void tabuSize() {
    var phaseScope = new LocalSearchPhaseScope<Solution_>(mockSolverScope(), 0);
    when(phaseScope.getWorkingEntityCount()).thenReturn(100);
    var stepScope = new LocalSearchStepScope<>(phaseScope);
    assertThat(new EntityRatioTabuSizeStrategy<Solution_>(0.1).determineTabuSize(stepScope))
        .isEqualTo(10);
    assertThat(new EntityRatioTabuSizeStrategy<Solution_>(0.5).determineTabuSize(stepScope))
        .isEqualTo(50);
    // Rounding
    assertThat(new EntityRatioTabuSizeStrategy<Solution_>(0.1051).determineTabuSize(stepScope))
        .isEqualTo(11);
    assertThat(new EntityRatioTabuSizeStrategy<Solution_>(0.1049).determineTabuSize(stepScope))
        .isEqualTo(10);
    // Corner cases
    assertThat(new EntityRatioTabuSizeStrategy<Solution_>(0.0000001).determineTabuSize(stepScope))
        .isEqualTo(1);
    assertThat(new EntityRatioTabuSizeStrategy<Solution_>(0.9999999).determineTabuSize(stepScope))
        .isEqualTo(99);
  }
}
