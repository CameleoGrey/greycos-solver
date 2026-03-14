package ai.greycos.solver.core.testcotwin.list.sort.invalid;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.common.DummyValueComparator;
import ai.greycos.solver.core.testcotwin.common.DummyValueComparatorFactory;
import ai.greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningEntity
public class TestdataInvalidListSortableEntity extends TestdataObject {

  @PlanningListVariable(
      valueRangeProviderRefs = "valueRange",
      comparatorClass = DummyValueComparator.class,
      comparatorFactoryClass = DummyValueComparatorFactory.class)
  private List<TestdataSortableValue> valueList;

  private int difficulty;

  public TestdataInvalidListSortableEntity() {}

  public TestdataInvalidListSortableEntity(String code, int difficulty) {
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

  public int getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(int difficulty) {
    this.difficulty = difficulty;
  }
}
