package greycos.solver.core.testcotwin.list.valuerange.unassignedvar.sortedset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataListUnassignedEntityProvidingSortedSetEntity extends TestdataObject {

  public static EntityDescriptor<TestdataListUnassignedEntityProvidingSortedSetSolution>
      buildEntityDescriptor() {
    return TestdataListUnassignedEntityProvidingSortedSetSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataListUnassignedEntityProvidingSortedSetEntity.class);
  }

  public static ListVariableDescriptor<TestdataListUnassignedEntityProvidingSortedSetSolution>
      buildVariableDescriptorForValueList() {
    return (ListVariableDescriptor<TestdataListUnassignedEntityProvidingSortedSetSolution>)
        buildEntityDescriptor().getGenuineVariableDescriptor("valueList");
  }

  @ValueRangeProvider(id = "valueRange")
  private final SortedSet<TestdataValue> valueRange;

  @PlanningListVariable(valueRangeProviderRefs = "valueRange", allowsUnassignedValues = true)
  private List<TestdataValue> valueList;

  public TestdataListUnassignedEntityProvidingSortedSetEntity() {
    valueRange = new TreeSet<>(Comparator.comparing(TestdataValue::getCode));
    valueList = new ArrayList<>();
  }

  public TestdataListUnassignedEntityProvidingSortedSetEntity(
      String code, List<TestdataValue> valueRange) {
    super(code);
    this.valueRange = new TreeSet<>(Comparator.comparing(TestdataValue::getCode));
    this.valueRange.addAll(valueRange);
    this.valueList = new ArrayList<>();
  }

  public SortedSet<TestdataValue> getValueRange() {
    return valueRange;
  }

  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }
}
