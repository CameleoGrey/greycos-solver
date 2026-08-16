package greycos.solver.quarkus.testcotwin.cascade;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;

@PlanningEntity
public class TestdataQuarkusDuplicateCascadingEntity {
  String id;

  @PlanningListVariable List<TestdataQuarkusDuplicateCascadingValue> valueList;

  public TestdataQuarkusDuplicateCascadingEntity() {
    valueList = new ArrayList<>();
  }

  public TestdataQuarkusDuplicateCascadingEntity(String id) {
    this.id = id;
    valueList = new ArrayList<>();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public List<TestdataQuarkusDuplicateCascadingValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataQuarkusDuplicateCascadingValue> valueList) {
    this.valueList = valueList;
  }
}
