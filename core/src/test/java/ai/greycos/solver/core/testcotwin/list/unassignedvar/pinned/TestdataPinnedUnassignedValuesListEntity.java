package ai.greycos.solver.core.testcotwin.list.unassignedvar.pinned;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataPinnedUnassignedValuesListEntity extends TestdataObject {

  public static EntityDescriptor<TestdataPinnedUnassignedValuesListSolution>
      buildEntityDescriptor() {
    return TestdataPinnedUnassignedValuesListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataPinnedUnassignedValuesListEntity.class);
  }

  public static ListVariableDescriptor<TestdataPinnedUnassignedValuesListSolution>
      buildVariableDescriptorForValueList() {
    return (ListVariableDescriptor<TestdataPinnedUnassignedValuesListSolution>)
        buildEntityDescriptor().getGenuineVariableDescriptor("valueList");
  }

  public static TestdataPinnedUnassignedValuesListEntity createWithValues(
      String code, TestdataPinnedUnassignedValuesListValue... values) {
    // Set up shadow variables to preserve consistency.
    return new TestdataPinnedUnassignedValuesListEntity(code, values).setUpShadowVariables();
  }

  TestdataPinnedUnassignedValuesListEntity setUpShadowVariables() {
    for (int i = 0; i < valueList.size(); i++) {
      var testdataListValue = valueList.get(i);
      testdataListValue.setEntity(this);
      testdataListValue.setIndex(valueList.indexOf(testdataListValue));
      if (i != 0) {
        testdataListValue.setPrevious(valueList.get(i - 1));
      }
      if (i != valueList.size() - 1) {
        testdataListValue.setNext(valueList.get(i + 1));
      }
    }
    return this;
  }

  private List<TestdataPinnedUnassignedValuesListValue> valueList;
  @PlanningPinToIndex private int planningPinToIndex = 0;

  public TestdataPinnedUnassignedValuesListEntity() {}

  public TestdataPinnedUnassignedValuesListEntity(
      String code, List<TestdataPinnedUnassignedValuesListValue> valueList) {
    super(code);
    this.valueList = valueList;
  }

  public TestdataPinnedUnassignedValuesListEntity(
      String code, TestdataPinnedUnassignedValuesListValue... values) {
    this(code, new ArrayList<>(Arrays.asList(values)));
  }

  @PlanningListVariable(allowsUnassignedValues = true, valueRangeProviderRefs = "valueRange")
  public List<TestdataPinnedUnassignedValuesListValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataPinnedUnassignedValuesListValue> valueList) {
    this.valueList = valueList;
  }

  public int getPlanningPinToIndex() {
    return planningPinToIndex;
  }

  public void setPlanningPinToIndex(int planningPinToIndex) {
    this.planningPinToIndex = planningPinToIndex;
  }
}
