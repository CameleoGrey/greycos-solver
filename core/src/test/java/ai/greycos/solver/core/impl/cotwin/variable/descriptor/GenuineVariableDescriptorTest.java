package ai.greycos.solver.core.impl.cotwin.variable.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;

import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;
import ai.greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedEntity;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.TestdataEntityProvidingWithParameterSolution;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.inheritance.TestdataEntityProvidingEntityProvidingOnlyBaseAnnotatedExtendedSolution;

import org.junit.jupiter.api.Test;

class GenuineVariableDescriptorTest {

  @Test
  void isReinitializable() {
    var variableDescriptor = TestdataEntity.buildVariableDescriptorForValue();
    assertThat(variableDescriptor.isReinitializable(new TestdataEntity("a", new TestdataValue())))
        .isFalse();
    assertThat(variableDescriptor.isReinitializable(new TestdataEntity("b", null))).isTrue();
  }

  @Test
  void isReinitializable_allowsUnassigned() {
    var variableDescriptor = TestdataAllowsUnassignedEntity.buildVariableDescriptorForValue();
    assertThat(
            variableDescriptor.isReinitializable(
                new TestdataAllowsUnassignedEntity("a", new TestdataValue())))
        .isFalse();
    assertThat(variableDescriptor.isReinitializable(new TestdataAllowsUnassignedEntity("b", null)))
        .isTrue();
  }

  @Test
  void isReinitializable_list() {
    var variableDescriptor = TestdataListEntity.buildVariableDescriptorForValueList();
    assertThat(
            variableDescriptor.isReinitializable(
                new TestdataListEntity("a", new TestdataListValue())))
        .isFalse();
    assertThat(variableDescriptor.isReinitializable(new TestdataListEntity("b", new ArrayList<>())))
        .isFalse();
  }

  @Test
  void valueRangeDescriptorWithSolution() {
    assertThatCode(TestdataEntityProvidingWithParameterSolution::buildSolutionDescriptor)
        .doesNotThrowAnyException();
    assertThatCode(
            TestdataEntityProvidingEntityProvidingOnlyBaseAnnotatedExtendedSolution
                ::buildSolutionDescriptor)
        .doesNotThrowAnyException();
  }
}
