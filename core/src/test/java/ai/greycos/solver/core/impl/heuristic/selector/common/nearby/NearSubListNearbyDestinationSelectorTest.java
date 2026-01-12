package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubList;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.ClassInstanceCache;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.preview.api.domain.metamodel.PositionInList;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testdomain.list.TestdataListEntity;
import ai.greycos.solver.core.testdomain.list.TestdataListValue;
import ai.greycos.solver.core.testutil.PlannerTestUtils;
import ai.greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;

@SuppressWarnings({"unchecked", "rawtypes"})

/**
 * Tests for sublist-to-destination nearby selection in {@link NearbyDestinationSelector}.
 *
 * <p>Tests selection of insertion points in list variables based on proximity to a sublist.
 */
class NearSubListNearbyDestinationSelectorTest {

  @Test
  void originalSelection() {
    // Test original (deterministic) destination selection from v2 origin (at A[1])
    TestdataListValue v1 = new TestdataListValue("10");
    TestdataListValue v2 = new TestdataListValue("45");
    TestdataListValue v3 = new TestdataListValue("50");
    TestdataListValue v4 = new TestdataListValue("60");
    TestdataListValue v5 = new TestdataListValue("75");
    TestdataListEntity e1 = TestdataListEntity.createWithValues("A", v1, v2, v3, v4);
    TestdataListEntity e2 = TestdataListEntity.createWithValues("B", v5);

    EntityDescriptor<TestdataSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataListEntity.class);
    ListVariableDescriptor<TestdataSolution> listVariableDescriptor =
        (ListVariableDescriptor) TestdataListEntity.buildVariableDescriptorForValueList();

    // Distance meter: distance from sublist to destination is based on distance to v2
    NearbyDistanceMeter<SubList, Object> meter =
        (origin, destination) -> {
          // v2 is at index 1 in A
          int v2Value = 45;
          if (destination instanceof TestdataListValue value) {
            int destValue = Integer.parseInt(value.getCode());
            return Math.abs(destValue - v2Value);
          } else if (destination instanceof TestdataListEntity entity) {
            if (entity.getValueList().isEmpty()) {
              return Double.MAX_VALUE;
            }
            int firstValue = Integer.parseInt(entity.getValueList().get(0).getCode());
            return Math.abs(firstValue - v2Value);
          }
          return Double.MAX_VALUE;
        };

    var entitySelector = SelectorTestUtils.mockEntitySelector(entityDescriptor, e1, e2);
    when(entitySelector.isCountable()).thenReturn(true);
    when(entitySelector.isNeverEnding()).thenReturn(false);

    var valueSelector =
        SelectorTestUtils.mockIterableValueSelector(listVariableDescriptor, v1, v2, v3, v4, v5);
    when(valueSelector.getSize(any())).thenReturn(5L);

    // Create a sublist at A[1] containing v2
    SubList originSubList = new SubList(e1, 1, 1); // v2 at index 1, length 1

    var originSubListSelector =
        mock(ai.greycos.solver.core.impl.heuristic.selector.list.SubListSelector.class);
    when(originSubListSelector.iterator())
        .thenAnswer(invocation -> java.util.List.of(originSubList).iterator());

    var configPolicy = mock(HeuristicConfigPolicy.class);
    when(configPolicy.getRandom()).thenReturn(new TestRandom(new double[0]));

    var classInstanceCache = mock(ClassInstanceCache.class);
    when(configPolicy.getClassInstanceCache()).thenReturn(classInstanceCache);
    when(classInstanceCache.newInstance(any(), any(), any(Class.class))).thenReturn(meter);

    var nearbyConfig = new NearbySelectionConfig();
    nearbyConfig.setNearbyDistanceMeterClass(meter.getClass());

    var destinationConfig = new DestinationSelectorConfig();

    NearbyDestinationSelector<TestdataSolution> nearbyDestinationSelector =
        new NearbyDestinationSelector<>(
            destinationConfig,
            configPolicy,
            nearbyConfig,
            SelectionCacheType.JUST_IN_TIME,
            SelectionOrder.ORIGINAL,
            mock(
                ai.greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector
                    .class),
            entitySelector,
            valueSelector,
            null, // originEntitySelector
            originSubListSelector, // originSubListSelector
            null); // originValueSelector

    TestRandom testRandom = new TestRandom(new double[0]);

    InnerScoreDirector<TestdataSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    var supplyManager =
        mock(ai.greycos.solver.core.impl.domain.variable.supply.SupplyManager.class);
    var listVariableStateSupply =
        mock(ai.greycos.solver.core.impl.domain.variable.ListVariableStateSupply.class);
    when(scoreDirector.getSupplyManager()).thenReturn(supplyManager);
    when(supplyManager.demand(any())).thenReturn(listVariableStateSupply);

    SolverScope<TestdataSolution> solverScope =
        SelectorTestUtils.solvingStarted(nearbyDestinationSelector, scoreDirector, testRandom);
    AbstractPhaseScope<TestdataSolution> phaseScopeA =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    nearbyDestinationSelector.phaseStarted(phaseScopeA);
    AbstractStepScope<TestdataSolution> stepScopeA1 =
        PlannerTestUtils.delegatingStepScope(phaseScopeA);
    nearbyDestinationSelector.stepStarted(stepScopeA1);

    var iterator = nearbyDestinationSelector.iterator();

    // Destinations should be returned in sorted order by distance from v2(45)
    // Distance order: v2(0), v3(5), v1(35), v4(15), v5(30)
    // Actually: v2(0), v3(5), v4(15), v5(30), v1(35)
    // Converted to insertion points (after the value):
    // A[2] after v2 at A[1], A[3] after v3 at A[2], A[4] after v4 at A[3], B[1] after v5, A[1]
    // after v2

    PositionInList dest1 = (PositionInList) iterator.next();
    var dest1Entity = dest1.entity();
    assertThat(dest1Entity).isSameAs(e1);
    int dest1Index = dest1.index();
    assertThat(dest1Index).isEqualTo(2); // After v2 at index 1

    PositionInList dest2 = (PositionInList) iterator.next();
    var dest2Entity = dest2.entity();
    assertThat(dest2Entity).isSameAs(e1);
    int dest2Index = dest2.index();
    assertThat(dest2Index).isEqualTo(3); // After v3 at index 2

    PositionInList dest3 = (PositionInList) iterator.next();
    var dest3Entity = dest3.entity();
    assertThat(dest3Entity).isSameAs(e1);
    int dest3Index = dest3.index();
    assertThat(dest3Index).isEqualTo(4); // After v4 at index 3

    PositionInList dest4 = (PositionInList) iterator.next();
    var dest4Entity = dest4.entity();
    assertThat(dest4Entity).isSameAs(e2);
    int dest4Index = dest4.index();
    assertThat(dest4Index).isEqualTo(1); // After v5 at index 0

    PositionInList dest5 = (PositionInList) iterator.next();
    var dest5Entity = dest5.entity();
    assertThat(dest5Entity).isSameAs(e1);
    int dest5Index = dest5.index();
    assertThat(dest5Index).isEqualTo(1); // After v2 (can also insert at v2's position)

    PositionInList dest6 = (PositionInList) iterator.next();
    var dest6Entity = dest6.entity();
    assertThat(dest6Entity).isSameAs(e1);
    int dest6Index = dest6.index();
    assertThat(dest6Index).isEqualTo(0); // Before v1

    PositionInList dest7 = (PositionInList) iterator.next();
    var dest7Entity = dest7.entity();
    assertThat(dest7Entity).isSameAs(e2);
    int dest7Index = dest7.index();
    assertThat(dest7Index).isEqualTo(0); // Before v5

    nearbyDestinationSelector.stepEnded(stepScopeA1);
    nearbyDestinationSelector.phaseEnded(phaseScopeA);
    nearbyDestinationSelector.solvingEnded(solverScope);
  }

  @Test
  void randomSelection() {
    // Test random destination selection from v2 origin (at A[1])
    TestdataListValue v1 = new TestdataListValue("10");
    TestdataListValue v2 = new TestdataListValue("45");
    TestdataListValue v3 = new TestdataListValue("50");
    TestdataListValue v4 = new TestdataListValue("60");
    TestdataListValue v5 = new TestdataListValue("75");
    TestdataListEntity e1 = TestdataListEntity.createWithValues("A", v1, v2, v3, v4);
    TestdataListEntity e2 = TestdataListEntity.createWithValues("B", v5);

    EntityDescriptor<TestdataSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataListEntity.class);
    ListVariableDescriptor<TestdataSolution> listVariableDescriptor =
        (ListVariableDescriptor) TestdataListEntity.buildVariableDescriptorForValueList();

    NearbyDistanceMeter<SubList, Object> meter =
        (origin, destination) -> {
          // v2 is at index 1 in A
          int v2Value = 45;
          if (destination instanceof TestdataListValue value) {
            int destValue = Integer.parseInt(value.getCode());
            return Math.abs(destValue - v2Value);
          } else if (destination instanceof TestdataListEntity entity) {
            if (entity.getValueList().isEmpty()) {
              return Double.MAX_VALUE;
            }
            int firstValue = Integer.parseInt(entity.getValueList().get(0).getCode());
            return Math.abs(firstValue - v2Value);
          }
          return Double.MAX_VALUE;
        };

    var entitySelector = SelectorTestUtils.mockEntitySelector(entityDescriptor, e1, e2);
    when(entitySelector.isCountable()).thenReturn(true);
    when(entitySelector.isNeverEnding()).thenReturn(false);

    var valueSelector =
        SelectorTestUtils.mockIterableValueSelector(listVariableDescriptor, v1, v2, v3, v4, v5);
    when(valueSelector.getSize(any())).thenReturn(5L);

    // Create a sublist at A[1] containing v2
    SubList originSubList = new SubList(e1, 1, 1); // v2 at index 1, length 1

    var originSubListSelector =
        mock(ai.greycos.solver.core.impl.heuristic.selector.list.SubListSelector.class);
    when(originSubListSelector.iterator())
        .thenAnswer(invocation -> java.util.List.of(originSubList).iterator());

    var configPolicy = mock(HeuristicConfigPolicy.class);
    when(configPolicy.getRandom()).thenReturn(new TestRandom(new double[0]));

    var classInstanceCache = mock(ClassInstanceCache.class);
    when(configPolicy.getClassInstanceCache()).thenReturn(classInstanceCache);
    when(classInstanceCache.newInstance(any(), any(), any(Class.class))).thenReturn(meter);

    var nearbyConfig = new NearbySelectionConfig();
    nearbyConfig.setNearbyDistanceMeterClass(meter.getClass());

    var destinationConfig = new DestinationSelectorConfig();

    NearbyDestinationSelector<TestdataSolution> nearbyDestinationSelector =
        new NearbyDestinationSelector<>(
            destinationConfig,
            configPolicy,
            nearbyConfig,
            SelectionCacheType.JUST_IN_TIME,
            SelectionOrder.RANDOM,
            mock(
                ai.greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector
                    .class),
            entitySelector,
            valueSelector,
            null, // originEntitySelector
            originSubListSelector, // originSubListSelector
            null); // originValueSelector

    TestRandom testRandom = new TestRandom(new double[] {0, 1, 2, 3, 4, 5, 6}); // nearbyIndices

    InnerScoreDirector<TestdataSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    var supplyManager =
        mock(ai.greycos.solver.core.impl.domain.variable.supply.SupplyManager.class);
    var listVariableStateSupply =
        mock(ai.greycos.solver.core.impl.domain.variable.ListVariableStateSupply.class);
    when(scoreDirector.getSupplyManager()).thenReturn(supplyManager);
    when(supplyManager.demand(any())).thenReturn(listVariableStateSupply);

    SolverScope<TestdataSolution> solverScope =
        SelectorTestUtils.solvingStarted(nearbyDestinationSelector, scoreDirector, testRandom);
    AbstractPhaseScope<TestdataSolution> phaseScopeA =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    nearbyDestinationSelector.phaseStarted(phaseScopeA);
    AbstractStepScope<TestdataSolution> stepScopeA1 =
        PlannerTestUtils.delegatingStepScope(phaseScopeA);
    nearbyDestinationSelector.stepStarted(stepScopeA1);

    var iterator = nearbyDestinationSelector.iterator();

    // Should return destinations in the order specified by nearbyIndices [0,1,2,3,4,5,6]
    // These are indices into the sorted-by-distance destination list

    PositionInList dest1 = (PositionInList) iterator.next();
    var dest1Entity = dest1.entity();
    assertThat(dest1Entity).isSameAs(e1);
    int dest1Index = dest1.index();
    assertThat(dest1Index).isEqualTo(2); // Index 0 in sorted list: after v2

    PositionInList dest2 = (PositionInList) iterator.next();
    var dest2Entity = dest2.entity();
    assertThat(dest2Entity).isSameAs(e1);
    int dest2Index = dest2.index();
    assertThat(dest2Index).isEqualTo(3); // Index 1 in sorted list: after v3

    PositionInList dest3 = (PositionInList) iterator.next();
    var dest3Entity = dest3.entity();
    assertThat(dest3Entity).isSameAs(e1);
    int dest3Index = dest3.index();
    assertThat(dest3Index).isEqualTo(4); // Index 2 in sorted list: after v4

    PositionInList dest4 = (PositionInList) iterator.next();
    var dest4Entity = dest4.entity();
    assertThat(dest4Entity).isSameAs(e2);
    int dest4Index = dest4.index();
    assertThat(dest4Index).isEqualTo(1); // Index 3 in sorted list: after v5

    PositionInList dest5 = (PositionInList) iterator.next();
    var dest5Entity = dest5.entity();
    assertThat(dest5Entity).isSameAs(e1);
    int dest5Index = dest5.index();
    assertThat(dest5Index).isEqualTo(1); // Index 4 in sorted list: after v2 (or at v2)

    PositionInList dest6 = (PositionInList) iterator.next();
    var dest6Entity = dest6.entity();
    assertThat(dest6Entity).isSameAs(e1);
    int dest6Index = dest6.index();
    assertThat(dest6Index).isEqualTo(0); // Index 5 in sorted list: before v1

    PositionInList dest7 = (PositionInList) iterator.next();
    var dest7Entity = dest7.entity();
    assertThat(dest7Entity).isSameAs(e2);
    int dest7Index = dest7.index();
    assertThat(dest7Index).isEqualTo(0); // Index 6 in sorted list: before v5

    nearbyDestinationSelector.stepEnded(stepScopeA1);
    nearbyDestinationSelector.phaseEnded(phaseScopeA);
    nearbyDestinationSelector.solvingEnded(solverScope);
  }
}
