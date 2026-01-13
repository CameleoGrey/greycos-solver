package ai.greycos.solver.core.testcotwin.list.shadowhistory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataListEntityWithShadowHistory extends TestdataObject {

  public static EntityDescriptor<TestdataListSolutionWithShadowHistory> buildEntityDescriptor() {
    return TestdataListSolutionWithShadowHistory.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataListEntityWithShadowHistory.class);
  }

  public static ListVariableDescriptor<TestdataListSolutionWithShadowHistory>
      buildVariableDescriptorForValueList() {
    return (ListVariableDescriptor<TestdataListSolutionWithShadowHistory>)
        buildEntityDescriptor().getGenuineVariableDescriptor("valueList");
  }

  public static TestdataListEntityWithShadowHistory createWithValues(
      String code, TestdataListValueWithShadowHistory... values) {
    // Set up shadow variables to preserve consistency.
    return new TestdataListEntityWithShadowHistory(code, values).setUpShadowVariables();
  }

  TestdataListEntityWithShadowHistory setUpShadowVariables() {
    for (int i = 0; i < valueList.size(); i++) {
      TestdataListValueWithShadowHistory value = valueList.get(i);
      value.setEntity(this);
      value.setIndex(i);
      if (i > 0) {
        value.setPrevious(valueList.get(i - 1));
      }
      if (i < valueList.size() - 1) {
        value.setNext(valueList.get(i + 1));
      }
    }
    return this;
  }

  @PlanningListVariable(valueRangeProviderRefs = "valueRange")
  private List<TestdataListValueWithShadowHistory> valueList;

  public TestdataListEntityWithShadowHistory() {}

  public TestdataListEntityWithShadowHistory(
      String code, List<TestdataListValueWithShadowHistory> valueList) {
    super(code);
    this.valueList = valueList;
  }

  public TestdataListEntityWithShadowHistory(
      String code, TestdataListValueWithShadowHistory... values) {
    this(code, new ArrayList<>(Arrays.asList(values)));
  }

  public List<TestdataListValueWithShadowHistory> getValueList() {
    return valueList;
  }
}
