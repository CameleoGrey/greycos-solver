package greycos.solver.core.testcotwin.list.unassignedvar.pinned;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataPinnedUnassignedValuesListSolution {

  public static SolutionDescriptor<TestdataPinnedUnassignedValuesListSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataPinnedUnassignedValuesListSolution.class,
        TestdataPinnedUnassignedValuesListEntity.class,
        TestdataPinnedUnassignedValuesListValue.class);
  }

  private List<TestdataPinnedUnassignedValuesListValue> valueList;
  private List<TestdataPinnedUnassignedValuesListEntity> entityList;
  private SimpleScore score;

  @ValueRangeProvider(id = "valueRange")
  @PlanningEntityCollectionProperty
  public List<TestdataPinnedUnassignedValuesListValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataPinnedUnassignedValuesListValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataPinnedUnassignedValuesListEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataPinnedUnassignedValuesListEntity> entityList) {
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
