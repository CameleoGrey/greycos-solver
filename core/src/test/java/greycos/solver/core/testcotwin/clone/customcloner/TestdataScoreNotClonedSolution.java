package greycos.solver.core.testcotwin.clone.customcloner;

import java.util.Collections;
import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataValue;

import org.jspecify.annotations.NonNull;

@PlanningSolution(solutionCloner = TestdataScoreNotClonedSolution.class)
public class TestdataScoreNotClonedSolution
    implements SolutionCloner<TestdataScoreNotClonedSolution> {

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
    return Collections.singletonList(new TestdataValue("1"));
  }

  @Override
  public @NonNull TestdataScoreNotClonedSolution cloneSolution(
      @NonNull TestdataScoreNotClonedSolution original) {
    TestdataScoreNotClonedSolution clone = new TestdataScoreNotClonedSolution();
    clone.entity.setValue(original.entity.getValue());
    return clone;
  }
}
