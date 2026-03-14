package ai.greycos.solver.core.testcotwin.valuerange.sort.comparator;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.common.TestSortableObject;
import ai.greycos.solver.core.testcotwin.common.TestSortableObjectComparator;
import ai.greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningEntity(comparatorClass = TestSortableObjectComparator.class)
public class TestdataComparatorSortableEntityProvidingEntity extends TestdataObject
    implements TestSortableObject {

  @PlanningVariable(
      valueRangeProviderRefs = "valueRange",
      comparatorClass = TestSortableObjectComparator.class)
  private TestdataSortableValue value;

  @ValueRangeProvider(id = "valueRange")
  @PlanningEntityCollectionProperty
  private List<TestdataSortableValue> valueRange;

  private int difficulty;

  public TestdataComparatorSortableEntityProvidingEntity() {}

  public TestdataComparatorSortableEntityProvidingEntity(String code, int difficulty) {
    super(code);
    this.difficulty = difficulty;
  }

  public TestdataSortableValue getValue() {
    return value;
  }

  public void setValue(TestdataSortableValue value) {
    this.value = value;
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
