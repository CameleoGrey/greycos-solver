package greycos.solver.core.impl.cotwin.variable.cascade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import greycos.solver.core.testcotwin.cascade.single.TestdataSingleCascadingEntity;
import greycos.solver.core.testcotwin.cascade.single.TestdataSingleCascadingSolution;
import greycos.solver.core.testcotwin.shadow.wrongcascade.TestdataCascadingInvalidField;
import greycos.solver.core.testcotwin.shadow.wrongcascade.TestdataCascadingWrongMethod;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class SingleCascadingUpdateShadowVariableTest {

  @Test
  void requiredShadowVariableDependencies() {
    assertThatIllegalArgumentException()
        .isThrownBy(TestdataCascadingWrongMethod::buildEntityDescriptor)
        .withMessageContaining(
            "The entity class (class greycos.solver.core.testcotwin.shadow.wrongcascade.TestdataCascadingWrongMethod)")
        .withMessageContaining(
            "has an @CascadingUpdateShadowVariable annotated property (cascadeValueReturnType)")
        .withMessageContaining(
            "but the method \"badUpdateCascadeValueWithReturnType\" cannot be found");

    assertThatIllegalArgumentException()
        .isThrownBy(TestdataCascadingInvalidField::buildEntityDescriptor)
        .withMessageContaining(
            "The entity class (class greycos.solver.core.testcotwin.shadow.wrongcascade.TestdataCascadingInvalidField)")
        .withMessageContaining(
            "has an @CascadingUpdateShadowVariable annotated property (cascadeValue)")
        .withMessageContaining("but the method \"value\" cannot be found");
  }

  @Test
  void updateAllNextValues() {
    var variableDescriptor = TestdataSingleCascadingEntity.buildVariableDescriptorForValueList();

    var scoreDirector =
        PlannerTestUtils.mockScoreDirector(
            variableDescriptor.getEntityDescriptor().getSolutionDescriptor());

    var solution = TestdataSingleCascadingSolution.generateUninitializedSolution(3, 2);
    scoreDirector.setWorkingSolution(solution);

    var entity = solution.getEntityList().get(0);
    scoreDirector.beforeListVariableChanged(entity, "valueList", 0, 0);
    entity.setValueList(solution.getValueList());
    scoreDirector.afterListVariableChanged(entity, "valueList", 0, 3);
    scoreDirector.updateShadowVariables();

    assertThat(entity.getValueList().get(0).getCascadeValue()).isEqualTo(2);
    assertThat(entity.getValueList().get(0).getNumberOfCalls()).isOne();

    assertThat(entity.getValueList().get(1).getCascadeValue()).isEqualTo(3);
    // Called from update next val1, inverse and previous element changes
    assertThat(entity.getValueList().get(1).getNumberOfCalls()).isOne();

    assertThat(entity.getValueList().get(2).getCascadeValue()).isEqualTo(4);
    // Called from update next val2, inverse and previous element changes
    assertThat(entity.getValueList().get(2).getNumberOfCalls()).isOne();
  }

  @Test
  void stopUpdateNextValues() {
    var variableDescriptor = TestdataSingleCascadingEntity.buildVariableDescriptorForValueList();

    var scoreDirector =
        PlannerTestUtils.mockScoreDirector(
            variableDescriptor.getEntityDescriptor().getSolutionDescriptor());

    var solution = TestdataSingleCascadingSolution.generateUninitializedSolution(3, 2);
    var entity = solution.getEntityList().getFirst();
    entity.setValueList(solution.getValueList());
    scoreDirector.setWorkingSolution(solution);
    var firstCalls = entity.getValueList().get(0).getNumberOfCalls();
    var secondCalls = entity.getValueList().get(1).getNumberOfCalls();
    var thirdCalls = entity.getValueList().get(2).getNumberOfCalls();

    // A no-op notification forces the specified range, then stops at the unchanged next value.
    scoreDirector.beforeListVariableChanged(entity, "valueList", 0, 1);
    scoreDirector.afterListVariableChanged(entity, "valueList", 0, 1);
    scoreDirector.updateShadowVariables();

    assertThat(entity.getValueList()).extracting(v -> v.getCascadeValue()).containsExactly(2, 3, 4);
    assertThat(entity.getValueList().get(0).getNumberOfCalls()).isEqualTo(firstCalls + 1);
    assertThat(entity.getValueList().get(1).getNumberOfCalls()).isEqualTo(secondCalls + 1);
    assertThat(entity.getValueList().get(2).getNumberOfCalls()).isEqualTo(thirdCalls);
  }
}
