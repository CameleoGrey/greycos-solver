package ai.greycos.solver.quarkus.testcotwin.declarative.list;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;

@PlanningEntity
public class TestdataQuarkusDeclarativeShadowVariableListEntity {
  String name;

  @PlanningListVariable List<TestdataQuarkusDeclarativeShadowVariableListValue> values;

  public TestdataQuarkusDeclarativeShadowVariableListEntity() {
    this.values = new ArrayList<>();
  }

  public TestdataQuarkusDeclarativeShadowVariableListEntity(String name) {
    this.name = name;
    this.values = new ArrayList<>();
  }

  @Override
  public String toString() {
    return name + " " + values;
  }
}
