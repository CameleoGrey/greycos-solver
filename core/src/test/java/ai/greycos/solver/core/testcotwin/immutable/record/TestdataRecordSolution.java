package ai.greycos.solver.core.testcotwin.immutable.record;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

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
