package greycos.solver.core.api.solver;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelectorFactory;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.util.MathUtils;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.cascade.single.TestdataSingleCascadingEntity;
import greycos.solver.core.testcotwin.cascade.single.TestdataSingleCascadingSolution;
import greycos.solver.core.testcotwin.composite.TestdataCompositeEntity;
import greycos.solver.core.testcotwin.composite.TestdataCompositeSolution;
import greycos.solver.core.testcotwin.constraintverifier.TestdataConstraintVerifierExtendedSolution;
import greycos.solver.core.testcotwin.constraintverifier.TestdataConstraintVerifierFirstEntity;
import greycos.solver.core.testcotwin.constraintverifier.TestdataConstraintVerifierSecondEntity;
import greycos.solver.core.testcotwin.equals.list.TestdataEqualsByCodeListEntity;
import greycos.solver.core.testcotwin.equals.list.TestdataEqualsByCodeListSolution;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.childtoo.TestdataBothAnnotatedChildEntity;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.childtoo.TestdataBothAnnotatedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.record.TestdataRecordEntity;
import greycos.solver.core.testcotwin.record.TestdataRecordSolution;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingSolution;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProblemSizeStatisticsTest {

  private static ProblemSizeStatistics getProblemSizeStatisticsFromCountLong(long scale) {
    return new ProblemSizeStatistics(
        0L, Collections.emptySortedMap(), 0L, 0L, Collections.emptySortedMap(), Math.log10(scale));
  }

  private static ProblemSizeStatistics getProblemSizeStatisticsFromDoubleLog(double scale) {
    return new ProblemSizeStatistics(
        0L, Collections.emptySortedMap(), 0L, 0L, Collections.emptySortedMap(), scale);
  }

  private static Locale defaultLocaleToRestore;

  @BeforeAll
  public static void setLocale() {
    defaultLocaleToRestore = Locale.getDefault();
    Locale.setDefault(Locale.US);
  }

  @AfterAll
  public static void restoreLocale() {
    Locale.setDefault(defaultLocaleToRestore);
    defaultLocaleToRestore = null;
  }

  @Test
  void getApproximateProblemScaleLogAsFixedPointLong() {
    var statistics = getProblemSizeStatisticsFromCountLong(100L);
    assertThat(statistics.approximateProblemScaleLogAsFixedPointLong())
        .isEqualTo(MathUtils.getScaledApproximateLog(MathUtils.LOG_PRECISION, 10L, 100L));

    statistics = getProblemSizeStatisticsFromCountLong(250L);
    assertThat(statistics.approximateProblemScaleLogAsFixedPointLong())
        .isEqualTo(MathUtils.getScaledApproximateLog(MathUtils.LOG_PRECISION, 10L, 250L));
  }

  @Test
  void formatApproximateProblemScale() {
    var statistics = getProblemSizeStatisticsFromCountLong(100L);
    assertThat(statistics.approximateProblemScaleAsFormattedString()).isEqualTo("100");

    statistics = getProblemSizeStatisticsFromCountLong(250L);
    assertThat(statistics.approximateProblemScaleAsFormattedString()).isEqualTo("250");

    statistics = getProblemSizeStatisticsFromCountLong(1_234_567L);
    assertThat(statistics.approximateProblemScaleAsFormattedString()).isEqualTo("1,234,567");

    statistics = getProblemSizeStatisticsFromCountLong(123_456_789L);
    assertThat(statistics.approximateProblemScaleAsFormattedString()).isEqualTo("123,456,789");

    statistics = getProblemSizeStatisticsFromCountLong(1_123_456_789L);
    assertThat(statistics.approximateProblemScaleAsFormattedString()).isEqualTo("1,123,456,789");

    statistics = getProblemSizeStatisticsFromCountLong(321_123_456_789L);
    assertThat(statistics.approximateProblemScaleAsFormattedString()).isEqualTo("3.211235 × 10^11");

    // scale = -infinity
    statistics = getProblemSizeStatisticsFromDoubleLog(Double.NEGATIVE_INFINITY);
    assertThat(statistics.approximateProblemScaleAsFormattedString()).isEqualTo("0");

    // scale = +infinity
    statistics = getProblemSizeStatisticsFromDoubleLog(Double.POSITIVE_INFINITY);
    assertThat(statistics.approximateProblemScaleAsFormattedString()).isEqualTo("0");

    // scale = NaN
    statistics = getProblemSizeStatisticsFromDoubleLog(Double.NaN);
    assertThat(statistics.approximateProblemScaleAsFormattedString()).isEqualTo("0");

    // scale = 0
    statistics = getProblemSizeStatisticsFromDoubleLog(0);
    assertThat(statistics.approximateProblemScaleAsFormattedString()).isEqualTo("1");
  }

  @Test
  void simpleSolutionEntityAndVariableCount() {
    var solution = TestdataSolution.generateSolution(5, 7);
    var valueRangeManager =
        ValueRangeManager.of(TestdataSolution.buildSolutionDescriptor(), solution);
    var statistics = valueRangeManager.getProblemSizeStatistics();

    assertThat(statistics.entityCount()).isEqualTo(7L);
    assertThat(statistics.variableCount()).isEqualTo(7L);
    assertThat(statistics.approximateValueCount()).isEqualTo(5L);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsExactlyEntriesOf(Map.of(TestdataEntity.class, 7L));
    assertThat(statistics.genuineEntityClassToVariableToValueCount().get(TestdataEntity.class))
        .containsEntry("value", 5L);
  }

  @Test
  void emptySolutionEntityAndVariableCount() {
    var solution = TestdataSolution.generateSolution(3, 0);
    var valueRangeManager =
        ValueRangeManager.of(TestdataSolution.buildSolutionDescriptor(), solution);
    var statistics = valueRangeManager.getProblemSizeStatistics();

    assertThat(statistics.entityCount()).isEqualTo(0L);
    assertThat(statistics.variableCount()).isEqualTo(0L);
    assertThat(statistics.approximateValueCount()).isEqualTo(3L);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsExactlyEntriesOf(Map.of(TestdataEntity.class, 0L));
    assertThat(statistics.genuineEntityClassToVariableToValueCount().get(TestdataEntity.class))
        .containsEntry("value", 3L);
  }

  @Test
  void compositeValueRangesEntityAndVariableCount() {
    var solution = TestdataCompositeSolution.generateSolution(4, 6);
    var valueRangeManager =
        ValueRangeManager.of(TestdataCompositeSolution.buildSolutionDescriptor(), solution);
    var statistics = valueRangeManager.getProblemSizeStatistics();

    assertThat(statistics.entityCount()).isEqualTo(6L);
    assertThat(statistics.variableCount()).isEqualTo(6L);
    assertThat(statistics.approximateValueCount()).isEqualTo(8L);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsExactlyEntriesOf(Map.of(TestdataCompositeEntity.class, 6L));
    assertThat(
            statistics
                .genuineEntityClassToVariableToValueCount()
                .get(TestdataCompositeEntity.class))
        .containsEntry("value", 8L);
  }

  @Test
  void multipleEntityTypesEntityAndVariableCount() {
    var solution = TestdataConstraintVerifierExtendedSolution.generateSolution(5, 8);
    var valueRangeManager =
        ValueRangeManager.of(
            SolutionDescriptor.buildSolutionDescriptor(
                TestdataConstraintVerifierExtendedSolution.class,
                TestdataConstraintVerifierFirstEntity.class,
                TestdataConstraintVerifierSecondEntity.class),
            solution);
    var statistics = valueRangeManager.getProblemSizeStatistics();

    var firstEntityCount = solution.getEntityList().size();
    var secondEntityCount = solution.getSecondEntityList().size();

    assertThat(statistics.entityCount()).isEqualTo(firstEntityCount + secondEntityCount);
    assertThat(statistics.variableCount()).isEqualTo(firstEntityCount + secondEntityCount);
    assertThat(statistics.approximateValueCount()).isEqualTo(10L);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsEntry(TestdataConstraintVerifierFirstEntity.class, (long) firstEntityCount);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsEntry(TestdataConstraintVerifierSecondEntity.class, (long) secondEntityCount);
    assertThat(
            statistics
                .genuineEntityClassToVariableToValueCount()
                .get(TestdataConstraintVerifierFirstEntity.class))
        .containsEntry("value", 5L);
    assertThat(
            statistics
                .genuineEntityClassToVariableToValueCount()
                .get(TestdataConstraintVerifierSecondEntity.class))
        .containsEntry("value", 5L);
  }

  @Test
  void listVariableEntityAndVariableCount() {
    var solution = TestdataEqualsByCodeListSolution.generateSolution(4, 6);
    var valueRangeManager =
        ValueRangeManager.of(TestdataEqualsByCodeListSolution.buildSolutionDescriptor(), solution);
    var statistics = valueRangeManager.getProblemSizeStatistics();

    assertThat(statistics.entityCount()).isEqualTo(6L);
    assertThat(statistics.variableCount()).isEqualTo(6L);
    assertThat(statistics.approximateValueCount()).isEqualTo(4L);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsExactlyEntriesOf(Map.of(TestdataEqualsByCodeListEntity.class, 6L));
    assertThat(
            statistics
                .genuineEntityClassToVariableToValueCount()
                .get(TestdataEqualsByCodeListEntity.class))
        .containsEntry("valueList", 4L);
  }

  @Test
  void cascadingListVariableEntityAndVariableCount() {
    var solution = TestdataSingleCascadingSolution.generateUninitializedSolution(3, 5);
    var valueRangeManager =
        ValueRangeManager.of(TestdataSingleCascadingSolution.buildSolutionDescriptor(), solution);
    var statistics = valueRangeManager.getProblemSizeStatistics();

    assertThat(statistics.entityCount()).isEqualTo(5L);
    assertThat(statistics.variableCount()).isEqualTo(5L);
    assertThat(statistics.approximateValueCount()).isEqualTo(3L);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsExactlyEntriesOf(Map.of(TestdataSingleCascadingEntity.class, 5L));
    assertThat(
            statistics
                .genuineEntityClassToVariableToValueCount()
                .get(TestdataSingleCascadingEntity.class))
        .containsEntry("valueList", 3L);
  }

  @Test
  void recordEntityAndVariableCount() {
    var solution = TestdataRecordSolution.generateSolution(4, 9);
    var valueRangeManager =
        ValueRangeManager.of(TestdataRecordSolution.buildSolutionDescriptor(), solution);
    var statistics = valueRangeManager.getProblemSizeStatistics();

    assertThat(statistics.entityCount()).isEqualTo(9L);
    assertThat(statistics.variableCount()).isEqualTo(9L);
    assertThat(statistics.approximateValueCount()).isEqualTo(4L);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsExactlyEntriesOf(Map.of(TestdataRecordEntity.class, 9L));
    assertThat(
            statistics.genuineEntityClassToVariableToValueCount().get(TestdataRecordEntity.class))
        .containsEntry("value", 4L);
  }

  @Test
  void uninitializedSolutionEntityAndVariableCount() {
    var solution = TestdataSolution.generateUninitializedSolution(6, 10);
    var valueRangeManager =
        ValueRangeManager.of(TestdataSolution.buildSolutionDescriptor(), solution);
    var statistics = valueRangeManager.getProblemSizeStatistics();

    assertThat(statistics.entityCount()).isEqualTo(10L);
    assertThat(statistics.variableCount()).isEqualTo(10L);
    assertThat(statistics.approximateValueCount()).isEqualTo(6L);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsExactlyEntriesOf(Map.of(TestdataEntity.class, 10L));
    assertThat(statistics.genuineEntityClassToVariableToValueCount().get(TestdataEntity.class))
        .containsEntry("value", 6L);
  }

  @Test
  void singleEntityAndValue() {
    var solution = TestdataSolution.generateSolution(1, 1);
    var valueRangeManager =
        ValueRangeManager.of(TestdataSolution.buildSolutionDescriptor(), solution);
    var statistics = valueRangeManager.getProblemSizeStatistics();

    assertThat(statistics.entityCount()).isEqualTo(1L);
    assertThat(statistics.variableCount()).isEqualTo(1L);
    assertThat(statistics.approximateValueCount()).isEqualTo(1L);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsExactlyEntriesOf(Map.of(TestdataEntity.class, 1L));
    assertThat(statistics.genuineEntityClassToVariableToValueCount().get(TestdataEntity.class))
        .containsEntry("value", 1L);
  }

  @Test
  void legacyConstructorKeepsAggregateStatistics() {
    var statistics = new ProblemSizeStatistics(2, 3, 5, 7.0);
    assertThat(statistics.entityCount()).isEqualTo(2);
    assertThat(statistics.variableCount()).isEqualTo(3);
    assertThat(statistics.approximateValueCount()).isEqualTo(5);
    assertThat(statistics.approximateProblemSizeLog()).isEqualTo(7.0);
    assertThat(statistics.genuineEntityClassToEntityCount()).isEmpty();
    assertThat(statistics.genuineEntityClassToVariableToValueCount()).isEmpty();
  }

  @Test
  void inheritedVariablesAreCountedOnceAndReportedOnEveryEffectiveEntityClass() {
    var solution = TestdataBothAnnotatedSolution.generateSolution(5, 7, true);
    solution.setSubValueList(solution.getSubValueList().subList(0, 3));
    solution
        .getEntityList()
        .forEach(entity -> entity.setSubValue(solution.getSubValueList().getFirst()));
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            TestdataBothAnnotatedSolution.class,
            TestdataEntity.class,
            TestdataBothAnnotatedChildEntity.class);
    var statistics = ValueRangeManager.of(descriptor, solution).getProblemSizeStatistics();
    assertThat(statistics.entityCount()).isEqualTo(7);
    assertThat(statistics.variableCount()).isEqualTo(14);
    assertThat(statistics.approximateValueCount()).isEqualTo(8);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(TestdataEntity.class, 0L, TestdataBothAnnotatedChildEntity.class, 7L));
    assertThat(statistics.genuineEntityClassToVariableToValueCount().get(TestdataEntity.class))
        .containsExactlyEntriesOf(Map.of("value", 5L));
    assertThat(
            statistics
                .genuineEntityClassToVariableToValueCount()
                .get(TestdataBothAnnotatedChildEntity.class))
        .containsExactlyInAnyOrderEntriesOf(Map.of("value", 5L, "subValue", 3L));
    assertThat(descriptor.getBasicVariableDescriptorList()).hasSize(2);
  }

  @Test
  void entityRangesAreSummedAndRefreshedAfterProblemChange() {
    var solution = TestdataEntityProvidingSolution.generateSolution();
    var manager =
        ValueRangeManager.of(TestdataEntityProvidingSolution.buildSolutionDescriptor(), solution);
    var statistics = manager.getProblemSizeStatistics();
    assertThat(statistics.approximateValueCount()).isEqualTo(4);
    assertThat(
            statistics
                .genuineEntityClassToVariableToValueCount()
                .get(TestdataEntityProvidingEntity.class))
        .containsExactlyEntriesOf(Map.of("value", 4L));
    var first = solution.getEntityList().getFirst();
    first.setValueRange(List.of(first.getValueRange().getFirst()));
    manager.reset(solution);
    var updated = manager.getProblemSizeStatistics();
    assertThat(updated.approximateValueCount()).isEqualTo(3);
    assertThat(
            updated
                .genuineEntityClassToVariableToValueCount()
                .get(TestdataEntityProvidingEntity.class))
        .containsExactlyEntriesOf(Map.of("value", 3L));
    assertThat(
            statistics
                .genuineEntityClassToVariableToValueCount()
                .get(TestdataEntityProvidingEntity.class))
        .containsExactlyEntriesOf(Map.of("value", 4L));
  }

  @Test
  void mixedVariableCountsIncludePinnedEntities() {
    var solution = TestdataMixedSolution.generateUninitializedSolution(3, 5, 7);
    solution.getEntityList().getFirst().setPinned(true);
    var statistics =
        ValueRangeManager.of(TestdataMixedSolution.buildSolutionDescriptor(), solution)
            .getProblemSizeStatistics();
    assertThat(statistics.entityCount()).isEqualTo(3);
    assertThat(statistics.variableCount()).isEqualTo(9);
    assertThat(statistics.approximateValueCount()).isEqualTo(19);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsExactlyEntriesOf(Map.of(TestdataMixedEntity.class, 3L));
    assertThat(statistics.genuineEntityClassToVariableToValueCount().get(TestdataMixedEntity.class))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("basicValue", 7L, "secondBasicValue", 7L, "valueList", 5L));
  }

  @PlanningEntity
  public static class ListCountEntity {
    @PlanningListVariable(valueRangeProviderRefs = "values")
    public List<String> valueList = new java.util.ArrayList<>();
  }

  @PlanningEntity
  public static class InheritedListEntity extends ListCountEntity {}

  @PlanningSolution
  public static class ListCountSolution {
    @ValueRangeProvider(id = "values")
    @ProblemFactCollectionProperty
    public List<String> values = List.of("a", "b", "c", "d");

    @PlanningEntityCollectionProperty
    public List<ListCountEntity> entities = List.of(new InheritedListEntity());

    @PlanningScore public SimpleScore score;
  }

  @Test
  void inheritedListUsesOneGlobalVariableDescriptor() {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            ListCountSolution.class, ListCountEntity.class, InheritedListEntity.class);
    var statistics =
        ValueRangeManager.of(descriptor, new ListCountSolution()).getProblemSizeStatistics();
    assertThat(statistics.entityCount()).isEqualTo(1);
    assertThat(statistics.variableCount()).isEqualTo(1);
    assertThat(statistics.approximateValueCount()).isEqualTo(4);
    assertThat(statistics.genuineEntityClassToEntityCount())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(ListCountEntity.class, 0L, InheritedListEntity.class, 1L));
    assertThat(statistics.genuineEntityClassToVariableToValueCount().get(InheritedListEntity.class))
        .containsExactlyEntriesOf(Map.of("valueList", 4L));
  }

  @Test
  void inheritedOnlyEntityDoesNotMakeDefaultEntitySelectorAmbiguous() {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            ListCountSolution.class, ListCountEntity.class, InheritedListEntity.class);
    var selector =
        EntitySelectorFactory.<ListCountSolution>create(new EntitySelectorConfig())
            .buildEntitySelector(
                buildHeuristicConfigPolicy(descriptor),
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.ORIGINAL);
    assertThat(selector.getEntityDescriptor().getEntityClass()).isEqualTo(ListCountEntity.class);
    assertThat(descriptor.getGenuineEntityDescriptors())
        .containsExactly(descriptor.findEntityDescriptorOrFail(ListCountEntity.class));
  }
}
