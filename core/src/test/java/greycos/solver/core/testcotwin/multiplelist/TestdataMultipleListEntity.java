package greycos.solver.core.testcotwin.multiplelist;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataMultipleListEntity extends TestdataObject {

  @PlanningListVariable(valueRangeProviderRefs = "firstValueRange")
  private List<TestdataMultipleListFirstValue> firstValueList = new ArrayList<>();

  @PlanningListVariable(valueRangeProviderRefs = "secondValueRange")
  private List<TestdataMultipleListSecondValue> secondValueList = new ArrayList<>();

  @PlanningVariable(valueRangeProviderRefs = "basicValueRange")
  private Integer basicValue;

  @PlanningPin private boolean pinned;

  @PlanningPinToIndex(variableName = "firstValueList")
  private int firstPinnedIndex;

  @PlanningPinToIndex(variableName = "secondValueList")
  private int secondPinnedIndex;

  public TestdataMultipleListEntity() {}

  public TestdataMultipleListEntity(String code) {
    super(code);
  }

  public static EntityDescriptor<TestdataMultipleListSolution> buildEntityDescriptor() {
    return TestdataMultipleListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataMultipleListEntity.class);
  }

  public static ListVariableDescriptor<TestdataMultipleListSolution>
      buildVariableDescriptorForFirstValueList() {
    return (ListVariableDescriptor<TestdataMultipleListSolution>)
        buildEntityDescriptor().getGenuineVariableDescriptor("firstValueList");
  }

  public static ListVariableDescriptor<TestdataMultipleListSolution>
      buildVariableDescriptorForSecondValueList() {
    return (ListVariableDescriptor<TestdataMultipleListSolution>)
        buildEntityDescriptor().getGenuineVariableDescriptor("secondValueList");
  }

  public List<TestdataMultipleListFirstValue> getFirstValueList() {
    return firstValueList;
  }

  public void setFirstValueList(List<TestdataMultipleListFirstValue> firstValueList) {
    this.firstValueList = firstValueList;
  }

  public List<TestdataMultipleListSecondValue> getSecondValueList() {
    return secondValueList;
  }

  public void setSecondValueList(List<TestdataMultipleListSecondValue> secondValueList) {
    this.secondValueList = secondValueList;
  }

  public Integer getBasicValue() {
    return basicValue;
  }

  public void setBasicValue(Integer basicValue) {
    this.basicValue = basicValue;
  }

  public boolean isPinned() {
    return pinned;
  }

  public void setPinned(boolean pinned) {
    this.pinned = pinned;
  }

  public int getFirstPinnedIndex() {
    return firstPinnedIndex;
  }

  public void setFirstPinnedIndex(int firstPinnedIndex) {
    this.firstPinnedIndex = firstPinnedIndex;
  }

  public int getSecondPinnedIndex() {
    return secondPinnedIndex;
  }

  public void setSecondPinnedIndex(int secondPinnedIndex) {
    this.secondPinnedIndex = secondPinnedIndex;
  }
}
