package greycos.solver.core.testcotwin.unassignedvar.sort;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.common.TestSortableObjectComparator;
import greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningEntity
public class TestdataAllowsUnassignedSortableEntity extends TestdataObject {

  @PlanningVariable(
      allowsUnassigned = true,
      valueRangeProviderRefs = "valueRange",
      comparatorClass = TestSortableObjectComparator.class)
  private TestdataSortableValue value;

  public TestdataAllowsUnassignedSortableEntity() {}

  public TestdataAllowsUnassignedSortableEntity(String code) {
    super(code);
  }

  public TestdataSortableValue getValue() {
    return value;
  }

  public void setValue(TestdataSortableValue value) {
    this.value = value;
  }
}
