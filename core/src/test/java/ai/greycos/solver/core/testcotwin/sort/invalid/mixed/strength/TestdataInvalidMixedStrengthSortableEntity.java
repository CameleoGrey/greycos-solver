package ai.greycos.solver.core.testcotwin.sort.invalid.mixed.strength;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.common.DummyValueComparator;
import ai.greycos.solver.core.testcotwin.common.DummyWeightValueFactory;
import ai.greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningEntity
public class TestdataInvalidMixedStrengthSortableEntity extends TestdataObject {

  @PlanningVariable(
      valueRangeProviderRefs = "valueRange",
      comparatorClass = DummyValueComparator.class,
      comparatorFactoryClass = DummyWeightValueFactory.class)
  private TestdataSortableValue value;

  private int difficulty;

  public TestdataInvalidMixedStrengthSortableEntity() {}

  public TestdataInvalidMixedStrengthSortableEntity(String code, int difficulty) {
    super(code);
    this.difficulty = difficulty;
  }

  public TestdataSortableValue getValue() {
    return value;
  }

  public void setValue(TestdataSortableValue value) {
    this.value = value;
  }

  public int getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(int difficulty) {
    this.difficulty = difficulty;
  }
}
