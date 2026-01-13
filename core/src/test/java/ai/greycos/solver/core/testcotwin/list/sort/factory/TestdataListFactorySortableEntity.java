package ai.greycos.solver.core.testcotwin.list.sort.factory;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.common.TestSortableFactory;
import ai.greycos.solver.core.testcotwin.common.TestSortableObject;
import ai.greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningEntity(difficultyWeightFactoryClass = TestSortableFactory.class)
public class TestdataListFactorySortableEntity extends TestdataObject
    implements TestSortableObject {

  @PlanningListVariable(
      valueRangeProviderRefs = "valueRange",
      comparatorFactoryClass = TestSortableFactory.class)
  private List<TestdataSortableValue> valueList;

  private int difficulty;

  public TestdataListFactorySortableEntity() {}

  public TestdataListFactorySortableEntity(String code, int difficulty) {
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
