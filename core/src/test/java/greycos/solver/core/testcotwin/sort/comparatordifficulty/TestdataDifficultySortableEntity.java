package greycos.solver.core.testcotwin.sort.comparatordifficulty;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.common.TestSortableObject;
import greycos.solver.core.testcotwin.common.TestSortableObjectComparator;
import greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningEntity(comparatorClass = TestSortableObjectComparator.class)
public class TestdataDifficultySortableEntity extends TestdataObject implements TestSortableObject {

  @PlanningVariable(
      valueRangeProviderRefs = "valueRange",
      comparatorClass = TestSortableObjectComparator.class)
  private TestdataSortableValue value;

  private int difficulty;

  public TestdataDifficultySortableEntity() {}

  public TestdataDifficultySortableEntity(String code, int difficulty) {
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
