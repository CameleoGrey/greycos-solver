package ai.greycos.solver.core.testcotwin.shadow.mixed;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;

@PlanningSolution
public class TestdataMixedSolution {
  @PlanningEntityCollectionProperty List<TestdataMixedEntity> mixedEntityList;

  @PlanningEntityCollectionProperty @ValueRangeProvider List<TestdataMixedValue> mixedValueList;

  @ValueRangeProvider List<Integer> delayList;

  @PlanningScore SimpleScore score;

  public TestdataMixedSolution() {
    // required for cloning
  }

  public List<TestdataMixedEntity> getMixedEntityList() {
    return mixedEntityList;
  }

  public void setMixedEntityList(List<TestdataMixedEntity> mixedEntityList) {
    this.mixedEntityList = mixedEntityList;
  }

  public List<TestdataMixedValue> getMixedValueList() {
    return mixedValueList;
  }

  public void setMixedValueList(List<TestdataMixedValue> mixedValueList) {
    this.mixedValueList = mixedValueList;
  }

  public List<Integer> getDelayList() {
    return delayList;
  }

  public void setDelayList(List<Integer> delayList) {
    this.delayList = delayList;
  }
}
