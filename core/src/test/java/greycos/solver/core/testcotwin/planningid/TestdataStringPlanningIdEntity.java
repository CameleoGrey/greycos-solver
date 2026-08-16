package greycos.solver.core.testcotwin.planningid;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataStringPlanningIdEntity {
  @PlanningId private String planningId;

  @PlanningVariable(valueRangeProviderRefs = "stringValueRange")
  private String value;

  public TestdataStringPlanningIdEntity(String planningId) {
    this(planningId, null);
  }

  public TestdataStringPlanningIdEntity(String planningId, String value) {
    this.planningId = planningId;
    this.value = value;
  }

  public String getPlanningId() {
    return planningId;
  }

  public void setPlanningId(String planningId) {
    this.planningId = planningId;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
