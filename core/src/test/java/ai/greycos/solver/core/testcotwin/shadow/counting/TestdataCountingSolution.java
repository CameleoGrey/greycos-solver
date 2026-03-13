package ai.greycos.solver.core.testcotwin.shadow.counting;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataCountingSolution extends TestdataObject {
  public static SolutionDescriptor<TestdataCountingSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataCountingSolution.class, TestdataCountingEntity.class, TestdataCountingValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataCountingEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider List<TestdataCountingValue> values;

  @PlanningScore SimpleScore score;

  public TestdataCountingSolution() {}

  public TestdataCountingSolution(
      String code, List<TestdataCountingEntity> entities, List<TestdataCountingValue> values) {
    super(code);
    this.entities = entities;
    this.values = values;
  }

  public List<TestdataCountingEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataCountingEntity> entities) {
    this.entities = entities;
  }

  public List<TestdataCountingValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataCountingValue> values) {
    this.values = values;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
