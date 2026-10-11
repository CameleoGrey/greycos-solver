package greycos.solver.core.impl.cotwin.variable.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import greycos.solver.core.impl.cotwin.common.accessor.ReflectionFieldMemberAccessor;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityWithArrayValueRange;

import org.junit.jupiter.api.Test;

class ListVariableDescriptorTest {

  @Test
  void elementType() {
    assertThat(TestdataListEntity.buildVariableDescriptorForValueList().getElementType())
        .isEqualTo(TestdataListValue.class);
  }

  @Test
  void elementTypeIsResolvedOnceWhenFirstRequested() throws NoSuchFieldException {
    var accessor =
        spy(
            new ReflectionFieldMemberAccessor(
                TestdataListEntity.class.getDeclaredField("valueList")));
    var descriptor =
        new ListVariableDescriptor<>(0, TestdataListEntity.buildEntityDescriptor(), accessor);
    verify(accessor, never()).getGenericType();

    assertThat(descriptor.getElementType()).isSameAs(TestdataListValue.class);
    assertThat(descriptor.getElementType()).isSameAs(TestdataListValue.class);
    assertThat(descriptor.acceptsValueType(TestdataListValue.class)).isTrue();
    assertThat(descriptor.acceptsValueType(String.class)).isFalse();
    verify(accessor, times(1)).getGenericType();
  }

  @Test
  void invalidGenericTypeStillFailsWhenRequested() throws NoSuchFieldException {
    var accessor =
        new ReflectionFieldMemberAccessor(RawListHolder.class.getDeclaredField("values"));
    var descriptor =
        new ListVariableDescriptor<>(0, TestdataListEntity.buildEntityDescriptor(), accessor);
    for (var attempt = 0; attempt < 2; attempt++) {
      assertThatThrownBy(descriptor::getElementType)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("values")
          .hasMessageContaining("no generic parameters");
    }
  }

  @SuppressWarnings("rawtypes")
  public static class RawListHolder {
    public List values;
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
