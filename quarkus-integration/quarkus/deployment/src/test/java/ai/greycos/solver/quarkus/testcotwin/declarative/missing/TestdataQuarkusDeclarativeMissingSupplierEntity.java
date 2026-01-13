package ai.greycos.solver.quarkus.testcotwin.declarative.missing;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class TestdataQuarkusDeclarativeMissingSupplierEntity {
  String id;
  @PlanningVariable TestdataQuarkusDeclarativeMissingSupplierValue value;

  @ShadowVariable(supplierName = "updateDurationInDays")
  long durationInDays;

  public TestdataQuarkusDeclarativeMissingSupplierEntity(
      String id, TestdataQuarkusDeclarativeMissingSupplierValue value) {
    this.id = id;
    this.value = value;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public TestdataQuarkusDeclarativeMissingSupplierValue getValue() {
    return value;
  }

  public void setValue(TestdataQuarkusDeclarativeMissingSupplierValue value) {
    this.value = value;
  }

  @ShadowSources("value")
  public long updateDurationInDays() {
    if (value != null) {
      return value.getDuration().toDays();
    }
    return 0;
  }

  public long getDurationInDays() {
    return durationInDays;
  }

  @Override
  public String toString() {
    return "TestdataDeclarativeMissingSupplierEntity{" + "id=" + id + ", value=" + value + '}';
  }
}
