package greycos.solver.core.testcotwin.shadow.simple_list_next;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataDeclarativeSimpleNextListValue extends TestdataObject {
  int duration;

  @NextElementShadowVariable(sourceVariableName = "values")
  TestdataDeclarativeSimpleNextListValue next;

  @InverseRelationShadowVariable(sourceVariableName = "values")
  TestdataDeclarativeSimpleNextListEntity entity;

  @ShadowVariable(supplierName = "startTimeSupplier")
  Integer startTime;

  @ShadowVariable(supplierName = "endTimeSupplier")
  Integer endTime;

  public TestdataDeclarativeSimpleNextListValue() {}

  public TestdataDeclarativeSimpleNextListValue(String code, int duration) {
    super(code);
    this.duration = duration;
  }

  public int getDuration() {
    return duration;
  }

  public TestdataDeclarativeSimpleNextListValue getNext() {
    return next;
  }

  public void setNext(TestdataDeclarativeSimpleNextListValue next) {
    this.next = next;
  }

  public TestdataDeclarativeSimpleNextListEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataDeclarativeSimpleNextListEntity entity) {
    this.entity = entity;
  }

  public Integer getStartTime() {
    return startTime;
  }

  public void setStartTime(Integer startTime) {
    this.startTime = startTime;
  }

  public Integer getEndTime() {
    return endTime;
  }

  public void setEndTime(Integer endTime) {
    this.endTime = endTime;
  }

  // Values are scheduled backwards from the entity's start time, which acts as a deadline.
  @ShadowSources({"entity", "next.startTime"})
  public Integer endTimeSupplier() {
    if (entity == null) {
      return null;
    }
    if (next == null) {
      return entity.startTime;
    }
    return next.startTime;
  }

  @ShadowSources("endTime")
  public Integer startTimeSupplier() {
    if (endTime == null) {
      return null;
    }
    return endTime - duration;
  }
}
