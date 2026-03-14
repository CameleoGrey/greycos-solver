package ai.greycos.solver.core.testcotwin.clone.customcloner;

import java.util.ArrayList;
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

@PlanningSolution(solutionCloner = TestdataCorrectlyClonedSolution.class)
public class TestdataCorrectlyClonedSolution
    implements SolutionCloner<TestdataCorrectlyClonedSolution> {

  private boolean clonedByCustomCloner = false;
  @PlanningScore private SimpleScore score;
  @PlanningEntityProperty private TestdataEntity entity = new TestdataEntity("A");

  private static final List<TestdataValue> STATIC_LIST =
      List.of(new TestdataValue("1"), new TestdataValue("2"));

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataValue> valueRange() {
    // two values needed to allow for at least one doable move, otherwise the second step ends in an
    // infinite loop
    return new ArrayList<>(STATIC_LIST);
  }

  @Override
  public @NonNull TestdataCorrectlyClonedSolution cloneSolution(
      @NonNull TestdataCorrectlyClonedSolution original) {
    TestdataCorrectlyClonedSolution clone = new TestdataCorrectlyClonedSolution();
    clone.clonedByCustomCloner = true;
    // score is immutable so no need to create a new instance
    clone.score = original.score;
    clone.entity.setValue(original.entity.getValue());
    return clone;
  }

  public boolean isClonedByCustomCloner() {
    return clonedByCustomCloner;
  }

  public void setClonedByCustomCloner(boolean clonedByCustomCloner) {
    this.clonedByCustomCloner = clonedByCustomCloner;
  }

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
}
