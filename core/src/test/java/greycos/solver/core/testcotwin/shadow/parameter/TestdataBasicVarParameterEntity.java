package greycos.solver.core.testcotwin.shadow.parameter;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class TestdataBasicVarParameterEntity {
  String id;

  @PlanningVariable TestdataBasicVarParameterValue value;

  @ShadowVariable(supplierName = "updateDurationInDays")
  long durationInDays;

  public TestdataBasicVarParameterEntity(String id, TestdataBasicVarParameterValue value) {
    this.id = id;
    this.value = value;
  }

  public String getId() {
    return id;
  }

  public TestdataBasicVarParameterValue getValue() {
    return value;
  }

  public void setValue(TestdataBasicVarParameterValue value) {
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

  public void setDurationInDays(long durationInDays) {
    this.durationInDays = durationInDays;
  }

  @Override
  public String toString() {
    return "TestdataBasicVarParameterEntity{" + "id=" + id + ", value=" + value + '}';
  }
}
