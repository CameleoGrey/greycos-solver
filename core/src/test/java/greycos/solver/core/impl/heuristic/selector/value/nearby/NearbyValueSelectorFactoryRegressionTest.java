package greycos.solver.core.impl.heuristic.selector.value.nearby;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.Selector;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyTestUtils;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicRecordingEntitySelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.TestdataListVarEasyScoreCalculator;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingSolution;
import greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises real descriptors and factory-built child iterators, including the list move paths. */
@Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class NearbyValueSelectorFactoryRegressionTest {

  @ParameterizedTest
  @EnumSource(
      value = SelectionOrder.class,
      names = {"ORIGINAL", "RANDOM"})
  void basicValuesAreSortedAndRandomEnumerationTerminates(SelectionOrder order) {
    var policy = buildHeuristicConfigPolicy();
    var descriptor =
        policy.getSolutionDescriptor().findEntityDescriptorOrFail(TestdataEntity.class);
    var entity = new TestdataEntity("origin");
    var far = new TestdataValue("far");
    var near = new TestdataValue("near");
    var solution = new TestdataSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(far, near));
    var origin =
        new MimicRecordingEntitySelector<>(
            SelectorTestUtils.mockEntitySelector(descriptor, entity));
    policy.addEntityMimicRecorder("origin", origin);
    var nearbyConfig = entityNearbyConfig();
    if (order == SelectionOrder.RANDOM) {
      nearbyConfig.withLinearDistributionSizeMaximum(1);
    }
    var selector =
        ValueSelectorFactory.<TestdataSolution>create(
                new ValueSelectorConfig()
                    .withVariableName("value")
                    .withNearbySelectionConfig(nearbyConfig))
            .buildValueSelector(policy, descriptor, SelectionCacheType.JUST_IN_TIME, order);
    try (var lifecycle = start(policy, solution, selector)) {
      origin.iterator().next();
      var iterator = selector.iterator(entity);
      assertThat(iterator.hasNext()).isTrue();
      assertThat(iterator.next()).isSameAs(near);
      if (order == SelectionOrder.ORIGINAL) {
        assertThat(iterator.next()).isSameAs(far);
        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
      } else {
        assertThat(iterator.next()).isSameAs(near);
      }
      // Ending enumeration remains finite even when ordinary selection is random.
      assertThat(toList(selector.endingIterator(entity))).containsExactly(far, near);
    }
  }

  @Test
  void valueOriginRetainsTheSelectingEntitysRange() {
    var policy =
        buildHeuristicConfigPolicy(TestdataEntityProvidingSolution.buildSolutionDescriptor());
    var descriptor =
        policy
            .getSolutionDescriptor()
            .findEntityDescriptorOrFail(TestdataEntityProvidingEntity.class);
    var far = new TestdataValue("far");
    var near = new TestdataValue("near");
    var left = new TestdataEntityProvidingEntity("left", List.of(far));
    var right = new TestdataEntityProvidingEntity("right", List.of(near));
    var solution = new TestdataEntityProvidingSolution();
    solution.setEntityList(List.of(left, right));
    var origin =
        (IterableValueSelector<TestdataEntityProvidingSolution>)
            ValueSelectorFactory.<TestdataEntityProvidingSolution>create(
                    new ValueSelectorConfig().withVariableName("value").withId("origin"))
                .buildValueSelector(
                    policy, descriptor, SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL);
    var selector =
        ValueSelectorFactory.<TestdataEntityProvidingSolution>create(
                new ValueSelectorConfig()
                    .withVariableName("value")
                    .withNearbySelectionConfig(
                        new NearbySelectionConfig()
                            .withOriginValueSelectorConfig(
                                new ValueSelectorConfig().withMimicSelectorRef("origin"))
                            .withNearbyDistanceMeterClass(BasicDistanceMeter.class)))
            .buildValueSelector(
                policy, descriptor, SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL);
    try (var lifecycle = start(policy, solution, origin, selector)) {
      origin.iterator(left).next();
      assertThat(toList(selector.iterator(left))).containsExactly(far);
      // The origin is still far; the second row must use right's range, not far or left's range.
      assertThat(toList(selector.iterator(right))).containsExactly(near);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = SelectionOrder.class,
      names = {"ORIGINAL", "RANDOM"})
  void emptyBasicRangesHaveNoNext(SelectionOrder order) {
    var policy = buildHeuristicConfigPolicy();
    var descriptor =
        policy.getSolutionDescriptor().findEntityDescriptorOrFail(TestdataEntity.class);
    var entity = new TestdataEntity("origin");
    var solution = new TestdataSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of());
    var origin =
        new MimicRecordingEntitySelector<>(
            SelectorTestUtils.mockEntitySelector(descriptor, entity));
    policy.addEntityMimicRecorder("origin", origin);
    var selector =
        ValueSelectorFactory.<TestdataSolution>create(
                new ValueSelectorConfig()
                    .withVariableName("value")
                    .withNearbySelectionConfig(entityNearbyConfig()))
            .buildValueSelector(policy, descriptor, SelectionCacheType.JUST_IN_TIME, order);
    try (var lifecycle = start(policy, solution, selector)) {
      origin.iterator().next();
      var iterator = selector.iterator(entity);
      assertThat(iterator.hasNext()).isFalse();
      assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = SelectionOrder.class,
      names = {"ORIGINAL", "RANDOM"})
  void nullableBasicRangeKeepsUnassignment(SelectionOrder order) {
    var policy =
        buildHeuristicConfigPolicy(
            TestdataAllowsUnassignedEntityProvidingSolution.buildSolutionDescriptor());
    var descriptor =
        policy
            .getSolutionDescriptor()
            .findEntityDescriptorOrFail(TestdataAllowsUnassignedEntityProvidingEntity.class);
    var entity = new TestdataAllowsUnassignedEntityProvidingEntity("origin", List.of());
    var solution = new TestdataAllowsUnassignedEntityProvidingSolution();
    solution.setEntityList(List.of(entity));
    var origin =
        new MimicRecordingEntitySelector<>(
            SelectorTestUtils.mockEntitySelector(descriptor, entity));
    policy.addEntityMimicRecorder("origin", origin);
    var selector =
        ValueSelectorFactory.<TestdataAllowsUnassignedEntityProvidingSolution>create(
                new ValueSelectorConfig()
                    .withVariableName("value")
                    .withNearbySelectionConfig(entityNearbyConfig()))
            .buildValueSelector(policy, descriptor, SelectionCacheType.JUST_IN_TIME, order);
    try (var lifecycle = start(policy, solution, selector)) {
      origin.iterator().next();
      var iterator = selector.iterator(entity);
      assertThat(iterator.hasNext()).isTrue();
      assertThat(iterator.next()).isNull();
      assertThat(iterator.hasNext()).isEqualTo(order == SelectionOrder.RANDOM);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void listMovesUseNearbyValueSelection(boolean kOpt) {
    CountingListDistanceMeter.calls.set(0);
    var originConfig = new ValueSelectorConfig().withVariableName("valueList").withId("origin");
    var nearbyConfig =
        new ValueSelectorConfig()
            .withVariableName("valueList")
            .withNearbySelectionConfig(
                new NearbySelectionConfig()
                    .withOriginValueSelectorConfig(
                        new ValueSelectorConfig().withMimicSelectorRef("origin"))
                    .withNearbyDistanceMeterClass(CountingListDistanceMeter.class));
    MoveSelectorConfig<?> moveConfig =
        kOpt
            ? new KOptListMoveSelectorConfig()
                .withMinimumK(2)
                .withMaximumK(2)
                .withOriginSelectorConfig(originConfig)
                .withValueSelectorConfig(nearbyConfig)
            : new ListSwapMoveSelectorConfig()
                .withValueSelectorConfig(originConfig)
                .withSecondaryValueSelectorConfig(nearbyConfig);
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataListSolution.class)
            .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
            .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
            .withEasyScoreCalculatorClass(TestdataListVarEasyScoreCalculator.class)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(moveConfig)
                    .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1))
                    .withTerminationConfig(
                        new TerminationConfig()
                            .withStepCountLimit(3)
                            .withSpentLimit(Duration.ofSeconds(2))));
    var solution = TestdataListSolution.generateInitializedSolution(8, 1);
    var result = SolverFactory.<TestdataListSolution>create(config).buildSolver().solve(solution);
    assertThat(CountingListDistanceMeter.calls.get()).isPositive();
    assertThat(result.getEntityList().getFirst().getValueList()).hasSize(8).doesNotHaveDuplicates();
  }

  private static NearbySelectionConfig entityNearbyConfig() {
    return new NearbySelectionConfig()
        .withOriginEntitySelectorConfig(new EntitySelectorConfig().withMimicSelectorRef("origin"))
        .withNearbyDistanceMeterClass(BasicDistanceMeter.class);
  }

  private static List<Object> toList(Iterator<Object> iterator) {
    var values = new ArrayList<Object>();
    iterator.forEachRemaining(values::add);
    return values;
  }

  @SafeVarargs
  private static <Solution_> Lifecycle<Solution_> start(
      HeuristicConfigPolicy<Solution_> policy,
      Solution_ solution,
      Selector<Solution_>... selectors) {
    InnerScoreDirector<Solution_, ?> director = mock(InnerScoreDirector.class);
    NearbyTestUtils.mockSupplyManager(director, null);
    when(director.getWorkingSolution()).thenReturn(solution);
    doReturn(ValueRangeManager.of(policy.getSolutionDescriptor(), solution))
        .when(director)
        .getValueRangeManager();
    var scope = SelectorTestUtils.solvingStarted(selectors[0], director, new Random(0));
    for (int i = 1; i < selectors.length; i++) {
      selectors[i].solvingStarted(scope);
    }
    var phase = PlannerTestUtils.delegatingPhaseScope(scope);
    for (var selector : selectors) {
      selector.phaseStarted(phase);
    }
    return new Lifecycle<>(scope, phase, selectors);
  }

  private record Lifecycle<Solution_>(
      SolverScope<Solution_> scope,
      AbstractPhaseScope<Solution_> phase,
      Selector<Solution_>[] selectors)
      implements AutoCloseable {
    @Override
    public void close() {
      for (var selector : selectors) {
        selector.phaseEnded(phase);
        selector.solvingEnded(scope);
      }
    }
  }

  public static class BasicDistanceMeter implements NearbyDistanceMeter<Object, TestdataValue> {
    private int calls;

    @Override
    public double getNearbyDistance(Object origin, TestdataValue destination) {
      if (++calls > 100) {
        throw new AssertionError(
            "A two-value matrix must not drain a never-ending random iterator.");
      }
      return destination.getCode().equals("near") ? 0 : 10;
    }
  }

  public static class CountingListDistanceMeter
      implements NearbyDistanceMeter<TestdataListValue, TestdataListValue> {
    static final AtomicInteger calls = new AtomicInteger();

    @Override
    public double getNearbyDistance(TestdataListValue origin, TestdataListValue destination) {
      calls.incrementAndGet();
      return Math.abs(origin.getCode().hashCode() - destination.getCode().hashCode());
    }
  }
}
