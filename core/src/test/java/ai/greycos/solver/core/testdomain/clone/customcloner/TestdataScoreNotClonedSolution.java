package ai.greycos.solver.core.testdomain.clone.customcloner;

import java.util.Collections;
import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.cloner.SolutionCloner;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataValue;

import org.jspecify.annotations.NonNull;

@PlanningSolution(solutionCloner = TestdataScoreNotClonedSolution.class)
public class TestdataScoreNotClonedSolution
    implements SolutionCloner<TestdataScoreNotClonedSolution> {

  @PlanningScore private SimpleScore score;
  @PlanningEntityProperty private TestdataEntity entity = new TestdataEntity("A");

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
