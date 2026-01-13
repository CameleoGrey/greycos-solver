package ai.greycos.solver.core.impl.cotwin.variable.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;
import ai.greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityWithArrayValueRange;

import org.junit.jupiter.api.Test;

class ListVariableDescriptorTest {

  @Test
  void elementType() {
    assertThat(TestdataListEntity.buildVariableDescriptorForValueList().getElementType())
        .isEqualTo(TestdataListValue.class);
  }

  @Test
  void acceptsValueType() {
    ListVariableDescriptor<TestdataListSolution> listVariableDescriptor =
        TestdataListEntity.buildVariableDescriptorForValueList();

    assertThat(listVariableDescriptor.acceptsValueType(TestdataListValue.class)).isTrue();
    assertThat(listVariableDescriptor.acceptsValueType(List.class)).isFalse();
  }

  @Test
  void buildDescriptorWithArrayValueRange() {
    assertThatCode(TestdataListEntityWithArrayValueRange::buildVariableDescriptorForValueList)
        .doesNotThrowAnyException();
  }
}
