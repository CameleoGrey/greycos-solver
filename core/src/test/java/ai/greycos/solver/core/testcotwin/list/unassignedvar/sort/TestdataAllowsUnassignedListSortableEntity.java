package ai.greycos.solver.core.testcotwin.list.unassignedvar.sort;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.common.TestSortableObjectComparator;
import ai.greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningEntity
public class TestdataAllowsUnassignedListSortableEntity extends TestdataObject {

  @PlanningListVariable(
      allowsUnassignedValues = true,
      valueRangeProviderRefs = "valueRange",
      comparatorClass = TestSortableObjectComparator.class)
  private List<TestdataSortableValue> valueList;

  public TestdataAllowsUnassignedListSortableEntity() {}

  public TestdataAllowsUnassignedListSortableEntity(String code) {
    super(code);
    this.valueList = new ArrayList<>();
  }

  public List<TestdataSortableValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataSortableValue> valueList) {
    this.valueList = valueList;
  }
}
