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
import ai.greycos.solver.core.impl.heuristic.selector.list.SubList;
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
  void randomSelectionUnrestricted() {
    // Test random sublist selection with no size constraints
    TestdataListValue v1 = new TestdataListValue("10");
    TestdataListValue v2 = new TestdataListValue("45");
    TestdataListValue v3 = new TestdataListValue("50");
    TestdataListValue v4 = new TestdataListValue("60");
    TestdataListValue v5 = new TestdataListValue("75");
    TestdataListEntity e1 = TestdataListEntity.createWithValues("A", v1, v2, v3, v4);
    TestdataListEntity e2 = TestdataListEntity.createWithValues("B", v5);

    EntityDescriptor<TestdataSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataListEntity.class);
    ListVariableDescriptor<TestdataSolution> variableDescriptor =
        (ListVariableDescriptor) TestdataListEntity.buildVariableDescriptorForValueList();

    // Distance meter based on difference between numeric values
    NearbyDistanceMeter<TestdataListValue, TestdataListValue> meter =
        (origin, destination) -> {
          int originValue = Integer.parseInt(origin.getCode());
          int destValue = Integer.parseInt(destination.getCode());
          return Math.abs(destValue - originValue);
        };

    var childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(variableDescriptor, v1, v2, v3, v4, v5);

    var entitySelector = SelectorTestUtils.mockEntitySelector(entityDescriptor, e1, e2);
    when(entitySelector.isNeverEnding()).thenReturn(true);

    var childSubListSelector =
        new RandomSubListSelector<>(entitySelector, childValueSelector, 1, Integer.MAX_VALUE);

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
            SelectionOrder.RANDOM,
            variableDescriptor,
            childSubListSelector);

    TestRandom testRandom =
        new TestRandom(new double[] {3, 0, 3, 1, 3, 2}); // nearbyIndex, then subListSize

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
    assertThat(iterator.hasNext()).isTrue();

    // First selection: nearbyIndex=3 => v1, then subListSize=0 => A[1+1]
    SubList subList1 = iterator.next();
    assertThat(subList1.entity()).isEqualTo(e1);
    assertThat(subList1.fromIndex()).isEqualTo(1); // v1 at index 0, so fromIndex=1
    assertThat(subList1.length()).isEqualTo(1); // subListSize 0 maps to minimum 1

    assertThat(iterator.hasNext()).isTrue();
    SubList subList2 = iterator.next();
    assertThat(subList2.entity()).isEqualTo(e1);
    assertThat(subList2.fromIndex()).isEqualTo(2); // v1 at index 0, so fromIndex=2
    assertThat(subList2.length()).isEqualTo(2);

    assertThat(iterator.hasNext()).isTrue();
    SubList subList3 = iterator.next();
    assertThat(subList3.entity()).isEqualTo(e1);
    assertThat(subList3.fromIndex()).isEqualTo(2);
    assertThat(subList3.length()).isEqualTo(3);

    nearbySubListSelector.stepEnded(stepScopeA1);
    nearbySubListSelector.phaseEnded(phaseScopeA);
    nearbySubListSelector.solvingEnded(solverScope);
  }

  @Test
  void randomSelectionWithMinMaxSubListSize() {
    // Test random sublist selection with minimum and maximum size constraints
    TestdataListValue v1 = new TestdataListValue("10");
    TestdataListValue v2 = new TestdataListValue("45");
    TestdataListValue v3 = new TestdataListValue("50");
    TestdataListValue v4 = new TestdataListValue("60");
    TestdataListValue v5 = new TestdataListValue("75");
    TestdataListEntity e1 = TestdataListEntity.createWithValues("A", v1, v2, v3, v4);
    TestdataListEntity e2 = TestdataListEntity.createWithValues("B", v5);

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
        SelectorTestUtils.mockIterableValueSelector(variableDescriptor, v1, v2, v3, v4, v5);

    var entitySelector = SelectorTestUtils.mockEntitySelector(entityDescriptor, e1, e2);
    when(entitySelector.isNeverEnding()).thenReturn(true);

    // minimumSubListSize=2, maximumSubListSize=3
    var childSubListSelector =
        new RandomSubListSelector<>(entitySelector, childValueSelector, 2, 3);

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
            SelectionOrder.RANDOM,
            variableDescriptor,
            childSubListSelector);

    TestRandom testRandom = new TestRandom(new double[] {4, 0, 4, 1, 4, 1, 4, 0});

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

    // First selection: nearbyIndex=4 => v1, then subListSize=0 => actual size=2 => A[0+2]
    SubList subList1 = iterator.next();
    assertThat(subList1.entity()).isEqualTo(e1);
    assertThat(subList1.fromIndex()).isEqualTo(0); // v1 is at index 0
    assertThat(subList1.length()).isEqualTo(2); // minimum size

    // Second selection: subListSize=1 => actual size=3 => A[0+3]
    SubList subList2 = iterator.next();
    assertThat(subList2.entity()).isEqualTo(e1);
    assertThat(subList2.fromIndex()).isEqualTo(0);
    assertThat(subList2.length()).isEqualTo(3); // maximum size

    // Third selection: subListSize=1 => actual size=3 => A[0+3]
    SubList subList3 = iterator.next();
    assertThat(subList3.entity()).isEqualTo(e1);
    assertThat(subList3.fromIndex()).isEqualTo(0);
    assertThat(subList3.length()).isEqualTo(3);

    // Fourth selection: subListSize=0 => actual size=2 => A[0+2]
    SubList subList4 = iterator.next();
    assertThat(subList4.entity()).isEqualTo(e1);
    assertThat(subList4.fromIndex()).isEqualTo(0);
    assertThat(subList4.length()).isEqualTo(2);

    nearbySubListSelector.stepEnded(stepScopeA1);
    nearbySubListSelector.phaseEnded(phaseScopeA);
    nearbySubListSelector.solvingEnded(solverScope);
  }

  @Test
  void avoidUsingRandomWhenOnlySingleSubListIsPossible() {
    // Test that random selection is avoided when only one sublist is possible
    // to prevent random.nextInt(0) error
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

    // minimumSubListSize=2, so A[v1, v2] has only one sublist of length 2
    var childSubListSelector =
        new RandomSubListSelector<>(entitySelector, childValueSelector, 2, 3);

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
            SelectionOrder.RANDOM,
            variableDescriptor,
            childSubListSelector);

    TestRandom testRandom = new TestRandom((double) 2); // => v1

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
    assertThat(iterator.hasNext()).isTrue();

    // Only one sublist is possible: A[0+2]
    SubList subList = iterator.next();
    assertThat(subList.entity()).isEqualTo(e1);
    assertThat(subList.fromIndex()).isEqualTo(0);
    assertThat(subList.length()).isEqualTo(2);

    // Verify random was only called once (for nearbyIndex)
    testRandom.assertIntBoundJustRequested(3);

    nearbySubListSelector.stepEnded(stepScopeA1);
    nearbySubListSelector.phaseEnded(phaseScopeA);
    nearbySubListSelector.solvingEnded(solverScope);
  }

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
