package greycos.solver.core.testcotwin.planningid;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.testcotwin.TestdataSolution;

@PlanningSolution
public class TestdataStringPlanningIdSolution extends TestdataSolution {
  private List<String> stringValueList;
  private List<TestdataStringPlanningIdEntity> stringEntityList;

  @ValueRangeProvider(id = "stringValueRange")
  @ProblemFactCollectionProperty
  public List<String> getStringValueList() {
    return stringValueList;
  }

  public void setStringValueList(List<String> stringValueList) {
    this.stringValueList = stringValueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataStringPlanningIdEntity> getStringEntityList() {
    return stringEntityList;
  }

  public void setStringEntityList(List<TestdataStringPlanningIdEntity> stringEntityList) {
    this.stringEntityList = stringEntityList;
  }
}
