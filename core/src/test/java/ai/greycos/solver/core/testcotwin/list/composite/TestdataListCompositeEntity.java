package ai.greycos.solver.core.testcotwin.list.composite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;

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
