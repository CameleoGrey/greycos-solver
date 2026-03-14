package ai.greycos.solver.core.testcotwin.clone.customcloner;

import java.util.Collections;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataValue;

import org.jspecify.annotations.NonNull;

@PlanningSolution(solutionCloner = TestdataEntitiesNotClonedSolution.class)
public class TestdataEntitiesNotClonedSolution
    implements SolutionCloner<TestdataEntitiesNotClonedSolution> {

  @PlanningScore private SimpleScore score;
  @PlanningEntityProperty private TestdataEntity entity = new TestdataEntity("A");

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }

  public TestdataEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataEntity entity) {
    this.entity = entity;
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataValue> valueRange() {
    return Collections.emptyList();
  }

  @Override
  public @NonNull TestdataEntitiesNotClonedSolution cloneSolution(
      @NonNull TestdataEntitiesNotClonedSolution original) {
    TestdataEntitiesNotClonedSolution clone = new TestdataEntitiesNotClonedSolution();
    clone.entity = original.entity;
    clone.score = original.score;
    return clone;
  }
}
