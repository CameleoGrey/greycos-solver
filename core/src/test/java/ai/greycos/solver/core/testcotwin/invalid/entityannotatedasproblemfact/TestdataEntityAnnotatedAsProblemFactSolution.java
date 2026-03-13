package ai.greycos.solver.core.testcotwin.invalid.entityannotatedasproblemfact;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataEntityAnnotatedAsProblemFactSolution {
  TestdataEntity entity;
  List<TestdataValue> values;
  SimpleScore score;

  public static SolutionDescriptor<TestdataEntityAnnotatedAsProblemFactSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataEntityAnnotatedAsProblemFactSolution.class, TestdataEntity.class);
  }

  public TestdataEntityAnnotatedAsProblemFactSolution() {}

  @PlanningEntityProperty
  public TestdataEntity getEntity() {
    return entity;
  }

  @ProblemFactProperty
  public TestdataEntity getEntityAsFact() {
    return entity;
  }

  public void setEntity(TestdataEntity entity) {
    this.entity = entity;
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
