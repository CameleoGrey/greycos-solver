package greycos.solver.core.impl.heuristic.selector.common.nearby;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionDistributionType;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.Selector;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelectorFactory;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicRecordingEntitySelector;
import greycos.solver.core.impl.heuristic.selector.list.DestinationSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.list.mimic.MimicRecordingSubListSelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.value.mimic.MimicRecordingValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEntity;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListValue;
import greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Custom filters must remain live even though the factory caches nearby distances for a phase. */
@Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class NearbyLiveFilterRegressionTest {

  @ParameterizedTest
  @CsvSource({"false, RANDOM", "true, RANDOM", "false, ORIGINAL", "true, ORIGINAL"})
  void entityFactoryAppliesLiveFilterBeforeCap(boolean eager, SelectionOrder order) {
    CountingDistanceMeter.calls.set(0);
    CurrentBasicChoiceFilter.calls.set(0);
    var policy = buildHeuristicConfigPolicy();
    var descriptor =
        policy.getSolutionDescriptor().findEntityDescriptorOrFail(TestdataEntity.class);
    var originEntity = new TestdataEntity("origin");
    var a = new TestdataEntity("a");
    var b = new TestdataEntity("b");
    var solution = new TestdataSolution();
    solution.setEntityList(List.of(originEntity, a, b));
    var aValue = new TestdataValue("a");
    var bValue = new TestdataValue("b");
    solution.setValueList(List.of(aValue, bValue));
    originEntity.setValue(bValue);
    var originChild = SelectorTestUtils.mockEntitySelector(descriptor, originEntity);
    var origin = new MimicRecordingEntitySelector<>(originChild);
    policy.addEntityMimicRecorder("origin", origin);
    var selector =
        EntitySelectorFactory.<TestdataSolution>create(
                new EntitySelectorConfig(TestdataEntity.class)
                    .withFilterClass(CurrentBasicChoiceFilter.class)
                    .withNearbySelectionConfig(
                        nearby(eager, order)
                            .withOriginEntitySelectorConfig(
                                new EntitySelectorConfig().withMimicSelectorRef("origin"))))
            .buildEntitySelector(policy, SelectionCacheType.JUST_IN_TIME, order);
    try (var lifecycle = start(policy, solution, null, origin, selector)) {
      verify(originChild, never()).iterator();
      assertThat(CurrentBasicChoiceFilter.calls).hasValue(0);
      origin.iterator().next();
      var iterator = selector.iterator();
      assertThat(iterator.next()).isSameAs(b);
      assertFiniteRemainder(iterator, order);
      int distanceCalls = CountingDistanceMeter.calls.get();
      assertThat(distanceCalls).isPositive();
      originEntity.setValue(aValue);
      if (order == SelectionOrder.ORIGINAL) {
        iterator = selector.iterator();
      }
      assertThat(iterator.next()).isSameAs(a);
      assertFiniteRemainder(iterator, order);
      originEntity.setValue(bValue);
      if (order == SelectionOrder.ORIGINAL) {
        iterator = selector.iterator();
      }
      assertThat(iterator.next()).isSameAs(b);
      assertFiniteRemainder(iterator, order);
      assertThat(CountingDistanceMeter.calls).hasValue(distanceCalls);
    }
  }

  @ParameterizedTest
  @CsvSource({"false, RANDOM", "true, RANDOM", "false, ORIGINAL", "true, ORIGINAL"})
  void valueFactoryAppliesLiveFilterBeforeCap(boolean eager, SelectionOrder order) {
    CountingDistanceMeter.calls.set(0);
    CurrentBasicChoiceFilter.calls.set(0);
    var policy = buildHeuristicConfigPolicy();
    var descriptor =
        policy.getSolutionDescriptor().findEntityDescriptorOrFail(TestdataEntity.class);
    var entity = new TestdataEntity("origin");
    var a = new TestdataValue("a");
    var b = new TestdataValue("b");
    var solution = new TestdataSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(a, b));
    entity.setValue(b);
    var originChild = SelectorTestUtils.mockEntitySelector(descriptor, entity);
    var origin = new MimicRecordingEntitySelector<>(originChild);
    policy.addEntityMimicRecorder("origin", origin);
    var selector =
        ValueSelectorFactory.<TestdataSolution>create(
                new ValueSelectorConfig("value")
                    .withFilterClass(CurrentBasicChoiceFilter.class)
                    .withNearbySelectionConfig(
                        nearby(eager, order)
                            .withOriginEntitySelectorConfig(
                                new EntitySelectorConfig().withMimicSelectorRef("origin"))))
            .buildValueSelector(policy, descriptor, SelectionCacheType.JUST_IN_TIME, order);
    try (var lifecycle = start(policy, solution, null, origin, selector)) {
      verify(originChild, never()).iterator();
      assertThat(CurrentBasicChoiceFilter.calls).hasValue(0);
      origin.iterator().next();
      var iterator = selector.iterator(entity);
      assertThat(iterator.next()).isSameAs(b);
      assertFiniteRemainder(iterator, order);
      int distanceCalls = CountingDistanceMeter.calls.get();
      assertThat(distanceCalls).isPositive();
      entity.setValue(a);
      if (order == SelectionOrder.ORIGINAL) {
        iterator = selector.iterator(entity);
      }
      assertThat(iterator.next()).isSameAs(a);
      assertFiniteRemainder(iterator, order);
      entity.setValue(b);
      if (order == SelectionOrder.ORIGINAL) {
        iterator = selector.iterator(entity);
      }
      assertThat(iterator.next()).isSameAs(b);
      assertFiniteRemainder(iterator, order);
      assertThat(CountingDistanceMeter.calls).hasValue(distanceCalls);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "false, false, RANDOM", "false, true, RANDOM", "true, false, RANDOM", "true, true, RANDOM",
    "false, false, ORIGINAL", "false, true, ORIGINAL", "true, false, ORIGINAL",
        "true, true, ORIGINAL"
  })
  void destinationFactoryRetainsLiveValueFilterAndMimicPopulation(
      boolean eager, boolean mimic, SelectionOrder order) {
    CountingDistanceMeter.calls.set(0);
    CurrentFirstListValueFilter.calls.set(0);
    var fixture = new ListFixture();
    var originChild =
        SelectorTestUtils.mockIterableValueSelector(
            fixture.policy.getSolutionDescriptor().getListVariableDescriptor(),
            fixture.originValue);
    var origin = new MimicRecordingValueSelector<>(originChild);
    fixture.policy.addValueMimicRecorder("origin", origin);
    var selectors = new ArrayList<Selector<TestdataListSolution>>();
    selectors.add(origin);
    var valueConfig = fixture.valueConfig(mimic, selectors);
    var selector =
        DestinationSelectorFactory.<TestdataListSolution>create(
                new DestinationSelectorConfig()
                    .withEntitySelectorConfig(new EntitySelectorConfig(TestdataListEntity.class))
                    .withValueSelectorConfig(valueConfig)
                    .withNearbySelectionConfig(
                        nearby(eager, order)
                            .withOriginValueSelectorConfig(
                                new ValueSelectorConfig().withMimicSelectorRef("origin"))))
            .buildDestinationSelector(
                fixture.policy, SelectionCacheType.JUST_IN_TIME, order == SelectionOrder.RANDOM);
    selectors.add(selector);
    try (var lifecycle = fixture.start(selectors)) {
      verify(originChild, never()).iterator();
      assertThat(CurrentFirstListValueFilter.calls).hasValue(0);
      origin.iterator().next();
      var iterator = selector.iterator();
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(fixture.target, 1));
      assertFiniteRemainder(
          iterator,
          order,
          ElementPosition.of(fixture.target, 0),
          ElementPosition.of(fixture.originEntity, 0));
      int distanceCalls = CountingDistanceMeter.calls.get();
      assertThat(distanceCalls).isPositive();
      fixture.swapTargetValues();
      if (order == SelectionOrder.ORIGINAL) {
        iterator = selector.iterator();
      }
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(fixture.target, 1));
      assertFiniteRemainder(
          iterator,
          order,
          ElementPosition.of(fixture.target, 0),
          ElementPosition.of(fixture.originEntity, 0));
      fixture.swapTargetValues();
      if (order == SelectionOrder.ORIGINAL) {
        iterator = selector.iterator();
      }
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(fixture.target, 1));
      assertFiniteRemainder(
          iterator,
          order,
          ElementPosition.of(fixture.target, 0),
          ElementPosition.of(fixture.originEntity, 0));
      assertThat(CountingDistanceMeter.calls).hasValue(distanceCalls);
    }
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void subListFactoryRetainsLiveValueFilterAndMimicPopulation(boolean eager, boolean mimic) {
    CountingDistanceMeter.calls.set(0);
    CurrentFirstListValueFilter.calls.set(0);
    var fixture = new ListFixture();
    var originChild = fixture.originSubList();
    var origin = new MimicRecordingSubListSelector<>(originChild);
    fixture.policy.addSubListMimicRecorder("origin", origin);
    var selectors = new ArrayList<Selector<TestdataListSolution>>();
    selectors.add(origin);
    var valueConfig = fixture.valueConfig(mimic, selectors);
    var entitySelector =
        EntitySelectorFactory.<TestdataListSolution>create(
                new EntitySelectorConfig(TestdataListEntity.class))
            .buildEntitySelector(
                fixture.policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM);
    var selector =
        SubListSelectorFactory.<TestdataListSolution>create(
                new SubListSelectorConfig()
                    .withMinimumSubListSize(1)
                    .withMaximumSubListSize(1)
                    .withValueSelectorConfig(valueConfig)
                    .withNearbySelectionConfig(
                        nearby(eager)
                            .withOriginSubListSelectorConfig(
                                new SubListSelectorConfig().withMimicSelectorRef("origin"))))
            .buildSubListSelector(
                fixture.policy,
                entitySelector,
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM);
    selectors.add(selector);
    try (var lifecycle = fixture.start(selectors)) {
      verify(originChild, never()).iterator();
      assertThat(CurrentFirstListValueFilter.calls).hasValue(0);
      origin.iterator().next();
      var iterator = selector.iterator();
      assertThat(iterator.next()).isEqualTo(new SubList(fixture.target, 0, 1));
      int distanceCalls = CountingDistanceMeter.calls.get();
      assertThat(distanceCalls).isPositive();
      fixture.swapTargetValues();
      assertThat(iterator.next()).isEqualTo(new SubList(fixture.target, 0, 1));
      fixture.swapTargetValues();
      assertThat(iterator.next()).isEqualTo(new SubList(fixture.target, 0, 1));
      assertThat(CountingDistanceMeter.calls).hasValue(distanceCalls);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void destinationFactoryRetainsLiveEntityFilter(boolean eager) {
    CountingDistanceMeter.calls.set(0);
    var fixture = new ListFixture();
    var origin =
        new MimicRecordingValueSelector<>(
            SelectorTestUtils.mockIterableValueSelector(
                fixture.policy.getSolutionDescriptor().getListVariableDescriptor(),
                fixture.originValue));
    fixture.policy.addValueMimicRecorder("origin", origin);
    var selector =
        DestinationSelectorFactory.<TestdataListSolution>create(
                new DestinationSelectorConfig()
                    .withEntitySelectorConfig(
                        new EntitySelectorConfig(TestdataListEntity.class)
                            .withFilterClass(CurrentOwnerOfAFilter.class))
                    .withValueSelectorConfig(
                        new ValueSelectorConfig("valueList")
                            .withFilterClass(RejectAllValuesFilter.class))
                    .withNearbySelectionConfig(
                        nearby(eager)
                            .withOriginValueSelectorConfig(
                                new ValueSelectorConfig().withMimicSelectorRef("origin"))))
            .buildDestinationSelector(fixture.policy, SelectionCacheType.JUST_IN_TIME, true);
    try (var lifecycle = fixture.start(List.of(origin, selector))) {
      origin.iterator().next();
      var iterator = selector.iterator();
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(fixture.target, 0));
      int distanceCalls = CountingDistanceMeter.calls.get();
      fixture.target.getValueList().remove(fixture.a);
      fixture.originEntity.getValueList().add(fixture.a);
      fixture.target.setUpShadowVariables();
      fixture.originEntity.setUpShadowVariables();
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(fixture.originEntity, 0));
      assertThat(CountingDistanceMeter.calls).hasValue(distanceCalls);
    }
  }

  @Test
  void subListCustomFilterOnlyTestsTheFirstValue() {
    var fixture = new ListFixture();
    var origin = new MimicRecordingSubListSelector<>(fixture.originSubList());
    fixture.policy.addSubListMimicRecorder("origin", origin);
    var entitySelector =
        EntitySelectorFactory.<TestdataListSolution>create(
                new EntitySelectorConfig(TestdataListEntity.class))
            .buildEntitySelector(
                fixture.policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM);
    var selector =
        SubListSelectorFactory.<TestdataListSolution>create(
                new SubListSelectorConfig()
                    .withMinimumSubListSize(2)
                    .withMaximumSubListSize(2)
                    .withValueSelectorConfig(
                        new ValueSelectorConfig("valueList")
                            .withFilterClass(CurrentFirstListValueFilter.class))
                    .withNearbySelectionConfig(
                        nearby(false)
                            .withOriginSubListSelectorConfig(
                                new SubListSelectorConfig().withMimicSelectorRef("origin"))))
            .buildSubListSelector(
                fixture.policy,
                entitySelector,
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM);
    try (var lifecycle = fixture.start(List.of(origin, selector))) {
      origin.iterator().next();
      // b is accepted, a is rejected; a may still be the second member of the selected sublist.
      assertThat(selector.iterator().next()).isEqualTo(new SubList(fixture.target, 0, 2));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void originalValueIteratorRechecksNullEligibility(boolean initiallyAllowed) {
    var policy =
        buildHeuristicConfigPolicy(
            TestdataAllowsUnassignedEntityProvidingSolution.buildSolutionDescriptor());
    var descriptor =
        policy
            .getSolutionDescriptor()
            .findEntityDescriptorOrFail(TestdataAllowsUnassignedEntityProvidingEntity.class);
    var a = new TestdataValue("a");
    var entity = new TestdataAllowsUnassignedEntityProvidingEntity("origin", List.of(a));
    entity.setValue(initiallyAllowed ? null : a);
    var solution = new TestdataAllowsUnassignedEntityProvidingSolution();
    solution.setEntityList(List.of(entity));
    var origin =
        new MimicRecordingEntitySelector<>(
            SelectorTestUtils.mockEntitySelector(descriptor, entity));
    policy.addEntityMimicRecorder("origin", origin);
    var selector =
        ValueSelectorFactory.<TestdataAllowsUnassignedEntityProvidingSolution>create(
                new ValueSelectorConfig("value")
                    .withFilterClass(NullWhileUnassignedFilter.class)
                    .withNearbySelectionConfig(
                        nearby(false, SelectionOrder.ORIGINAL)
                            .withOriginEntitySelectorConfig(
                                new EntitySelectorConfig().withMimicSelectorRef("origin"))))
            .buildValueSelector(
                policy, descriptor, SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL);
    try (var lifecycle = start(policy, solution, null, origin, selector)) {
      origin.iterator().next();
      var iterator = selector.iterator(entity);
      assertThat(iterator.next()).isSameAs(a);
      entity.setValue(initiallyAllowed ? a : null);
      assertThat(iterator.hasNext()).isEqualTo(!initiallyAllowed);
      if (!initiallyAllowed) {
        assertThat(iterator.next()).isNull();
      }
      assertThat(iterator.hasNext()).isFalse();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void destinationFactoryCombinesPinnedPrefixAndCustomFilterBeforeCap() {
    var policy =
        buildHeuristicConfigPolicy(TestdataPinnedWithIndexListSolution.buildSolutionDescriptor());
    var descriptor = policy.getSolutionDescriptor().getListVariableDescriptor();
    var pinned = new TestdataPinnedWithIndexListValue("pinned");
    var rejected = new TestdataPinnedWithIndexListValue("a");
    var accepted = new TestdataPinnedWithIndexListValue("b");
    var entity =
        TestdataPinnedWithIndexListEntity.createWithValues("target", pinned, rejected, accepted);
    entity.setPinIndex(1);
    var solution = new TestdataPinnedWithIndexListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(pinned, rejected, accepted));
    var origin =
        new MimicRecordingValueSelector<>(
            SelectorTestUtils.mockIterableValueSelector(descriptor, rejected));
    policy.addValueMimicRecorder("origin", origin);
    var selector =
        DestinationSelectorFactory.<TestdataPinnedWithIndexListSolution>create(
                new DestinationSelectorConfig()
                    .withEntitySelectorConfig(
                        new EntitySelectorConfig(TestdataPinnedWithIndexListEntity.class))
                    .withValueSelectorConfig(
                        new ValueSelectorConfig("valueList").withFilterClass(PinnedOrBFilter.class))
                    .withNearbySelectionConfig(
                        nearby(true)
                            .withOriginValueSelectorConfig(
                                new ValueSelectorConfig().withMimicSelectorRef("origin"))))
            .buildDestinationSelector(policy, SelectionCacheType.JUST_IN_TIME, true);
    ListVariableState<TestdataPinnedWithIndexListSolution, Object, Object> state =
        mock(ListVariableState.class);
    when(state.getElementPosition(any()))
        .thenAnswer(
            invocation ->
                ElementPosition.of(
                    entity, entity.getValueList().indexOf(invocation.getArgument(0))));
    when(state.getInverseSingleton(any())).thenReturn(entity);
    when(state.isPinned(pinned)).thenReturn(true);
    when(state.getIndexOrElse(any(), anyInt()))
        .thenAnswer(invocation -> entity.getValueList().indexOf(invocation.getArgument(0)));
    try (var lifecycle = start(policy, solution, state, origin, selector)) {
      origin.iterator().next();
      assertThat(selector.iterator().next()).isEqualTo(ElementPosition.of(entity, 3));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void requiredListDestinationDuringConstructionRechecksAssignmentsBeforeCap(boolean eager) {
    CountingDistanceMeter.calls.set(0);
    var fixture = new ListFixture(true);
    fixture.target.getValueList().remove(fixture.a);
    fixture.a.setEntity(null);
    fixture.a.setIndex(null);
    fixture.target.setUpShadowVariables();
    var origin =
        new MimicRecordingValueSelector<>(
            SelectorTestUtils.mockIterableValueSelector(
                fixture.policy.getSolutionDescriptor().getListVariableDescriptor(),
                fixture.originValue));
    fixture.policy.addValueMimicRecorder("origin", origin);
    var selector =
        DestinationSelectorFactory.<TestdataListSolution>create(
                new DestinationSelectorConfig()
                    .withEntitySelectorConfig(new EntitySelectorConfig(TestdataListEntity.class))
                    .withValueSelectorConfig(new ValueSelectorConfig("valueList"))
                    .withNearbySelectionConfig(
                        nearby(eager)
                            .withOriginValueSelectorConfig(
                                new ValueSelectorConfig().withMimicSelectorRef("origin"))))
            .buildDestinationSelector(fixture.policy, SelectionCacheType.JUST_IN_TIME, true);
    try (var lifecycle = fixture.start(List.of(origin, selector))) {
      origin.iterator().next();
      var iterator = selector.iterator();
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(fixture.target, 1));
      int distanceCalls = CountingDistanceMeter.calls.get();
      fixture.target.getValueList().add(fixture.a);
      fixture.target.setUpShadowVariables();
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(fixture.target, 2));
      assertThat(CountingDistanceMeter.calls).hasValue(distanceCalls);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void destinationValueCountLimitTracksTheFilteredSourcePrefix(int limit) {
    CountingDistanceMeter.calls.set(0);
    var fixture = new ListFixture();
    fixture.solution.setValueList(List.of(fixture.b, fixture.a, fixture.originValue));
    var origin =
        new MimicRecordingValueSelector<>(
            SelectorTestUtils.mockIterableValueSelector(
                fixture.policy.getSolutionDescriptor().getListVariableDescriptor(),
                fixture.originValue));
    fixture.policy.addValueMimicRecorder("origin", origin);
    var selector =
        DestinationSelectorFactory.<TestdataListSolution>create(
                new DestinationSelectorConfig()
                    .withEntitySelectorConfig(
                        new EntitySelectorConfig(TestdataListEntity.class)
                            .withFilterClass(RejectAllValuesFilter.class))
                    .withValueSelectorConfig(
                        new ValueSelectorConfig("valueList")
                            .withFilterClass(CurrentFirstOrAFilter.class)
                            .withSelectedCountLimit(limit))
                    .withNearbySelectionConfig(
                        nearby(true)
                            .withOriginValueSelectorConfig(
                                new ValueSelectorConfig().withMimicSelectorRef("origin"))))
            .buildDestinationSelector(fixture.policy, SelectionCacheType.JUST_IN_TIME, true);
    try (var lifecycle = fixture.start(List.of(origin, selector))) {
      origin.iterator().next();
      var iterator = selector.iterator();
      assertThat(iterator.hasNext()).isEqualTo(limit > 0);
      if (limit > 0) {
        // b precedes a in the source population, although a is geographically closer.
        assertThat(iterator.next()).isEqualTo(ElementPosition.of(fixture.target, 1));
      }
      int distanceCalls = CountingDistanceMeter.calls.get();
      fixture.swapTargetValues();
      iterator = selector.iterator();
      assertThat(iterator.hasNext()).isEqualTo(limit > 0);
      if (limit > 0) {
        // b is now rejected; a becomes the first accepted source value.
        assertThat(iterator.next()).isEqualTo(ElementPosition.of(fixture.target, 1));
      }
      assertThat(CountingDistanceMeter.calls).hasValue(distanceCalls);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void subListValueCountLimitTracksTheFilteredSourcePrefix(int limit) {
    CountingDistanceMeter.calls.set(0);
    var fixture = new ListFixture();
    fixture.solution.setValueList(List.of(fixture.b, fixture.a, fixture.originValue));
    var origin = new MimicRecordingSubListSelector<>(fixture.originSubList());
    fixture.policy.addSubListMimicRecorder("origin", origin);
    var entitySelector =
        EntitySelectorFactory.<TestdataListSolution>create(
                new EntitySelectorConfig(TestdataListEntity.class))
            .buildEntitySelector(
                fixture.policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM);
    var selector =
        SubListSelectorFactory.<TestdataListSolution>create(
                new SubListSelectorConfig()
                    .withMinimumSubListSize(1)
                    .withMaximumSubListSize(1)
                    .withValueSelectorConfig(
                        new ValueSelectorConfig("valueList")
                            .withFilterClass(CurrentFirstOrAFilter.class)
                            .withSelectedCountLimit(limit))
                    .withNearbySelectionConfig(
                        nearby(true)
                            .withOriginSubListSelectorConfig(
                                new SubListSelectorConfig().withMimicSelectorRef("origin"))))
            .buildSubListSelector(
                fixture.policy,
                entitySelector,
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM);
    try (var lifecycle = fixture.start(List.of(origin, selector))) {
      origin.iterator().next();
      var iterator = selector.iterator();
      assertThat(iterator.hasNext()).isEqualTo(limit > 0);
      if (limit > 0) {
        assertThat(iterator.next()).isEqualTo(new SubList(fixture.target, 0, 1));
      }
      int distanceCalls = CountingDistanceMeter.calls.get();
      fixture.swapTargetValues();
      iterator = selector.iterator();
      assertThat(iterator.hasNext()).isEqualTo(limit > 0);
      if (limit > 0) {
        assertThat(iterator.next()).isEqualTo(new SubList(fixture.target, 0, 1));
      }
      assertThat(CountingDistanceMeter.calls).hasValue(distanceCalls);
    }
  }

  @ParameterizedTest
  @CsvSource({"0, ORIGINAL", "1, ORIGINAL", "0, RANDOM", "1, RANDOM"})
  void destinationEntityCountLimitPreservesChildSelectionOrder(int limit, SelectionOrder order) {
    CountingDistanceMeter.calls.set(0);
    var fixture = new ListFixture();
    var origin =
        new MimicRecordingValueSelector<>(
            SelectorTestUtils.mockIterableValueSelector(
                fixture.policy.getSolutionDescriptor().getListVariableDescriptor(),
                fixture.originValue));
    fixture.policy.addValueMimicRecorder("origin", origin);
    var selector =
        DestinationSelectorFactory.<TestdataListSolution>create(
                new DestinationSelectorConfig()
                    .withEntitySelectorConfig(
                        new EntitySelectorConfig(TestdataListEntity.class)
                            .withFilterClass(CurrentOwnerOfAOrOriginEntityFilter.class)
                            .withSelectedCountLimit(limit))
                    .withValueSelectorConfig(
                        new ValueSelectorConfig("valueList")
                            .withFilterClass(RejectAllValuesFilter.class))
                    .withNearbySelectionConfig(
                        nearby(true, order)
                            .withNearbyDistanceMeterClass(OriginEntityFirstDistanceMeter.class)
                            .withOriginValueSelectorConfig(
                                new ValueSelectorConfig().withMimicSelectorRef("origin"))))
            .buildDestinationSelector(
                fixture.policy, SelectionCacheType.JUST_IN_TIME, order == SelectionOrder.RANDOM);
    try (var lifecycle = fixture.start(List.of(origin, selector))) {
      origin.iterator().next();
      var iterator = selector.iterator();
      boolean hasSelection = order == SelectionOrder.RANDOM || limit > 0;
      assertThat(iterator.hasNext()).isEqualTo(hasSelection);
      if (hasSelection) {
        // ORIGINAL honors the source prefix; RANDOM deliberately ignores the child entity limit.
        var expected = order == SelectionOrder.ORIGINAL ? fixture.target : fixture.originEntity;
        assertThat(iterator.next()).isEqualTo(ElementPosition.of(expected, 0));
        assertFiniteRemainder(iterator, order);
      }
      int distanceCalls = CountingDistanceMeter.calls.get();
      fixture.target.getValueList().remove(fixture.a);
      fixture.originEntity.getValueList().add(fixture.a);
      fixture.target.setUpShadowVariables();
      fixture.originEntity.setUpShadowVariables();
      iterator = selector.iterator();
      assertThat(iterator.hasNext()).isEqualTo(hasSelection);
      if (hasSelection) {
        assertThat(iterator.next()).isEqualTo(ElementPosition.of(fixture.originEntity, 0));
        assertFiniteRemainder(iterator, order);
      }
      assertThat(CountingDistanceMeter.calls).hasValue(distanceCalls);
    }
  }

  private static NearbySelectionConfig nearby(boolean eager) {
    return new NearbySelectionConfig()
        .withNearbyDistanceMeterClass(CountingDistanceMeter.class)
        .withNearbySelectionDistributionType(NearbySelectionDistributionType.BLOCK_DISTRIBUTION)
        .withBlockDistributionSizeMinimum(1)
        .withBlockDistributionSizeMaximum(1)
        .withBlockDistributionUniformDistributionProbability(0.0)
        .withMaxNearbySortSize(1)
        .withEagerInitialization(eager);
  }

  private static NearbySelectionConfig nearby(boolean eager, SelectionOrder order) {
    return order == SelectionOrder.RANDOM
        ? nearby(eager)
        : new NearbySelectionConfig()
            .withNearbyDistanceMeterClass(CountingDistanceMeter.class)
            .withEagerInitialization(eager);
  }

  @SafeVarargs
  private static <T> void assertFiniteRemainder(
      Iterator<T> iterator, SelectionOrder order, T... expected) {
    if (order == SelectionOrder.ORIGINAL) {
      var remainder = new ArrayList<T>();
      iterator.forEachRemaining(remainder::add);
      assertThat(remainder).containsExactly(expected);
      assertThat(iterator.hasNext()).isFalse();
    }
  }

  private static final class ListFixture {
    final HeuristicConfigPolicy<TestdataListSolution> policy;
    final TestdataListValue a = new TestdataListValue("a");
    final TestdataListValue b = new TestdataListValue("b");
    final TestdataListValue originValue = new TestdataListValue("origin");
    final TestdataListEntity target = TestdataListEntity.createWithValues("target", b, a);
    final TestdataListEntity originEntity =
        TestdataListEntity.createWithValues("originEntity", originValue);
    final TestdataListSolution solution = new TestdataListSolution();

    ListFixture() {
      this(false);
    }

    ListFixture(boolean unassignedValuesAllowed) {
      policy =
          buildHeuristicConfigPolicy(TestdataListSolution.buildSolutionDescriptor())
              .cloneBuilder()
              .withUnassignedValuesAllowed(unassignedValuesAllowed)
              .build();
      solution.setEntityList(List.of(target, originEntity));
      solution.setValueList(List.of(a, b, originValue));
    }

    ValueSelectorConfig valueConfig(boolean mimic, List<Selector<TestdataListSolution>> selectors) {
      var valueConfig =
          new ValueSelectorConfig("valueList").withFilterClass(CurrentFirstListValueFilter.class);
      if (!mimic) {
        return valueConfig;
      }
      var recorder =
          ValueSelectorFactory.<TestdataListSolution>create(valueConfig.withId("candidates"))
              .buildValueSelector(
                  policy,
                  policy
                      .getSolutionDescriptor()
                      .findEntityDescriptorOrFail(TestdataListEntity.class),
                  SelectionCacheType.JUST_IN_TIME,
                  SelectionOrder.ORIGINAL);
      selectors.add(recorder);
      // No value is recorded: building the matrix must use the recorder's finite population.
      return new ValueSelectorConfig().withMimicSelectorRef("candidates");
    }

    @SuppressWarnings("unchecked")
    SubListSelector<TestdataListSolution> originSubList() {
      SubListSelector<TestdataListSolution> selector = mock(SubListSelector.class);
      when(selector.getVariableDescriptor())
          .thenReturn(policy.getSolutionDescriptor().getListVariableDescriptor());
      when(selector.getValueCount()).thenReturn(1L);
      when(selector.endingValueIterator())
          .thenAnswer(ignored -> List.<Object>of(originValue).iterator());
      when(selector.iterator())
          .thenAnswer(ignored -> List.of(new SubList(originEntity, 0, 1)).iterator());
      return selector;
    }

    void swapTargetValues() {
      Collections.swap(target.getValueList(), 0, 1);
      target.setUpShadowVariables();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Lifecycle<TestdataListSolution> start(List<Selector<TestdataListSolution>> selectors) {
      ListVariableState<TestdataListSolution, Object, Object> state = mock(ListVariableState.class);
      when(state.getElementPosition(any()))
          .thenAnswer(
              invocation -> {
                var value = (TestdataListValue) invocation.getArgument(0);
                return value.getEntity() == null
                    ? ElementPosition.unassigned()
                    : ElementPosition.of(value.getEntity(), value.getIndex());
              });
      when(state.getInverseSingleton(any()))
          .thenAnswer(invocation -> ((TestdataListValue) invocation.getArgument(0)).getEntity());
      when(state.getIndexOrElse(any(), anyInt()))
          .thenAnswer(
              invocation -> {
                var index = ((TestdataListValue) invocation.getArgument(0)).getIndex();
                return index == null ? invocation.getArgument(1) : index;
              });
      return NearbyLiveFilterRegressionTest.start(
          policy, solution, state, selectors.toArray(Selector[]::new));
    }
  }

  @SafeVarargs
  private static <Solution_> Lifecycle<Solution_> start(
      HeuristicConfigPolicy<Solution_> policy,
      Solution_ solution,
      ListVariableState<Solution_, Object, Object> state,
      Selector<Solution_>... selectors) {
    InnerScoreDirector<Solution_, ?> director = mock(InnerScoreDirector.class);
    NearbyTestUtils.mockSupplyManager(director, state);
    when(director.getSolutionDescriptor()).thenReturn(policy.getSolutionDescriptor());
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

  public static final class CurrentBasicChoiceFilter
      implements SelectionFilter<TestdataSolution, Object> {
    static final AtomicInteger calls = new AtomicInteger();

    @Override
    public boolean accept(ScoreDirector<TestdataSolution> scoreDirector, Object selection) {
      calls.incrementAndGet();
      return ((TestdataObject) selection)
          .getCode()
          .equals(
              scoreDirector.getWorkingSolution().getEntityList().getFirst().getValue().getCode());
    }
  }

  public static final class CurrentFirstListValueFilter
      implements SelectionFilter<TestdataListSolution, Object> {
    static final AtomicInteger calls = new AtomicInteger();

    @Override
    public boolean accept(ScoreDirector<TestdataListSolution> scoreDirector, Object selection) {
      calls.incrementAndGet();
      return selection
          == scoreDirector
              .getWorkingSolution()
              .getEntityList()
              .getFirst()
              .getValueList()
              .getFirst();
    }
  }

  public static final class CurrentOwnerOfAFilter
      implements SelectionFilter<TestdataListSolution, Object> {
    @Override
    public boolean accept(ScoreDirector<TestdataListSolution> scoreDirector, Object selection) {
      return ((TestdataListEntity) selection)
          .getValueList().stream().anyMatch(value -> value.getCode().equals("a"));
    }
  }

  public static final class CurrentFirstOrAFilter
      implements SelectionFilter<TestdataListSolution, Object> {
    @Override
    public boolean accept(ScoreDirector<TestdataListSolution> scoreDirector, Object selection) {
      return ((TestdataListValue) selection).getCode().equals("a")
          || selection
              == scoreDirector
                  .getWorkingSolution()
                  .getEntityList()
                  .getFirst()
                  .getValueList()
                  .getFirst();
    }
  }

  public static final class CurrentOwnerOfAOrOriginEntityFilter
      implements SelectionFilter<TestdataListSolution, Object> {
    @Override
    public boolean accept(ScoreDirector<TestdataListSolution> scoreDirector, Object selection) {
      var entity = (TestdataListEntity) selection;
      return entity.getCode().equals("originEntity")
          || entity.getValueList().stream().anyMatch(value -> value.getCode().equals("a"));
    }
  }

  public static final class RejectAllValuesFilter
      implements SelectionFilter<TestdataListSolution, Object> {
    @Override
    public boolean accept(ScoreDirector<TestdataListSolution> scoreDirector, Object selection) {
      return false;
    }
  }

  public static final class NullWhileUnassignedFilter
      implements SelectionFilter<TestdataAllowsUnassignedEntityProvidingSolution, Object> {
    @Override
    public boolean accept(
        ScoreDirector<TestdataAllowsUnassignedEntityProvidingSolution> scoreDirector,
        Object selection) {
      return selection != null
          || scoreDirector.getWorkingSolution().getEntityList().getFirst().getValue() == null;
    }
  }

  public static final class PinnedOrBFilter
      implements SelectionFilter<TestdataPinnedWithIndexListSolution, Object> {
    @Override
    public boolean accept(
        ScoreDirector<TestdataPinnedWithIndexListSolution> scoreDirector, Object selection) {
      var code = ((TestdataPinnedWithIndexListValue) selection).getCode();
      return code.equals("pinned") || code.equals("b");
    }
  }

  public static final class CountingDistanceMeter implements NearbyDistanceMeter<Object, Object> {
    static final AtomicInteger calls = new AtomicInteger();

    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      calls.incrementAndGet();
      return switch (((TestdataObject) destination).getCode()) {
        case "pinned" -> 0;
        case "a" -> 1;
        case "b" -> 2;
        case "target" -> 10;
        case "originEntity" -> 20;
        default -> 100;
      };
    }
  }

  public static final class OriginEntityFirstDistanceMeter
      implements NearbyDistanceMeter<Object, Object> {
    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      CountingDistanceMeter.calls.incrementAndGet();
      return ((TestdataObject) destination).getCode().equals("originEntity") ? 0 : 1;
    }
  }
}
