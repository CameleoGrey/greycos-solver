package greycos.solver.core.testcotwin.list.pinned.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataPinnedWithIndexListEntity extends TestdataObject {

  public static EntityDescriptor<TestdataPinnedWithIndexListSolution> buildEntityDescriptor() {
    return TestdataPinnedWithIndexListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataPinnedWithIndexListEntity.class);
  }

  public static ListVariableDescriptor<TestdataPinnedWithIndexListSolution>
      buildVariableDescriptorForValueList() {
    return (ListVariableDescriptor<TestdataPinnedWithIndexListSolution>)
        buildEntityDescriptor().getGenuineVariableDescriptor("valueList");
  }

  public static TestdataPinnedWithIndexListEntity createWithValues(
      String code, TestdataPinnedWithIndexListValue... values) {
    // Set up shadow variables to preserve consistency.
    return new TestdataPinnedWithIndexListEntity(code, values).setUpShadowVariables();
  }

  public TestdataPinnedWithIndexListEntity setUpShadowVariables() {
    valueList.forEach(
        testdataListValue -> {
          testdataListValue.setEntity(this);
        });
    return this;
  }

  private List<TestdataPinnedWithIndexListValue> valueList;

  @PlanningPin private boolean pinned;

  @PlanningPinToIndex private int pinIndex;

  public TestdataPinnedWithIndexListEntity() {}

  public TestdataPinnedWithIndexListEntity(
      String code, List<TestdataPinnedWithIndexListValue> valueList) {
    super(code);
    this.valueList = valueList;
  }

  public TestdataPinnedWithIndexListEntity(
      String code, TestdataPinnedWithIndexListValue... values) {
    this(code, new ArrayList<>(Arrays.asList(values)));
  }

  @PlanningListVariable(valueRangeProviderRefs = "valueRange")
  public List<TestdataPinnedWithIndexListValue> getValueList() {
    if (pinned) {
      return Collections.unmodifiableList(
          valueList); // Hard fail when something tries to modify the list.
    }
    return valueList;
  }

  public void setValueList(List<TestdataPinnedWithIndexListValue> valueList) {
    this.valueList = valueList;
  }

  public boolean isPinned() {
    return pinned;
  }

  public void setPinned(boolean pinned) {
    this.pinned = pinned;
  }

  public int getPinIndex() {
    return pinIndex;
  }

  public void setPinIndex(int pinIndex) {
    this.pinIndex = pinIndex;
  }

  public void setPlanningPinToIndex(int pinIndex) {
    setPinIndex(pinIndex);
  }
}
