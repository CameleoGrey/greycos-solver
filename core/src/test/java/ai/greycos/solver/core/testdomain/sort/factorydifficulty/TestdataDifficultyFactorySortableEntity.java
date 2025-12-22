package ai.greycos.solver.core.testdomain.sort.factorydifficulty;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.common.TestSortableFactory;
import ai.greycos.solver.core.testdomain.common.TestSortableObject;
import ai.greycos.solver.core.testdomain.common.TestdataSortableValue;

@PlanningEntity(difficultyWeightFactoryClass = TestSortableFactory.class)
public class TestdataDifficultyFactorySortableEntity extends TestdataObject
    implements TestSortableObject {

  @PlanningVariable(
      valueRangeProviderRefs = "valueRange",
      strengthWeightFactoryClass = TestSortableFactory.class)
  private TestdataSortableValue value;

  private int difficulty;

  public TestdataDifficultyFactorySortableEntity() {}

  public TestdataDifficultyFactorySortableEntity(String code, int difficulty) {
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
