package greycos.solver.core.testcotwin.list.pinned.unassignedvar;

import java.util.List;
import java.util.stream.IntStream;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;

@PlanningSolution
public class TestdataPinnedAllowsUnassignedValuesListSolution {

  public static SolutionDescriptor<TestdataPinnedAllowsUnassignedValuesListSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataPinnedAllowsUnassignedValuesListSolution.class,
        TestdataPinnedAllowsUnassignedValuesListEntity.class,
        TestdataPinnedAllowsUnassignedValuesListValue.class);
  }

  public static PlanningSolutionMetaModel<TestdataPinnedAllowsUnassignedValuesListSolution>
      buildMetaModel() {
    return buildSolutionDescriptor().getMetaModel();
  }

  public static TestdataPinnedAllowsUnassignedValuesListSolution generateUninitializedSolution(
      int valueCount, int entityCount) {
    var entityList =
        IntStream.range(0, entityCount)
            .mapToObj(
                i -> new TestdataPinnedAllowsUnassignedValuesListEntity("Generated Entity " + i))
            .toList();
    var valueList =
        IntStream.range(0, valueCount)
            .mapToObj(
                i -> new TestdataPinnedAllowsUnassignedValuesListValue("Generated Value " + i))
            .toList();
    var solution = new TestdataPinnedAllowsUnassignedValuesListSolution();
    solution.setValueList(valueList);
    solution.setEntityList(entityList);
    return solution;
  }

  private List<TestdataPinnedAllowsUnassignedValuesListValue> valueList;
  private List<TestdataPinnedAllowsUnassignedValuesListEntity> entityList;
  private SimpleScore score;

  @ValueRangeProvider(id = "valueRange")
  @PlanningEntityCollectionProperty
  public List<TestdataPinnedAllowsUnassignedValuesListValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataPinnedAllowsUnassignedValuesListValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataPinnedAllowsUnassignedValuesListEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataPinnedAllowsUnassignedValuesListEntity> entityList) {
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
