package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.ClassInstanceCache;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testdomain.list.TestdataListEntity;
import ai.greycos.solver.core.testdomain.list.TestdataListValue;
import ai.greycos.solver.core.testutil.PlannerTestUtils;
import ai.greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;

@SuppressWarnings({"unchecked", "rawtypes"})
class NearbySubListSelectorTest {
  @Test
  void iteratorShouldBeEmptyIfChildSubListSelectorIsEmpty() {
    // Test that iterator is empty when no sublists satisfy minimum size constraint
    TestdataListValue v1 = new TestdataListValue("10");
    TestdataListValue v2 = new TestdataListValue("45");
    TestdataListValue v3 = new TestdataListValue("50");
    TestdataListEntity e1 = TestdataListEntity.createWithValues("A", v1, v2);
    TestdataListEntity e2 = TestdataListEntity.createWithValues("B", v3);

    EntityDescriptor<TestdataSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataListEntity.class);
    ListVariableDescriptor<TestdataSolution> variableDescriptor =
        (ListVariableDescriptor) TestdataListEntity.buildVariableDescriptorForValueList();

    NearbyDistanceMeter<TestdataListValue, TestdataListValue> meter =
        (origin, destination) -> {
          int originValue = Integer.parseInt(origin.getCode());
          int destValue = Integer.parseInt(destination.getCode());
          return Math.abs(destValue - originValue);
        };

    var childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(variableDescriptor, v1, v2, v3);

    var entitySelector = SelectorTestUtils.mockEntitySelector(entityDescriptor, e1, e2);
    when(entitySelector.isNeverEnding()).thenReturn(false);

    // minimumSubListSize=3, but A only has 2 elements, so no sublists are possible
    var childSubListSelector =
        new RandomSubListSelector<>(entitySelector, childValueSelector, 3, 5);

    var configPolicy = mock(HeuristicConfigPolicy.class);
    when(configPolicy.getRandom()).thenReturn(new TestRandom(new double[0]));

    var classInstanceCache = mock(ClassInstanceCache.class);
    when(configPolicy.getClassInstanceCache()).thenReturn(classInstanceCache);
    when(classInstanceCache.newInstance(any(), any(), any(Class.class))).thenReturn(meter);

    var nearbyConfig = new NearbySelectionConfig();
    nearbyConfig.setNearbyDistanceMeterClass(meter.getClass());

    NearbySubListSelector<TestdataSolution> nearbySubListSelector =
        new NearbySubListSelector<>(
            new SubListSelectorConfig(),
            configPolicy,
            nearbyConfig,
            SelectionCacheType.JUST_IN_TIME,
            SelectionOrder.ORIGINAL,
            variableDescriptor,
            childSubListSelector);

    TestRandom testRandom = new TestRandom(new double[0]);

    InnerScoreDirector<TestdataSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    var supplyManager =
        mock(ai.greycos.solver.core.impl.domain.variable.supply.SupplyManager.class);
    var listVariableStateSupply =
        mock(ai.greycos.solver.core.impl.domain.variable.ListVariableStateSupply.class);
    when(scoreDirector.getSupplyManager()).thenReturn(supplyManager);
    when(supplyManager.demand(any())).thenReturn(listVariableStateSupply);

    SolverScope<TestdataSolution> solverScope =
        SelectorTestUtils.solvingStarted(nearbySubListSelector, scoreDirector, testRandom);
    AbstractPhaseScope<TestdataSolution> phaseScopeA =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    nearbySubListSelector.phaseStarted(phaseScopeA);
    AbstractStepScope<TestdataSolution> stepScopeA1 =
        PlannerTestUtils.delegatingStepScope(phaseScopeA);
    nearbySubListSelector.stepStarted(stepScopeA1);

    var iterator = nearbySubListSelector.iterator();
    // No sublists possible, so iterator should be empty
    assertThat(iterator.hasNext()).isFalse();

    nearbySubListSelector.stepEnded(stepScopeA1);
    nearbySubListSelector.phaseEnded(phaseScopeA);
    nearbySubListSelector.solvingEnded(solverScope);
  }
}
