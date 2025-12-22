package ai.greycos.solver.core.testdomain.immutable.record;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public record TestdataRecordSolution(
    @ValueRangeProvider(id = "valueRange") @ProblemFactCollectionProperty
        List<TestdataRecordValue> valueList,
    @PlanningEntityCollectionProperty List<TestdataRecordEntity> entityList,
    @PlanningScore SimpleScore score) {

  public static SolutionDescriptor<TestdataRecordSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataRecordSolution.class, TestdataRecordEntity.class);
  }
}
