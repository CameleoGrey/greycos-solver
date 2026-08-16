package greycos.solver.core.testcotwin.list.unassignedvar.composite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.list.TestdataListValue;

@PlanningEntity
public class TestdataAllowsUnassignedCompositeListEntity extends TestdataObject {

  public static EntityDescriptor<TestdataAllowsUnassignedCompositeListSolution>
      buildEntityDescriptor() {
    return TestdataAllowsUnassignedCompositeListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataAllowsUnassignedCompositeListEntity.class);
  }

  public static ListVariableDescriptor<TestdataAllowsUnassignedCompositeListSolution>
      buildVariableDescriptorForValueList() {
    return (ListVariableDescriptor<TestdataAllowsUnassignedCompositeListSolution>)
        buildEntityDescriptor().getGenuineVariableDescriptor("valueList");
  }

  @PlanningListVariable(
      valueRangeProviderRefs = {"valueRange1", "valueRange2"},
      allowsUnassignedValues = true)
  private List<TestdataListValue> valueList;

  public TestdataAllowsUnassignedCompositeListEntity() {
    // Required for cloning
  }

  public TestdataAllowsUnassignedCompositeListEntity(
      String code, List<TestdataListValue> valueList) {
    super(code);
    this.valueList = valueList;
  }

  public TestdataAllowsUnassignedCompositeListEntity(String code, TestdataListValue... values) {
    this(code, new ArrayList<>(Arrays.asList(values)));
  }

  public List<TestdataListValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataListValue> valueList) {
    this.valueList = valueList;
  }
}
