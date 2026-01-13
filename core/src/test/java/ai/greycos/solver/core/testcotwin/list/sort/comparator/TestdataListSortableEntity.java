package ai.greycos.solver.core.testcotwin.list.sort.comparator;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.common.TestSortableComparator;
import ai.greycos.solver.core.testcotwin.common.TestSortableObject;
import ai.greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningEntity(difficultyComparatorClass = TestSortableComparator.class)
public class TestdataListSortableEntity extends TestdataObject implements TestSortableObject {

  @PlanningListVariable(
      valueRangeProviderRefs = "valueRange",
      comparatorClass = TestSortableComparator.class)
  private List<TestdataSortableValue> valueList;

  private int difficulty;

  public TestdataListSortableEntity() {}

  public TestdataListSortableEntity(String code, int difficulty) {
    super(code);
    this.difficulty = difficulty;
    this.valueList = new ArrayList<>();
  }

  public List<TestdataSortableValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataSortableValue> valueList) {
    this.valueList = valueList;
  }

  @Override
  public int getComparatorValue() {
    return difficulty;
  }
}
