package greycos.solver.core.impl.cotwin.variable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class ShadowVariableMixedReconstructionTest {

  @Test
  void wholeSolutionMixedBasicAndListControl() {
    var oracle = solution();
    SolutionManager.updateShadowVariables(oracle);
    assertThat(oracle.getOtherValueList().getFirst().getEntityList())
        .containsExactly(oracle.getEntityList().getFirst());
    assertThat(oracle.getOtherValueList().getFirst().getDeclarativeShadowVariableValue())
        .isEqualTo(3);
    assertThat(oracle.getValueList().getFirst().getEntity())
        .isSameAs(oracle.getEntityList().getFirst());
  }

  @Test
  void entityLevelMixedBasicAndListMustPopulateBothInverseRelations() {
    var oracle = solution();
    SolutionManager.updateShadowVariables(oracle);
    assertThat(oracle.getOtherValueList().getFirst().getEntityList())
        .containsExactly(oracle.getEntityList().getFirst());
    assertThat(oracle.getOtherValueList().getFirst().getDeclarativeShadowVariableValue())
        .isEqualTo(3);

    var actual = solution();
    var entities =
        Stream.of(actual.getEntityList(), actual.getValueList(), actual.getOtherValueList())
            .flatMap(list -> list.stream())
            .toArray();
    SolutionManager.updateShadowVariables(TestdataMixedSolution.class, entities);
    SoftAssertions.assertSoftly(
        softly -> {
          softly
              .assertThat(actual.getOtherValueList().getFirst().getEntityList())
              .as("basic inverse in mixed model")
              .containsExactly(actual.getEntityList().getFirst());
          softly
              .assertThat(actual.getOtherValueList().getFirst().getDeclarativeShadowVariableValue())
              .as("inverse-dependent declarative value")
              .isEqualTo(3);
          softly
              .assertThat(actual.getValueList().getFirst().getEntity())
              .as("list inverse in mixed model")
              .isSameAs(actual.getEntityList().getFirst());
          softly.assertThat(actual.getValueList().getFirst().getIndex()).isZero();
        });
  }

  private static TestdataMixedSolution solution() {
    var solution = TestdataMixedSolution.generateUninitializedSolution(1, 1, 1);
    var entity = solution.getEntityList().getFirst();
    entity.setBasicValue(solution.getOtherValueList().getFirst());
    entity.setSecondBasicValue(solution.getOtherValueList().getFirst());
    entity.getValueList().add(solution.getValueList().getFirst());
    return solution;
  }
}
