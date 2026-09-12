package greycos.solver.core.testcotwin.shadow.list_element;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;

@PlanningSolution
public class TestdataListElementSolution {

  public static SolutionDescriptor<TestdataListElementSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataListElementSolution.class,
        TestdataListElementEntity.class,
        TestdataListElementValue.class);
  }

  public static PlanningSolutionMetaModel<TestdataListElementSolution> buildMetaModel() {
    return buildSolutionDescriptor().getMetaModel();
  }

  public static TestdataListElementSolution generateSolution(int entityCount, int valueCount) {
    var solution = new TestdataListElementSolution();
    var entities = new ArrayList<TestdataListElementEntity>(entityCount);
    for (var i = 0; i < entityCount; i++) {
      entities.add(new TestdataListElementEntity("e" + i, i));
    }
    var values = new ArrayList<TestdataListElementValue>(valueCount);
    for (var i = 0; i < valueCount; i++) {
      values.add(new TestdataListElementValue("v" + i, 1 + (i % 3)));
    }
    solution.setEntities(entities);
    solution.setValues(values);
    return solution;
  }

  @PlanningEntityCollectionProperty List<TestdataListElementEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider List<TestdataListElementValue> values;

  @PlanningScore SimpleScore score;

  public List<TestdataListElementEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataListElementEntity> entities) {
    this.entities = entities;
  }

  public List<TestdataListElementValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataListElementValue> values) {
    this.values = values;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
