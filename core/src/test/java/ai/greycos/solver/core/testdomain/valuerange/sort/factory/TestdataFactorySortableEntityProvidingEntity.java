package ai.greycos.solver.core.testdomain.valuerange.sort.factory;

import java.util.List;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.common.TestSortableFactory;
import ai.greycos.solver.core.testdomain.common.TestSortableObject;
import ai.greycos.solver.core.testdomain.common.TestdataSortableValue;

@PlanningEntity(difficultyWeightFactoryClass = TestSortableFactory.class)
public class TestdataFactorySortableEntityProvidingEntity extends TestdataObject
    implements TestSortableObject {

  @PlanningVariable(
      valueRangeProviderRefs = "valueRange",
      comparatorFactoryClass = TestSortableFactory.class)
  private TestdataSortableValue value;

  @ValueRangeProvider(id = "valueRange")
  @PlanningEntityCollectionProperty
  private List<TestdataSortableValue> valueRange;

  private int difficulty;

  public TestdataFactorySortableEntityProvidingEntity() {}

  public TestdataFactorySortableEntityProvidingEntity(String code, int difficulty) {
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
