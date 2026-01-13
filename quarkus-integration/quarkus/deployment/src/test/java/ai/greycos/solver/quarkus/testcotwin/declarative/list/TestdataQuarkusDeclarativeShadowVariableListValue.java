package ai.greycos.solver.quarkus.testcotwin.declarative.list;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariablesInconsistent;

@PlanningEntity
public class TestdataQuarkusDeclarativeShadowVariableListValue {
  String name;

  private List<TestdataQuarkusDeclarativeShadowVariableListValue> dependencies;

  @PreviousElementShadowVariable(sourceVariableName = "values")
  private TestdataQuarkusDeclarativeShadowVariableListValue previous;

  @ShadowVariable(supplierName = "startTimeSupplier")
  private Integer startTime;

  @ShadowVariablesInconsistent private boolean isInconsistent;

  public TestdataQuarkusDeclarativeShadowVariableListValue() {}

  public TestdataQuarkusDeclarativeShadowVariableListValue(String name) {
    this.name = name;
  }

  public TestdataQuarkusDeclarativeShadowVariableListValue(
      String name, List<TestdataQuarkusDeclarativeShadowVariableListValue> dependencies) {
    this.name = name;
    this.dependencies = dependencies;
  }

  public Integer getStartTime() {
    return startTime;
  }

  public void setStartTime(Integer startTime) {
    this.startTime = startTime;
  }

  public boolean isInconsistent() {
    return isInconsistent;
  }

  public void setInconsistent(boolean inconsistent) {
    this.isInconsistent = inconsistent;
  }

  @ShadowSources({"previous.startTime", "dependencies[].startTime"})
  private Integer startTimeSupplier() {
    if (previous == null) {
      return 0;
    }
    int minStartTime = previous.getStartTime();
    if (dependencies != null) {
      for (var dependency : dependencies) {
        if (dependency.getStartTime() > minStartTime) {
          minStartTime = dependency.getStartTime();
        }
      }
    }
    return minStartTime + 1;
  }

  public String toString() {
    return name + "(" + startTime + ")";
  }
}
