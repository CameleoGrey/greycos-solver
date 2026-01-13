package ai.greycos.solver.core.testcotwin.list.valuerange.unassignedvar;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataListUnassignedEntityProvidingEntity extends TestdataObject {

  public static EntityDescriptor<TestdataListUnassignedEntityProvidingSolution>
      buildEntityDescriptor() {
    return TestdataListUnassignedEntityProvidingSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataListUnassignedEntityProvidingEntity.class);
  }

  public static ListVariableDescriptor<TestdataListUnassignedEntityProvidingSolution>
      buildVariableDescriptorForValueList() {
    return (ListVariableDescriptor<TestdataListUnassignedEntityProvidingSolution>)
        buildEntityDescriptor().getGenuineVariableDescriptor("valueList");
  }

  @ValueRangeProvider(id = "valueRange")
  private final List<TestdataValue> valueRange;

  @PlanningListVariable(valueRangeProviderRefs = "valueRange", allowsUnassignedValues = true)
  private List<TestdataValue> valueList;

  public TestdataListUnassignedEntityProvidingEntity() {
    valueRange = new ArrayList<>();
    valueList = new ArrayList<>();
  }

  public TestdataListUnassignedEntityProvidingEntity(String code, List<TestdataValue> valueRange) {
    this(code, valueRange, new ArrayList<>());
  }

  public TestdataListUnassignedEntityProvidingEntity(
      String code, List<TestdataValue> valueRange, List<TestdataValue> valueList) {
    super(code);
    this.valueRange = valueRange;
    this.valueList = valueList;
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
}
