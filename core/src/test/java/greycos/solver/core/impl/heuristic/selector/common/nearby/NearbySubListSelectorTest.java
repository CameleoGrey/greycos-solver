package greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.valuerange.buildin.collection.ListValueRange;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingValue;
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
    var listVariableState = mock(greycos.solver.core.impl.cotwin.variable.ListVariableState.class);
    when(listVariableState.getInverseSingleton(any())).thenReturn(entity);
    when(listVariableState.getIndexOrElse(any(), eq(-1))).thenReturn(-1);
    NearbyTestUtils.mockSupplyManager(scoreDirector, listVariableState);

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
    var listVariableState = mock(greycos.solver.core.impl.cotwin.variable.ListVariableState.class);
    NearbyTestUtils.mockSupplyManager(scoreDirector, listVariableState);

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

  @Test
  void randomMinimumLengthFilteringPrecedesTheCapAndTracksListChanges() {
    var a = new TestdataListValue("a");
    var b = new TestdataListValue("b");
    var c = new TestdataListValue("c");
    var entity = TestdataListEntity.createWithValues("entity", a, b, c);
    var variableDescriptor = TestdataListEntity.buildVariableDescriptorForValueList();
    var entitySelector =
        SelectorTestUtils.mockEntitySelector(TestdataListEntity.buildEntityDescriptor(), entity);
    var childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(variableDescriptor, a, b, c);
    var childSelector = new RandomSubListSelector<>(entitySelector, childValueSelector, 2, 2);
    SubListSelector<TestdataListSolution> originSelector = mock(SubListSelector.class);
    when(originSelector.getValueCount()).thenReturn(3L);
    when(originSelector.iterator())
        .thenAnswer(ignored -> List.of(new SubList(entity, 0, 1)).iterator());
    NearbyDistanceMeter<Object, Object> meter =
        (origin, destination) -> destination == c ? 0 : destination == b ? 1 : 2;
    var selector =
        new NearbySubListSelector<>(
            childSelector,
            originSelector,
            meter,
            new BlockDistributionNearbyRandom(1, 1, 1.0, 0.0),
            true,
            1,
            false);
    InnerScoreDirector<TestdataListSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    var listVariableState = mock(greycos.solver.core.impl.cotwin.variable.ListVariableState.class);
    when(listVariableState.getInverseSingleton(any()))
        .thenAnswer(
            invocation ->
                entity.getValueList().contains(invocation.getArgument(0)) ? entity : null);
    when(listVariableState.getIndexOrElse(any(), eq(-1)))
        .thenAnswer(invocation -> entity.getValueList().indexOf(invocation.getArgument(0)));
    NearbyTestUtils.mockSupplyManager(scoreDirector, listVariableState);
    var solverScope = SelectorTestUtils.solvingStarted(selector, scoreDirector, new Random(0));
    var phaseScope = PlannerTestUtils.delegatingPhaseScope(solverScope);
    selector.phaseStarted(phaseScope);
    try {
      var iterator = selector.iterator();
      var first = iterator.next();
      assertThat(variableDescriptor.<Object>getElement(first.entity(), first.fromIndex()))
          .isSameAs(b);
      assertThat(first.length()).isEqualTo(2);
      Collections.swap(entity.getValueList(), 1, 2);
      var second = iterator.next();
      assertThat(variableDescriptor.<Object>getElement(second.entity(), second.fromIndex()))
          .isSameAs(c);
      entity.getValueList().subList(1, 3).clear();
      assertThat(iterator.hasNext()).isFalse();
      assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    } finally {
      selector.phaseEnded(phaseScope);
      selector.solvingEnded(solverScope);
    }
  }

  @Test
  void entityRangeEligibilityFiltersBothDirectionsBeforeCappingAndLimitsSelectedLength() {
    var a = new TestdataListEntityProvidingValue("a");
    var b = new TestdataListEntityProvidingValue("b");
    var c = new TestdataListEntityProvidingValue("c");
    var d = new TestdataListEntityProvidingValue("d");
    var source =
        new TestdataListEntityProvidingEntity(
            "source", List.of(a, c, d), new ArrayList<>(List.of(a)));
    var forbidden =
        new TestdataListEntityProvidingEntity("forbidden", List.of(d), new ArrayList<>(List.of(d)));
    var allowed =
        new TestdataListEntityProvidingEntity(
            "allowed", List.of(a, b, c, d), new ArrayList<>(List.of(c, b)));
    var entities = List.of(source, forbidden, allowed);
    var variableDescriptor =
        TestdataListEntityProvidingEntity.buildVariableDescriptorForValueList();
    var entitySelector =
        SelectorTestUtils.mockEntitySelector(
            TestdataListEntityProvidingEntity.buildEntityDescriptor(), entities.toArray());
    var values = SelectorTestUtils.mockIterableValueSelector(variableDescriptor, a, b, c, d);
    var childSelector = new RandomSubListSelector<>(entitySelector, values, 1, 3);
    SubListSelector<TestdataListEntityProvidingSolution> originSelector =
        mock(SubListSelector.class);
    when(originSelector.getValueCount()).thenReturn(4L);
    when(originSelector.iterator())
        .thenAnswer(ignored -> List.of(new SubList(source, 0, 1)).iterator());
    NearbyDistanceMeter<Object, Object> meter =
        (origin, destination) ->
            destination == d ? 0 : destination == c ? 1 : destination == b ? 2 : 3;
    var selector =
        new NearbySubListSelector<>(
            childSelector,
            originSelector,
            meter,
            new BlockDistributionNearbyRandom(1, 1, 1.0, 0.0),
            true,
            1,
            false);
    InnerScoreDirector<TestdataListEntityProvidingSolution, ?> scoreDirector =
        mock(InnerScoreDirector.class);
    var state = mock(greycos.solver.core.impl.cotwin.variable.ListVariableState.class);
    when(state.getInverseSingleton(any()))
        .thenAnswer(
            invocation ->
                entities.stream()
                    .filter(entity -> entity.getValueList().contains(invocation.getArgument(0)))
                    .findFirst()
                    .orElse(null));
    when(state.getIndexOrElse(any(), eq(-1)))
        .thenAnswer(
            invocation ->
                entities.stream()
                    .mapToInt(entity -> entity.getValueList().indexOf(invocation.getArgument(0)))
                    .filter(index -> index >= 0)
                    .findFirst()
                    .orElse(-1));
    ValueRangeManager<TestdataListEntityProvidingSolution> ranges = mock(ValueRangeManager.class);
    when(scoreDirector.getValueRangeManager()).thenReturn(ranges);
    when(ranges.getFromEntity(any(), any()))
        .thenAnswer(
            invocation ->
                new ListValueRange<>(
                    ((TestdataListEntityProvidingEntity) invocation.getArgument(1))
                        .getValueRange()));
    NearbyTestUtils.mockSupplyManager(scoreDirector, state);
    var random =
        new Random(0) {
          @Override
          public int nextInt(int bound) {
            return bound - 1;
          }
        };
    var solverScope = SelectorTestUtils.solvingStarted(selector, scoreDirector, random);
    var phaseScope = PlannerTestUtils.delegatingPhaseScope(solverScope);
    selector.phaseStarted(phaseScope);
    try {
      var iterator = selector.iterator();
      // The nearest destination cannot accept a. The next one starts at c, but extending it to b
      // would violate the source entity's value range, even with a maximum length of three.
      assertThat(iterator.next()).isEqualTo(new SubList(allowed, 0, 1));
      forbidden.getValueList().remove(d);
      allowed.getValueList().add(d);
      // Geographic distances remain cached; eligibility follows the value's current owner.
      assertThat(iterator.next()).isEqualTo(new SubList(allowed, 2, 1));
    } finally {
      selector.phaseEnded(phaseScope);
      selector.solvingEnded(solverScope);
    }
  }
}
