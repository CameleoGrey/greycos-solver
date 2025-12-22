package ai.greycos.solver.core.testdomain.list.valuerange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningListVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningEntity
public class TestdataListEntityWithDoubleValueRange extends TestdataObject {

  public static EntityDescriptor<TestdataListSolutionWithDoubleValueRange> buildEntityDescriptor() {
    return TestdataListSolutionWithDoubleValueRange.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataListEntityWithDoubleValueRange.class);
  }

  public static GenuineVariableDescriptor<TestdataListSolutionWithDoubleValueRange>
      buildVariableDescriptorForValueList() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("valueList");
  }

  @PlanningListVariable(valueRangeProviderRefs = "doubleValueRange")
  private final List<TestdataValue> valueList;

  public TestdataListEntityWithDoubleValueRange(String code, List<TestdataValue> valueList) {
    super(code);
    this.valueList = valueList;
  }

  public TestdataListEntityWithDoubleValueRange(String code, TestdataValue... values) {
    this(code, new ArrayList<>(Arrays.asList(values)));
  }

  public List<TestdataValue> getValueList() {
    return valueList;
  }
}
