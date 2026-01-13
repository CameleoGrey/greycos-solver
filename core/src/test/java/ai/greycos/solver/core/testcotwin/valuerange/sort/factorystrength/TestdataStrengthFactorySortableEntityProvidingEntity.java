package ai.greycos.solver.core.testcotwin.valuerange.sort.factorystrength;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.common.TestSortableFactory;
import ai.greycos.solver.core.testcotwin.common.TestSortableObject;
import ai.greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningEntity(difficultyWeightFactoryClass = TestSortableFactory.class)
public class TestdataStrengthFactorySortableEntityProvidingEntity extends TestdataObject
    implements TestSortableObject {

  @PlanningVariable(
      valueRangeProviderRefs = "valueRange",
      strengthWeightFactoryClass = TestSortableFactory.class)
  private TestdataSortableValue value;

  @ValueRangeProvider(id = "valueRange")
  @PlanningEntityCollectionProperty
  private List<TestdataSortableValue> valueRange;

  private int difficulty;

  public TestdataStrengthFactorySortableEntityProvidingEntity() {}

  public TestdataStrengthFactorySortableEntityProvidingEntity(String code, int difficulty) {
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
