package ai.greycos.solver.core.testcotwin.list.valuerange.sort.factory;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.common.TestSortableFactory;
import ai.greycos.solver.core.testcotwin.common.TestSortableObject;
import ai.greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningEntity(difficultyWeightFactoryClass = TestSortableFactory.class)
public class TestdataListFactorySortableEntityProvidingEntity extends TestdataObject
    implements TestSortableObject {

  @PlanningListVariable(
      valueRangeProviderRefs = "valueRange",
      comparatorFactoryClass = TestSortableFactory.class)
  private List<TestdataSortableValue> valueList;

  @ValueRangeProvider(id = "valueRange")
  @PlanningEntityCollectionProperty
  private List<TestdataSortableValue> valueRange;

  private int difficulty;

  public TestdataListFactorySortableEntityProvidingEntity() {}

  public TestdataListFactorySortableEntityProvidingEntity(String code, int difficulty) {
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

  public List<TestdataSortableValue> getValueRange() {
    return valueRange;
  }

  public void setValueRange(List<TestdataSortableValue> valueRange) {
    this.valueRange = valueRange;
  }

  @Override
  public int getComparatorValue() {
    return difficulty;
  }
}
