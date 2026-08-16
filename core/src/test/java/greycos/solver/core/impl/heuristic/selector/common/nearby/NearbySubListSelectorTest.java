package greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testutil.PlannerTestUtils;
import greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;

@SuppressWarnings({"unchecked", "rawtypes"})
class NearbySubListSelectorTest {

  @Test
  void unassignedDestinationIsNotTreatedAsIndexZero() {
    TestdataListValue v1 = new TestdataListValue("10");
    TestdataListValue v2 = new TestdataListValue("20");
    TestdataListValue v3 = new TestdataListValue("30");
    TestdataListEntity entity = TestdataListEntity.createWithValues("A", v1, v2, v3);

    EntityDescriptor<TestdataSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataListEntity.class);
    ListVariableDescriptor<TestdataSolution> variableDescriptor =
        (ListVariableDescriptor) TestdataListEntity.buildVariableDescriptorForValueList();
    var childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(variableDescriptor, v1, v2, v3);
    var entitySelector = SelectorTestUtils.mockEntitySelector(entityDescriptor, entity);
    var childSubListSelector =
        new RandomSubListSelector<>(entitySelector, childValueSelector, 1, 3);
    var originSubListSelector =
        SelectorTestUtils.mockReplayingSubListSelector(
            variableDescriptor, new SubList(entity, 0, 1));

    NearbySubListSelector<TestdataSolution> nearbySubListSelector =
        new NearbySubListSelector<>(
            childSubListSelector,
            originSubListSelector,
            (origin, destination) -> 0.0,
            null,
            false,
            Integer.MAX_VALUE,
            false);

    InnerScoreDirector<TestdataSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    var listVariableStateSupply =
        mock(greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply.class);
    when(listVariableStateSupply.getInverseSingleton(any())).thenReturn(entity);
    when(listVariableStateSupply.getIndexOrElse(any(), eq(-1))).thenReturn(-1);
    NearbyTestUtils.mockSupplyManager(scoreDirector, listVariableStateSupply);

    SolverScope<TestdataSolution> solverScope =
        SelectorTestUtils.solvingStarted(nearbySubListSelector, scoreDirector, new TestRandom(0));
    AbstractPhaseScope<TestdataSolution> phaseScope =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    nearbySubListSelector.phaseStarted(phaseScope);

    assertThat(nearbySubListSelector.iterator().hasNext()).isFalse();

    nearbySubListSelector.phaseEnded(phaseScope);
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

    var originSubListSelector =
        SelectorTestUtils.mockReplayingSubListSelector(variableDescriptor, new SubList(e1, 0, 1));

    NearbySubListSelector<TestdataSolution> nearbySubListSelector =
        new NearbySubListSelector<>(
            childSubListSelector,
            originSubListSelector,
            meter,
            null,
            false,
            Integer.MAX_VALUE,
            false);

    TestRandom testRandom = new TestRandom(new double[0]);

    InnerScoreDirector<TestdataSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    var listVariableStateSupply =
        mock(greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply.class);
    NearbyTestUtils.mockSupplyManager(scoreDirector, listVariableStateSupply);

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
