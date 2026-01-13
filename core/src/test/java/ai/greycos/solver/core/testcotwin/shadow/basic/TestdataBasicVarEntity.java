package ai.greycos.solver.core.testcotwin.shadow.basic;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class TestdataBasicVarEntity {
  String id;

  @PlanningVariable TestdataBasicVarValue value;

  @ShadowVariable(supplierName = "updateDurationInDays")
  long durationInDays;

  public TestdataBasicVarEntity(String id, TestdataBasicVarValue value) {
    this.id = id;
    this.value = value;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public TestdataBasicVarValue getValue() {
    return value;
  }

  public void setValue(TestdataBasicVarValue value) {
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
    return "TestdataBasicVarEntity{" + "id=" + id + ", value=" + value + '}';
  }
}
