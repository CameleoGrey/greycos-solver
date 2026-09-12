package greycos.solver.core.testcotwin.shadow.list_element;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.testcotwin.TestdataObject;

/**
 * A list element without any element ordering shadow variables; its declarative shadow variable
 * depends on a genuine basic variable instead.
 */
@PlanningEntity
public class TestdataMixedListElementValue extends TestdataObject {

  @PlanningVariable(allowsUnassigned = true)
  Integer duration;

  @ShadowVariable(supplierName = "paddedDurationSupplier")
  Integer paddedDuration;

  public TestdataMixedListElementValue() {}

  public TestdataMixedListElementValue(String code) {
    super(code);
  }

  @ShadowSources("duration")
  public Integer paddedDurationSupplier() {
    return duration == null ? null : duration + 1;
  }

  public Integer getDuration() {
    return duration;
  }

  public void setDuration(Integer duration) {
    this.duration = duration;
  }

  public Integer getPaddedDuration() {
    return paddedDuration;
  }

  public void setPaddedDuration(Integer paddedDuration) {
    this.paddedDuration = paddedDuration;
  }
}
