package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import static ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.stream.IntStream;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionDistributionType;
import ai.greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubList;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;
import ai.greycos.solver.core.testutil.PlannerTestUtils;
import ai.greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;

@SuppressWarnings({"unchecked", "rawtypes"})
class NearbyDestinationSelectorTest {

  @Test
  void solvingStartedDoesNotFailWhenOriginValueSelectorSizeIsUnavailable() {
    ListVariableDescriptor<TestdataListSolution> listVariableDescriptor =
        TestdataListEntity.buildVariableDescriptorForValueList();
    EntityDescriptor<TestdataListSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataListEntity.class);

    EntitySelector<TestdataListSolution> childEntitySelector =
        SelectorTestUtils.mockEntitySelector(
            entityDescriptor, new TestdataListEntity("A"), new TestdataListEntity("B"));
    IterableValueSelector<TestdataListSolution> childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(
            listVariableDescriptor, new TestdataListValue("1"), new TestdataListValue("2"));
    IterableValueSelector<TestdataListSolution> originValueSelector =
        SelectorTestUtils.mockIterableValueSelector(
            listVariableDescriptor, new TestdataListValue("1"));
    when(originValueSelector.getSize()).thenThrow(new NullPointerException("cachedValueRange"));

    ElementDestinationSelector<TestdataListSolution> destinationSelector =
        mock(ElementDestinationSelector.class);

    NearbySelectionConfig nearbySelectionConfig = new NearbySelectionConfig();
    nearbySelectionConfig.setNearbyDistanceMeterClass(TestNearbyDistanceMeter.class);

    NearbyDestinationSelector<TestdataListSolution> nearbyDestinationSelector =
        new NearbyDestinationSelector<>(
            new DestinationSelectorConfig(),
            buildHeuristicConfigPolicy(TestdataListSolution.buildSolutionDescriptor()),
            nearbySelectionConfig,
            SelectionCacheType.JUST_IN_TIME,
            SelectionOrder.ORIGINAL,
            destinationSelector,
            childEntitySelector,
            childValueSelector,
            null,
            null,
            originValueSelector);

    InnerScoreDirector<TestdataListSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    SupplyManager supplyManager = mock(SupplyManager.class);
    ListVariableStateSupply<TestdataListSolution, Object, Object> listVariableStateSupply =
        mock(ListVariableStateSupply.class);
    when(scoreDirector.getSupplyManager()).thenReturn(supplyManager);
    when(supplyManager.demand(any())).thenAnswer(invocation -> listVariableStateSupply);

    assertThatCode(
            () ->
                SelectorTestUtils.solvingStarted(
                    nearbyDestinationSelector, scoreDirector, new TestRandom(0)))
        .doesNotThrowAnyException();
  }

  @Test
  void randomSelectionWithDistributionCapDoesNotBreakStrictDestinationSizeValidation() {
    ListVariableDescriptor<TestdataListSolution> listVariableDescriptor =
        TestdataListEntity.buildVariableDescriptorForValueList();
    EntityDescriptor<TestdataListSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataListEntity.class);
    when(entityDescriptor.matchesEntity(any())).thenReturn(true);

    var childEntities =
        IntStream.range(0, 80).mapToObj(i -> new TestdataListEntity("E" + i)).toArray();
    EntitySelector<TestdataListSolution> childEntitySelector =
        SelectorTestUtils.mockEntitySelector(entityDescriptor, childEntities);
    IterableValueSelector<TestdataListSolution> childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(listVariableDescriptor);
    EntitySelector<TestdataListSolution> originEntitySelector =
        SelectorTestUtils.mockEntitySelector(entityDescriptor, childEntities[0]);

    ElementDestinationSelector<TestdataListSolution> destinationSelector =
        mock(ElementDestinationSelector.class);

    NearbySelectionConfig nearbySelectionConfig = new NearbySelectionConfig();
    nearbySelectionConfig.setNearbyDistanceMeterClass(TestNearbyDistanceMeter.class);
    nearbySelectionConfig.setNearbySelectionDistributionType(
        NearbySelectionDistributionType.PARABOLIC_DISTRIBUTION);
    nearbySelectionConfig.setParabolicDistributionSizeMaximum(40);

    NearbyDestinationSelector<TestdataListSolution> nearbyDestinationSelector =
        new NearbyDestinationSelector<>(
            new DestinationSelectorConfig(),
            buildHeuristicConfigPolicy(TestdataListSolution.buildSolutionDescriptor()),
            nearbySelectionConfig,
            SelectionCacheType.JUST_IN_TIME,
            SelectionOrder.RANDOM,
            destinationSelector,
            childEntitySelector,
            childValueSelector,
            originEntitySelector,
            null,
            null);

    InnerScoreDirector<TestdataListSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    SupplyManager supplyManager = mock(SupplyManager.class);
    ListVariableStateSupply<TestdataListSolution, Object, Object> listVariableStateSupply =
        mock(ListVariableStateSupply.class);
    when(scoreDirector.getSupplyManager()).thenReturn(supplyManager);
    when(supplyManager.demand(any())).thenAnswer(invocation -> listVariableStateSupply);

    SelectorTestUtils.solvingStarted(nearbyDestinationSelector, scoreDirector, new TestRandom(0));

    assertThatCode(() -> nearbyDestinationSelector.iterator().next()).doesNotThrowAnyException();
  }

  @Test
  void eagerInitializationSkipsSubListOriginEnumeration() {
    ListVariableDescriptor<TestdataListSolution> listVariableDescriptor =
        TestdataListEntity.buildVariableDescriptorForValueList();
    EntityDescriptor<TestdataListSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataListEntity.class);
    when(entityDescriptor.matchesEntity(any())).thenReturn(true);

    EntitySelector<TestdataListSolution> childEntitySelector =
        SelectorTestUtils.mockEntitySelector(
            entityDescriptor, new TestdataListEntity("A"), new TestdataListEntity("B"));
    IterableValueSelector<TestdataListSolution> childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(
            listVariableDescriptor, new TestdataListValue("1"), new TestdataListValue("2"));

    @SuppressWarnings("unchecked")
    SubListSelector<TestdataListSolution> originSubListSelector = mock(SubListSelector.class);
    @SuppressWarnings("unchecked")
    Iterator<SubList> throwingIterator = mock(Iterator.class);
    when(throwingIterator.hasNext())
        .thenThrow(new IllegalStateException("Replay must occur after record."));
    when(originSubListSelector.iterator()).thenReturn(throwingIterator);
    when(originSubListSelector.getValueCount()).thenReturn(1L);
    when(originSubListSelector.getVariableDescriptor()).thenReturn(listVariableDescriptor);

    ElementDestinationSelector<TestdataListSolution> destinationSelector =
        mock(ElementDestinationSelector.class);

    NearbySelectionConfig nearbySelectionConfig = new NearbySelectionConfig();
    nearbySelectionConfig.setNearbyDistanceMeterClass(TestNearbyDistanceMeter.class);
    nearbySelectionConfig.setEagerInitialization(true);

    NearbyDestinationSelector<TestdataListSolution> nearbyDestinationSelector =
        new NearbyDestinationSelector<>(
            new DestinationSelectorConfig(),
            buildHeuristicConfigPolicy(TestdataListSolution.buildSolutionDescriptor()),
            nearbySelectionConfig,
            SelectionCacheType.JUST_IN_TIME,
            SelectionOrder.ORIGINAL,
            destinationSelector,
            childEntitySelector,
            childValueSelector,
            null,
            originSubListSelector,
            null);

    InnerScoreDirector<TestdataListSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    SupplyManager supplyManager = mock(SupplyManager.class);
    ListVariableStateSupply<TestdataListSolution, Object, Object> listVariableStateSupply =
        mock(ListVariableStateSupply.class);
    when(scoreDirector.getSupplyManager()).thenReturn(supplyManager);
    when(supplyManager.demand(any())).thenAnswer(invocation -> listVariableStateSupply);

    SolverScope<TestdataListSolution> solverScope =
        SelectorTestUtils.solvingStarted(
            nearbyDestinationSelector, scoreDirector, new TestRandom(0));
    AbstractPhaseScope<TestdataListSolution> phaseScope =
        PlannerTestUtils.delegatingPhaseScope(solverScope);

    assertThatCode(() -> nearbyDestinationSelector.phaseStarted(phaseScope))
        .doesNotThrowAnyException();
    verify(originSubListSelector, never()).iterator();
  }

  public static final class TestNearbyDistanceMeter implements NearbyDistanceMeter<Object, Object> {

    public TestNearbyDistanceMeter() {}

    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      return 0.0;
    }
  }
}
