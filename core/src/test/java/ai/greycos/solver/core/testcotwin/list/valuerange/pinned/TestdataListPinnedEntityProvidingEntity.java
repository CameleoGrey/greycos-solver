package ai.greycos.solver.core.testcotwin.list.valuerange.pinned;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.entity.PlanningPin;
import ai.greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataListPinnedEntityProvidingEntity extends TestdataObject {

  @ValueRangeProvider(id = "valueRange")
  private final List<TestdataValue> valueRange;

  @PlanningListVariable(valueRangeProviderRefs = "valueRange")
  private List<TestdataValue> valueList;

  @PlanningPin private boolean pinned;
  @PlanningPinToIndex private int pinIndex;

  public TestdataListPinnedEntityProvidingEntity() {
    // Required for cloning
    valueRange = new ArrayList<>();
    valueList = new ArrayList<>();
  }

  public TestdataListPinnedEntityProvidingEntity(String code, List<TestdataValue> valueRange) {
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

  public void setPlanningPinToIndex(int pinIndex) {
    setPinIndex(pinIndex);
  }
}
