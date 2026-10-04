package greycos.solver.core.testcotwin.pinned.entityvalue;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataPinnedValueSolution {

  public static SolutionDescriptor<TestdataPinnedValueSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataPinnedValueSolution.class, TestdataPinnedValueEntity.class);
  }

  public static TestdataPinnedValueSolution generateSolution() {
    var first = new TestdataPinnedValueEntity("first", false);
    var pinned = new TestdataPinnedValueEntity("pinned", true);
    var outsideRange = new TestdataPinnedValueEntity("outsideRange", true);
    var solution = new TestdataPinnedValueSolution();
    solution.entityList = List.of(first, pinned, outsideRange);
    solution.valueRange = List.of(first, pinned);
    for (var entity : solution.entityList) {
      entity.setEntityRange(solution.valueRange);
      entity.setSolutionValue(first);
      entity.setNullableSolutionValue(first);
      entity.setEntityValue(first);
      entity.setNullableEntityValue(first);
    }
    return solution;
  }

  private List<TestdataPinnedValueEntity> entityList;
  private List<TestdataPinnedValueEntity> valueRange;
  private SimpleScore score;

  @PlanningEntityCollectionProperty
  public List<TestdataPinnedValueEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataPinnedValueEntity> entityList) {
    this.entityList = entityList;
  }

  @ValueRangeProvider(id = "solutionRange")
  public List<TestdataPinnedValueEntity> getValueRange() {
    return valueRange;
  }

  public void setValueRange(List<TestdataPinnedValueEntity> valueRange) {
    this.valueRange = valueRange;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
