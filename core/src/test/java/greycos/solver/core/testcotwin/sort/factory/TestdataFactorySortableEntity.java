package greycos.solver.core.testcotwin.sort.factory;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.common.TestSortableObject;
import greycos.solver.core.testcotwin.common.TestSortableObjectComparatorFactory;
import greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningEntity(comparatorFactoryClass = TestSortableObjectComparatorFactory.class)
public class TestdataFactorySortableEntity extends TestdataObject implements TestSortableObject {

  @PlanningVariable(
      valueRangeProviderRefs = "valueRange",
      comparatorFactoryClass = TestSortableObjectComparatorFactory.class)
  private TestdataSortableValue value;

  private int difficulty;

  public TestdataFactorySortableEntity() {}

  public TestdataFactorySortableEntity(String code, int difficulty) {
    super(code);
    this.difficulty = difficulty;
  }

  public TestdataSortableValue getValue() {
    return value;
  }

  public void setValue(TestdataSortableValue value) {
    this.value = value;
  }

  @Override
  public int getComparatorValue() {
    return difficulty;
  }
}
