package ai.greycos.solver.core.testdomain.sort.invalid.twocomparator.entity;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.greycos.solver.core.testdomain.common.TestdataSortableValue;

@PlanningSolution
public class TestdataInvalidTwoEntityComparatorSortableSolution {

  private List<TestdataSortableValue> valueList;
  private List<TestdataInvalidTwoEntityComparatorSortableEntity> entityList;
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
  public List<TestdataInvalidTwoEntityComparatorSortableEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataInvalidTwoEntityComparatorSortableEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }

  public void removeEntity(TestdataInvalidTwoEntityComparatorSortableEntity entity) {
    this.entityList = entityList.stream().filter(e -> e != entity).toList();
  }
}
