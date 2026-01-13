package ai.greycos.solver.core.testcotwin.clone.customcloner;

import java.util.Collections;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataValue;

import org.jspecify.annotations.NonNull;

@PlanningSolution(solutionCloner = TestdataScoreNotEqualSolution.class)
public class TestdataScoreNotEqualSolution
    implements SolutionCloner<TestdataScoreNotEqualSolution> {

  @PlanningScore private SimpleScore score;
  @PlanningEntityProperty private TestdataEntity entity = new TestdataEntity("A");

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataValue> valueRange() {
    return Collections.emptyList();
  }

  @Override
  public @NonNull TestdataScoreNotEqualSolution cloneSolution(
      @NonNull TestdataScoreNotEqualSolution original) {
    var clone = new TestdataScoreNotEqualSolution();
    clone.entity.setValue(original.entity.getValue());
    if (original.score != null) {
      clone.score = SimpleScore.of(original.score.score() - 1);
    } else {
      clone.score = SimpleScore.ZERO;
    }
    if (clone.score.equals(original.score)) {
      throw new IllegalStateException(
          "The cloned score should be intentionally unequal to the original score");
    }
    return clone;
  }
}
