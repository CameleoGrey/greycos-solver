package greycos.solver.core.testcotwin.shadow.shared_source;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataSharedSourceSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataSharedSourceSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataSharedSourceSolution.class, TestdataSharedSourceEntity.class);
  }

  public static PlanningSolutionMetaModel<TestdataSharedSourceSolution> buildMetaModel() {
    return buildSolutionDescriptor().getMetaModel();
  }

  @PlanningEntityCollectionProperty List<TestdataSharedSourceEntity> entities;

  @ValueRangeProvider List<TestdataSharedSourceValue> values;

  @PlanningScore SimpleScore score;

  public TestdataSharedSourceSolution() {}

  public TestdataSharedSourceSolution(
      String code,
      List<TestdataSharedSourceEntity> entities,
      List<TestdataSharedSourceValue> values) {
    super(code);
    this.entities = entities;
    this.values = values;
  }

  public List<TestdataSharedSourceEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataSharedSourceEntity> entities) {
    this.entities = entities;
  }

  public List<TestdataSharedSourceValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataSharedSourceValue> values) {
    this.values = values;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
