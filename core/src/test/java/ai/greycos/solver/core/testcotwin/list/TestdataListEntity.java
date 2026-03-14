package ai.greycos.solver.core.testcotwin.list;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataListEntity extends TestdataObject {

  public static EntityDescriptor<TestdataListSolution> buildEntityDescriptor() {
    return TestdataListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataListEntity.class);
  }

  public static ListVariableDescriptor<TestdataListSolution> buildVariableDescriptorForValueList() {
    return (ListVariableDescriptor<TestdataListSolution>)
        buildEntityDescriptor().getGenuineVariableDescriptor("valueList");
  }

  public static TestdataListEntity createWithValues(String code, TestdataListValue... values) {
    // Set up shadow variables to preserve consistency.
    return new TestdataListEntity(code, values).setUpShadowVariables();
  }

  public TestdataListEntity setUpShadowVariables() {
    for (int i = 0; i < valueList.size(); i++) {
      var testdataListValue = valueList.get(i);
      testdataListValue.setEntity(this);
      testdataListValue.setIndex(i);
    }
    return this;
  }

  @PlanningListVariable(valueRangeProviderRefs = "valueRange")
  private List<TestdataListValue> valueList;

  public TestdataListEntity() {}

  public TestdataListEntity(String code, List<TestdataListValue> valueList) {
    super(code);
    this.valueList = valueList;
  }

  public TestdataListEntity(String code, TestdataListValue... values) {
    this(code, new ArrayList<>(Arrays.asList(values)));
  }

  public List<TestdataListValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataListValue> valueList) {
    this.valueList = valueList;
  }

  public void addValue(TestdataListValue value) {
    addValueAt(valueList.size(), value);
  }

  public void addValueAt(int pos, TestdataListValue value) {
    List<TestdataListValue> newValueList = new ArrayList<>(valueList);
    newValueList.add(pos, value);
    this.valueList = newValueList;
  }

  public void removeValue(TestdataListValue value) {
    this.valueList = valueList.stream().filter(v -> !Objects.equals(v, value)).toList();
  }
}
