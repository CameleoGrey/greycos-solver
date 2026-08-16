package greycos.solver.core.impl.heuristic.selector.common.nearby;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.IntStream;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionDistributionType;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testutil.PlannerTestUtils;
import greycos.solver.core.testutil.TestRandom;

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
    ListVariableStateSupply<TestdataListSolution, Object, Object> listVariableStateSupply =
        mock(ListVariableStateSupply.class);
    NearbyTestUtils.mockSupplyManager(scoreDirector, listVariableStateSupply);

    SolverScope<TestdataListSolution> solverScope =
        SelectorTestUtils.solvingStarted(
            nearbyDestinationSelector, scoreDirector, new TestRandom(0));
    verify(originValueSelector, never()).getSize();
    nearbyDestinationSelector.solvingEnded(solverScope);
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
    ListVariableStateSupply<TestdataListSolution, Object, Object> listVariableStateSupply =
        mock(ListVariableStateSupply.class);
    NearbyTestUtils.mockSupplyManager(scoreDirector, listVariableStateSupply);

    SolverScope<TestdataListSolution> solverScope =
        SelectorTestUtils.solvingStarted(
            nearbyDestinationSelector, scoreDirector, new TestRandom(0));
    AbstractPhaseScope<TestdataListSolution> phaseScope =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    nearbyDestinationSelector.phaseStarted(phaseScope);

    assertThatCode(() -> nearbyDestinationSelector.iterator().next()).doesNotThrowAnyException();
    nearbyDestinationSelector.phaseEnded(phaseScope);
    nearbyDestinationSelector.solvingEnded(solverScope);
  }

  @Test
  void eagerInitializationRejectsNonEnumerableSubListOrigin() {
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
    when(originSubListSelector.getValueCount()).thenReturn(1L);
    when(originSubListSelector.getVariableDescriptor()).thenReturn(listVariableDescriptor);

    ElementDestinationSelector<TestdataListSolution> destinationSelector =
        mock(ElementDestinationSelector.class);

    NearbySelectionConfig nearbySelectionConfig = new NearbySelectionConfig();
    nearbySelectionConfig.setNearbyDistanceMeterClass(TestNearbyDistanceMeter.class);
    nearbySelectionConfig.setEagerInitialization(true);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
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
                    null))
        .withMessageContainingAll("Eager nearby initialization", "subList", "lazily");
  }

  public static final class TestNearbyDistanceMeter implements NearbyDistanceMeter<Object, Object> {

    public TestNearbyDistanceMeter() {}

    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      return 0.0;
    }
  }
}
