package ai.greycos.solver.core.testcotwin.list.valuerange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataListEntityWithArrayValueRange extends TestdataObject {

  public static EntityDescriptor<TestdataListSolutionWithArrayValueRange> buildEntityDescriptor() {
    return TestdataListSolutionWithArrayValueRange.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataListEntityWithArrayValueRange.class);
  }

  public static GenuineVariableDescriptor<TestdataListSolutionWithArrayValueRange>
      buildVariableDescriptorForValueList() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("valueList");
  }

  @PlanningListVariable(valueRangeProviderRefs = "arrayValueRange")
  private List<TestdataValue> valueList;

  public TestdataListEntityWithArrayValueRange(String code, List<TestdataValue> valueList) {
    super(code);
    this.valueList = valueList;
  }

  public TestdataListEntityWithArrayValueRange(String code, TestdataValue... values) {
    this(code, new ArrayList<>(Arrays.asList(values)));
  }

  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }
}
