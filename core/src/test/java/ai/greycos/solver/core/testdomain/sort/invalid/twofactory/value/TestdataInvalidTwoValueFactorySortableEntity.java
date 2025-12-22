package ai.greycos.solver.core.testdomain.sort.invalid.twofactory.value;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.common.DummyValueFactory;
import ai.greycos.solver.core.testdomain.common.DummyWeightValueFactory;
import ai.greycos.solver.core.testdomain.common.TestdataSortableValue;

@PlanningEntity
public class TestdataInvalidTwoValueFactorySortableEntity extends TestdataObject {

  @PlanningVariable(
      valueRangeProviderRefs = "valueRange",
      comparatorFactoryClass = DummyValueFactory.class,
      strengthWeightFactoryClass = DummyWeightValueFactory.class)
  private TestdataSortableValue value;

  private int difficulty;

  public TestdataInvalidTwoValueFactorySortableEntity() {}

  public TestdataInvalidTwoValueFactorySortableEntity(String code, int difficulty) {
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
