package ai.greycos.solver.core.testcotwin.shadow.mixed;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataMixedValue extends TestdataObject {
  @PlanningVariable Integer delay;

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  private TestdataMixedEntity entity;

  @PreviousElementShadowVariable(sourceVariableName = "valueList")
  private TestdataMixedValue previous;

  @ShadowVariable(supplierName = "previousDelaySupplier")
  private Integer previousDelay;

  public TestdataMixedValue() {
    // required for cloning
  }

  public TestdataMixedValue(String code) {
    super(code);
  }

  @ShadowSources({"previous", "previous.delay"})
  private Integer previousDelaySupplier() {
    if (previous == null) {
      return null;
    } else {
      return previous.delay;
    }
  }

  public Integer getDelay() {
    return delay;
  }

  public void setDelay(Integer delay) {
    this.delay = delay;
  }

  public TestdataMixedEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataMixedEntity entity) {
    this.entity = entity;
  }

  public TestdataMixedValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataMixedValue previous) {
    this.previous = previous;
  }

  public Integer getPreviousDelay() {
    return previousDelay;
  }

  public void setPreviousDelay(Integer previousDelay) {
    this.previousDelay = previousDelay;
  }
}
