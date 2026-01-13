package ai.greycos.solver.core.testcotwin.shadow.missing;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class TestdataDeclarativeMissingSupplierEntity {
  String id;
  @PlanningVariable TestdataDeclarativeMissingSupplierValue value;

  @ShadowVariable(supplierName = "updateDurationInDays")
  long durationInDays;

  public TestdataDeclarativeMissingSupplierEntity(
      String id, TestdataDeclarativeMissingSupplierValue value) {
    this.id = id;
    this.value = value;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public TestdataDeclarativeMissingSupplierValue getValue() {
    return value;
  }

  public void setValue(TestdataDeclarativeMissingSupplierValue value) {
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
