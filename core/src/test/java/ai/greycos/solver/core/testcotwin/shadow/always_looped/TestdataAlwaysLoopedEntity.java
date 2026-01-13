package ai.greycos.solver.core.testcotwin.shadow.always_looped;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataAlwaysLoopedEntity extends TestdataObject {
  @PlanningVariable Integer value;

  @ShadowVariable(supplierName = "isEvenSupplier")
  Boolean isEven;

  @ShadowVariable(supplierName = "isOddSupplier")
  Boolean isOdd;

  public TestdataAlwaysLoopedEntity() {}

  public TestdataAlwaysLoopedEntity(String id) {
    super(id);
  }

  public TestdataAlwaysLoopedEntity(String id, int value) {
    this(id);
    this.value = value;
  }

  public Integer getValue() {
    return value;
  }

  public void setValue(Integer value) {
    this.value = value;
  }

  public Boolean getEven() {
    return isEven;
  }

  public Boolean getOdd() {
    return isOdd;
  }

  // Complex methods
  @ShadowSources({"value", "isOdd"})
  public Boolean isEvenSupplier() {
    if (value == null) {
      return null;
    }
    if (isOdd != null) {
      return !isOdd;
    }
    return value % 2 == 0;
  }

  @ShadowSources({"value", "isEven"})
  public Boolean isOddSupplier() {
    if (value == null) {
      return null;
    }
    if (isEven != null) {
      return !isEven;
    }
    return value % 2 == 1;
  }
}
