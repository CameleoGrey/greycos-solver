package greycos.solver.core.testcotwin.list.shadowhistory;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataListSolutionWithShadowHistory {

  public static SolutionDescriptor<TestdataListSolutionWithShadowHistory>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataListSolutionWithShadowHistory.class,
        TestdataListEntityWithShadowHistory.class,
        TestdataListValueWithShadowHistory.class);
  }

  private List<TestdataListValueWithShadowHistory> valueList;
  private List<TestdataListEntityWithShadowHistory> entityList;
  private SimpleScore score;

  @ValueRangeProvider(id = "valueRange")
  @PlanningEntityCollectionProperty
  public List<TestdataListValueWithShadowHistory> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataListValueWithShadowHistory> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataListEntityWithShadowHistory> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataListEntityWithShadowHistory> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
