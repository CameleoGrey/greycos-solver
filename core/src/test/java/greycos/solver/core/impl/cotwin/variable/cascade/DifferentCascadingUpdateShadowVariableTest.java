package greycos.solver.core.impl.cotwin.variable.cascade;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.testcotwin.cascade.distinct.TestdataDifferentCascadingEntity;
import greycos.solver.core.testcotwin.cascade.distinct.TestdataDifferentCascadingSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class DifferentCascadingUpdateShadowVariableTest {

  @Test
  void updateAllNextValues() {
    var variableDescriptor = TestdataDifferentCascadingEntity.buildVariableDescriptorForValueList();

    var scoreDirector =
        PlannerTestUtils.mockScoreDirector(
            variableDescriptor.getEntityDescriptor().getSolutionDescriptor());

    var solution = TestdataDifferentCascadingSolution.generateUninitializedSolution(3, 2);
    scoreDirector.setWorkingSolution(solution);
    assertThat(solution.getValueList())
        .allSatisfy(
            value -> {
              assertThat(value.getNumberOfCalls()).isOne();
              assertThat(value.getSecondNumberOfCalls()).isOne();
            });

    var entity = solution.getEntityList().get(0);
    scoreDirector.beforeListVariableChanged(entity, "valueList", 0, 0);
    entity.setValueList(solution.getValueList());
    scoreDirector.afterListVariableChanged(entity, "valueList", 0, 3);
    scoreDirector.updateShadowVariables();

    assertThat(entity.getValueList().get(0).getCascadeValue()).isEqualTo(2);
    assertThat(entity.getValueList().get(0).getSecondCascadeValue()).isEqualTo(2);
    assertThat(entity.getValueList().get(0).getNumberOfCalls()).isEqualTo(2);
    assertThat(entity.getValueList().get(0).getSecondNumberOfCalls()).isEqualTo(2);

    assertThat(entity.getValueList().get(1).getCascadeValue()).isEqualTo(3);
    assertThat(entity.getValueList().get(1).getSecondCascadeValue()).isEqualTo(3);
    // Called from update next val1, inverse and previous element changes
    assertThat(entity.getValueList().get(1).getNumberOfCalls()).isEqualTo(2);
    assertThat(entity.getValueList().get(1).getSecondNumberOfCalls()).isEqualTo(2);

    assertThat(entity.getValueList().get(2).getSecondCascadeValue()).isEqualTo(4);
    // Called from update next val2, inverse and previous element changes
    assertThat(entity.getValueList().get(2).getSecondNumberOfCalls()).isEqualTo(2);
  }

  @Test
  void stopUpdateNextValues() {
    var variableDescriptor = TestdataDifferentCascadingEntity.buildVariableDescriptorForValueList();

    var scoreDirector =
        PlannerTestUtils.mockScoreDirector(
            variableDescriptor.getEntityDescriptor().getSolutionDescriptor());

    var solution = TestdataDifferentCascadingSolution.generateUninitializedSolution(3, 2);
    var entity = solution.getEntityList().getFirst();
    entity.setValueList(solution.getValueList());
    scoreDirector.setWorkingSolution(solution);
    var firstCalls = entity.getValueList().get(0).getNumberOfCalls();
    var secondCalls = entity.getValueList().get(1).getNumberOfCalls();
    var thirdCalls = entity.getValueList().get(2).getNumberOfCalls();
    var thirdSecondCalls = entity.getValueList().get(2).getSecondNumberOfCalls();
    // Only the second cascade needs to propagate past value 2.
    entity.getValueList().get(1).setSecondCascadeValue(null);
    scoreDirector.beforeListVariableChanged(entity, "valueList", 0, 1);
    scoreDirector.afterListVariableChanged(entity, "valueList", 0, 1);
    scoreDirector.updateShadowVariables();

    assertThat(entity.getValueList()).extracting(v -> v.getCascadeValue()).containsExactly(2, 3, 4);
    assertThat(entity.getValueList())
        .extracting(v -> v.getSecondCascadeValue())
        .containsExactly(2, 3, 4);
    assertThat(entity.getValueList().get(0).getNumberOfCalls()).isEqualTo(firstCalls + 1);
    assertThat(entity.getValueList().get(1).getNumberOfCalls()).isEqualTo(secondCalls + 1);
    assertThat(entity.getValueList().get(2).getNumberOfCalls()).isEqualTo(thirdCalls);
    assertThat(entity.getValueList().get(2).getSecondNumberOfCalls())
        .isEqualTo(thirdSecondCalls + 1);
  }
}
