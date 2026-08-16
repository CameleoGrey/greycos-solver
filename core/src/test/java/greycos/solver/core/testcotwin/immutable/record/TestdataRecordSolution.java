package greycos.solver.core.testcotwin.immutable.record;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

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
