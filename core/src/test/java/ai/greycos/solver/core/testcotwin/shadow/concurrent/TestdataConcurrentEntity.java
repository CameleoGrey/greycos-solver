package ai.greycos.solver.core.testcotwin.shadow.concurrent;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.lookup.PlanningId;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;

@PlanningEntity
public class TestdataConcurrentEntity {
  @PlanningId String id;

  @PlanningListVariable List<TestdataConcurrentValue> values;

  public TestdataConcurrentEntity() {
    values = new ArrayList<>();
  }

  public TestdataConcurrentEntity(String id) {
    this.id = id;
    values = new ArrayList<>();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public List<TestdataConcurrentValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataConcurrentValue> values) {
    this.values = values;
  }

  public void updateValueShadows() {
    TestdataConcurrentValue previousVisit = null;
    for (var visit : values) {
      visit.setEntity(this);
      visit.setPreviousValue(previousVisit);
      if (previousVisit != null) {
        previousVisit.setNextValue(visit);
      }
      previousVisit = visit;
    }
    if (previousVisit != null) {
      previousVisit.setNextValue(null);
    }
  }

  @Override
  public String toString() {
    return id;
  }
}
