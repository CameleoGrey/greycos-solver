package greycos.solver.core.testcotwin.list.valuerange.unassignedvar.pinned;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataListUnassignedPinnedEntityProvidingEntity extends TestdataObject {

  public static EntityDescriptor<TestdataListUnassignedPinnedEntityProvidingSolution>
      buildEntityDescriptor() {
    return TestdataListUnassignedPinnedEntityProvidingSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataListUnassignedPinnedEntityProvidingEntity.class);
  }

  public static ListVariableDescriptor<TestdataListUnassignedPinnedEntityProvidingSolution>
      buildVariableDescriptorForValueList() {
    return (ListVariableDescriptor<TestdataListUnassignedPinnedEntityProvidingSolution>)
        buildEntityDescriptor().getGenuineVariableDescriptor("valueList");
  }

  @ValueRangeProvider(id = "valueRange")
  private final List<TestdataValue> valueRange;

  @PlanningListVariable(valueRangeProviderRefs = "valueRange", allowsUnassignedValues = true)
  private List<TestdataValue> valueList;

  @PlanningPin private boolean pinned;
  @PlanningPinToIndex private int pinIndex;

  public TestdataListUnassignedPinnedEntityProvidingEntity() {
    // Required for cloning
    valueRange = new ArrayList<>();
    valueList = new ArrayList<>();
  }

  public TestdataListUnassignedPinnedEntityProvidingEntity(
      String code, List<TestdataValue> valueRange) {
    super(code);
    this.valueRange = valueRange;
    valueList = new ArrayList<>();
  }

  public List<TestdataValue> getValueRange() {
    return valueRange;
  }

  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
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
}
