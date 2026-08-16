package greycos.solver.core.testcotwin.list.composite;

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
public class TestdataListCompositeEntity extends TestdataObject {

  public static EntityDescriptor<TestdataListCompositeSolution> buildEntityDescriptor() {
    return TestdataListCompositeSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataListCompositeEntity.class);
  }

  public static ListVariableDescriptor<TestdataListCompositeSolution>
      buildVariableDescriptorForValueList() {
    return (ListVariableDescriptor<TestdataListCompositeSolution>)
        buildEntityDescriptor().getGenuineVariableDescriptor("valueList");
  }

  @PlanningListVariable(valueRangeProviderRefs = {"valueRange1", "valueRange2"})
  private List<TestdataListValue> valueList;

  public TestdataListCompositeEntity() {
    // Required for cloning
  }

  public TestdataListCompositeEntity(String code, List<TestdataListValue> valueList) {
    super(code);
    this.valueList = valueList;
  }

  public TestdataListCompositeEntity(String code, TestdataListValue... values) {
    this(code, new ArrayList<>(Arrays.asList(values)));
  }

  public List<TestdataListValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataListValue> valueList) {
    this.valueList = valueList;
  }
}
