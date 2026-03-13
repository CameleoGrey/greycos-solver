package ai.greycos.solver.core.testcotwin.sort.invalid.mixed.comparator;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningSolution
public class TestdataInvalidMixedComparatorSortableSolution {

  private List<TestdataSortableValue> valueList;
  private List<TestdataInvalidMixedComparatorSortableEntity> entityList;
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
  public List<TestdataInvalidMixedComparatorSortableEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataInvalidMixedComparatorSortableEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }

  public void removeEntity(TestdataInvalidMixedComparatorSortableEntity entity) {
    this.entityList = entityList.stream().filter(e -> e != entity).toList();
  }
}
