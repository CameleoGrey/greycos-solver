package ai.greycos.solver.core.testcotwin.list.sort.invalid;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningSolution
public class TestdataInvalidListSortableSolution {

  private List<TestdataSortableValue> valueList;
  private List<TestdataInvalidListSortableEntity> entityList;
  private HardSoftScore score;

  @ValueRangeProvider(id = "valueRange")
  @PlanningEntityCollectionProperty
  public List<TestdataSortableValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataSortableValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataInvalidListSortableEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataInvalidListSortableEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }

  public void removeEntity(TestdataInvalidListSortableEntity entity) {
    this.entityList = entityList.stream().filter(e -> e != entity).toList();
  }
}
