package greycos.solver.core.testcotwin.invalid.entityannotatedasproblemfact;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataEntityAnnotatedAsProblemFactCollectionSolution {
  List<TestdataEntity> entities;
  List<TestdataValue> values;
  SimpleScore score;

  public static SolutionDescriptor<TestdataEntityAnnotatedAsProblemFactCollectionSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataEntityAnnotatedAsProblemFactCollectionSolution.class, TestdataEntity.class);
  }

  public TestdataEntityAnnotatedAsProblemFactCollectionSolution() {}

  @ProblemFactCollectionProperty
  public List<TestdataEntity> getEntitiesAsFacts() {
    return entities;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataEntity> entities) {
    this.entities = entities;
  }

  @ValueRangeProvider(id = "valueRange")
  public List<TestdataValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataValue> values) {
    this.values = values;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
