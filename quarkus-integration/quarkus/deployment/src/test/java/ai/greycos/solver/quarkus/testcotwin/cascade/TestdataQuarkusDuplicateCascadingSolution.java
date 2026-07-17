package ai.greycos.solver.quarkus.testcotwin.cascade;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;

@PlanningSolution
public class TestdataQuarkusDuplicateCascadingSolution {
  @PlanningEntityCollectionProperty List<TestdataQuarkusDuplicateCascadingEntity> entityList;

  @PlanningEntityCollectionProperty @ValueRangeProvider
  List<TestdataQuarkusDuplicateCascadingValue> valueList;

  @PlanningScore SimpleScore score;

  public TestdataQuarkusDuplicateCascadingSolution() {}

  public TestdataQuarkusDuplicateCascadingSolution(
      List<TestdataQuarkusDuplicateCascadingEntity> entityList,
      List<TestdataQuarkusDuplicateCascadingValue> valueList) {
    this.entityList = entityList;
    this.valueList = valueList;
  }

  public List<TestdataQuarkusDuplicateCascadingEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataQuarkusDuplicateCascadingEntity> entityList) {
    this.entityList = entityList;
  }

  public List<TestdataQuarkusDuplicateCascadingValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataQuarkusDuplicateCascadingValue> valueList) {
    this.valueList = valueList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
