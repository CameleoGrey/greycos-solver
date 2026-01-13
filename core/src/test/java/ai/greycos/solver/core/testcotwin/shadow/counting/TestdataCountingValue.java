package ai.greycos.solver.core.testcotwin.shadow.counting;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataCountingValue extends TestdataObject {
  @PreviousElementShadowVariable(sourceVariableName = "values")
  TestdataCountingValue previous;

  @InverseRelationShadowVariable(sourceVariableName = "values")
  TestdataCountingEntity entity;

  @ShadowVariable(supplierName = "countSupplier")
  Integer count;

  int calledCount = 0;

  public TestdataCountingValue() {}

  public TestdataCountingValue(String code) {
    super(code);
  }

  public TestdataCountingValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataCountingValue previous) {
    this.previous = previous;
  }

  public TestdataCountingEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataCountingEntity entity) {
    this.entity = entity;
  }

  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  @ShadowSources({"previous.count", "entity"})
  public Integer countSupplier() {
    if (calledCount != 0) {
      throw new IllegalStateException(
          "Supplier for entity %s was already called.".formatted(entity));
    }
    calledCount++;
    if (entity == null) {
      return null;
    }
    if (previous == null) {
      return 0;
    }
    return previous.count + 1;
  }

  public void reset() {
    calledCount = 0;
  }
}
