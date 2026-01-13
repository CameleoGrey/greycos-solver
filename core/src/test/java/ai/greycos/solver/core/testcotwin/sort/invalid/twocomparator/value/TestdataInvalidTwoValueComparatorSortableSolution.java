package ai.greycos.solver.core.testcotwin.sort.invalid.twocomparator.value;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.greycos.solver.core.testcotwin.common.TestdataSortableValue;

@PlanningSolution
public class TestdataInvalidTwoValueComparatorSortableSolution {

  private List<TestdataSortableValue> valueList;
  private List<TestdataInvalidTwoValueComparatorSortableEntity> entityList;
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
  public List<TestdataInvalidTwoValueComparatorSortableEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataInvalidTwoValueComparatorSortableEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }

  public void removeEntity(TestdataInvalidTwoValueComparatorSortableEntity entity) {
    this.entityList = entityList.stream().filter(e -> e != entity).toList();
  }
}
