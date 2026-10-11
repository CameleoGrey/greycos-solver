package greycos.solver.core.impl.score.director;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

class ValueRangeStatisticsTest {

  @Test
  void multipleFieldsAggregateAssignmentsAndBasicVariables() {
    var solution = TestdataMultipleListSolution.generateUninitializedSolution(3, 2, 2);
    solution.getEntityList().get(0).getFirstValueList().add(solution.getFirstValueList().get(0));
    solution.getEntityList().get(1).getFirstValueList().add(solution.getFirstValueList().get(1));
    solution.getEntityList().get(0).getSecondValueList().add(solution.getSecondValueList().get(0));
    var manager =
        ValueRangeManager.of(TestdataMultipleListSolution.buildSolutionDescriptor(), solution);

    var visited = new ArrayList<>();
    assertThat(manager.getInitializationStatistics(visited::add))
        .isEqualTo(new SolutionInitializationStatistics(4, 3, 4, 4, 2, 2));
    assertThat(visited).hasSize(7);
    var statistics = manager.getProblemSizeStatistics();
    assertThat(statistics.entityCount()).isEqualTo(4);
    assertThat(statistics.variableCount()).isEqualTo(8);
    assertThat(statistics.approximateValueCount()).isEqualTo(13);
    assertThat(
            statistics
                .genuineEntityClassToVariableToValueCount()
                .get(TestdataMultipleListEntity.class))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("firstValueList", 3L, "secondValueList", 2L, "basicValue", 4L));
    assertThat(
            statistics
                .genuineEntityClassToVariableToValueCount()
                .get(TestdataMultipleListSecondValue.class))
        .containsExactlyEntriesOf(Map.of("quantity", 4L));
    // The first list has 3! * 4 = 24 ordered allocations over two owners; the second
    // has 2! * 3 = 6. Four basic variables independently have four possible values each.
    assertThat(statistics.approximateProblemSizeLog())
        .isCloseTo(Math.log10(24 * 6 * 4 * 4 * 4 * 4), Percentage.withPercentage(1.0));

    solution.getEntityList().get(1).getFirstValueList().add(solution.getFirstValueList().get(2));
    solution.getEntityList().get(1).getSecondValueList().add(solution.getSecondValueList().get(1));
    assertThat(manager.computeInitializationStatistics(solution, null).unassignedValueCount())
        .isZero();
    assertThat(manager.computeInitializationStatistics(solution, null).notInAnyListValueCount())
        .isZero();
  }

  @Test
  void inheritedEntityRangesKeepIndependentPoliciesAndEffectiveVariableIndexes() {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            InheritedListsSolution.class, RequiredOwner.class, OptionalOwner.class);
    var solution = new InheritedListsSolution();
    var requiredOwner = new RequiredOwner();
    requiredOwner.requiredRange = List.of("a", "b", "c");
    requiredOwner.requiredList.add("a");
    var optionalOwner = new OptionalOwner();
    optionalOwner.requiredRange = List.of("b", "c");
    optionalOwner.requiredList.add("b");
    optionalOwner.optionalRange = List.of(1, 2);
    optionalOwner.optionalList.add(1);
    optionalOwner.basicValue = 7L;
    solution.owners = List.of(requiredOwner, optionalOwner);
    var manager = ValueRangeManager.of(descriptor, solution);

    assertThat(manager.getInitializationStatistics())
        .isEqualTo(new SolutionInitializationStatistics(2, 0, 0, 0, 1, 2));
    var statistics = manager.getProblemSizeStatistics();
    assertThat(statistics.entityCount()).isEqualTo(2);
    assertThat(statistics.variableCount()).isEqualTo(4);
    assertThat(statistics.approximateValueCount()).isEqualTo(9);
    assertThat(statistics.genuineEntityClassToVariableToValueCount().get(RequiredOwner.class))
        .containsExactlyEntriesOf(Map.of("requiredList", 3L));
    assertThat(statistics.genuineEntityClassToVariableToValueCount().get(OptionalOwner.class))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("requiredList", 2L, "optionalList", 2L, "basicValue", 2L));
    // Required allocations: 24, with "a" reachable on only one of two owners, giving 12.
    // Optional-list estimation adds a virtual owner, giving 6; the basic variable gives 2.
    assertThat(statistics.approximateProblemSizeLog())
        .isCloseTo(Math.log10(12 * 6 * 2), Percentage.withPercentage(1.0));

    optionalOwner.requiredList.add("c");
    assertThat(manager.computeInitializationStatistics(solution, null))
        .isEqualTo(new SolutionInitializationStatistics(2, 0, 0, 0, 0, 1));
  }

  @Test
  void emptyListRangesStillCountIndependentBasicVariables() {
    var solution = TestdataMultipleListSolution.generateUninitializedSolution(0, 0, 2);
    var manager =
        ValueRangeManager.of(TestdataMultipleListSolution.buildSolutionDescriptor(), solution);

    assertThat(manager.getInitializationStatistics())
        .isEqualTo(new SolutionInitializationStatistics(2, 0, 2, 2, 0, 0));
    assertThat(manager.getProblemSizeStatistics().approximateValueCount()).isEqualTo(8);
    assertThat(manager.getProblemSizeStatistics().approximateProblemSizeLog())
        .isCloseTo(Math.log10(4 * 4), Percentage.withPercentage(1.0));
  }

  @PlanningEntity
  public static class RequiredOwner {
    @ValueRangeProvider(id = "requiredRange")
    public List<String> requiredRange = List.of();

    @PlanningListVariable(valueRangeProviderRefs = "requiredRange")
    public List<String> requiredList = new ArrayList<>();
  }

  @PlanningEntity
  public static class OptionalOwner extends RequiredOwner {
    @ValueRangeProvider(id = "optionalRange")
    public List<Integer> optionalRange = List.of();

    @PlanningListVariable(valueRangeProviderRefs = "optionalRange", allowsUnassignedValues = true)
    public List<Integer> optionalList = new ArrayList<>();

    @PlanningVariable(valueRangeProviderRefs = "basicRange")
    public Long basicValue;
  }

  @PlanningSolution
  public static class InheritedListsSolution {
    @PlanningEntityCollectionProperty public List<RequiredOwner> owners = List.of();

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "basicRange")
    public List<Long> basicRange = List.of(7L, 8L);

    @PlanningScore public SimpleScore score;
  }
}
