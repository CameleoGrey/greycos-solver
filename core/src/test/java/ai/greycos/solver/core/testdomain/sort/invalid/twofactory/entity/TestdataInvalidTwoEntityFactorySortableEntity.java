package ai.greycos.solver.core.testdomain.sort.invalid.twofactory.entity;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.common.DummyValueFactory;
import ai.greycos.solver.core.testdomain.common.TestdataSortableValue;

@PlanningEntity(
    comparatorFactoryClass = DummyValueFactory.class,
    difficultyWeightFactoryClass = DummyValueFactory.class)
public class TestdataInvalidTwoEntityFactorySortableEntity extends TestdataObject {

  @PlanningVariable(
      valueRangeProviderRefs = "valueRange",
      comparatorFactoryClass = DummyValueFactory.class)
  private TestdataSortableValue value;

  private int difficulty;

  public TestdataInvalidTwoEntityFactorySortableEntity() {}

  public TestdataInvalidTwoEntityFactorySortableEntity(String code, int difficulty) {
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
