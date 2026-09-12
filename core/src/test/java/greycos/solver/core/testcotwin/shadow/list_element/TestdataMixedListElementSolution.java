package greycos.solver.core.testcotwin.shadow.list_element;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataMixedListElementSolution {

  public static SolutionDescriptor<TestdataMixedListElementSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataMixedListElementSolution.class,
        TestdataMixedListElementEntity.class,
        TestdataMixedListElementValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataMixedListElementEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider List<TestdataMixedListElementValue> values;

  @PlanningScore SimpleScore score;

  @ValueRangeProvider
  public List<Integer> getDurationRange() {
    return List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
  }

  public List<TestdataMixedListElementEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataMixedListElementEntity> entities) {
    this.entities = entities;
  }

  public List<TestdataMixedListElementValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataMixedListElementValue> values) {
    this.values = values;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
