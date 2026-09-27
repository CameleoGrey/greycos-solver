package greycos.solver.core.impl.heuristic.selector.value.nearby;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.Selector;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyTestUtils;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory.ListValueFilteringType;
import greycos.solver.core.impl.heuristic.selector.value.mimic.MimicRecordingValueSelector;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.list.pinned.unassignedvar.TestdataPinnedAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.pinned.unassignedvar.TestdataPinnedAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.pinned.unassignedvar.TestdataPinnedAllowsUnassignedValuesListValue;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class NearbyValueRangeRegressionTest {

  @Test
  void rangeSourceIsIndependentOfDistanceOriginAndRefreshesAfterMoves() throws Exception {
    var policy =
        buildHeuristicConfigPolicy(TestdataListEntityProvidingSolution.buildSolutionDescriptor());
    var descriptor =
        policy
            .getSolutionDescriptor()
            .findEntityDescriptorOrFail(TestdataListEntityProvidingEntity.class);
    var variable = descriptor.getListVariableDescriptor();
    var source = new TestdataListEntityProvidingValue("source");
    var near = new TestdataListEntityProvidingValue("near");
    var far = new TestdataListEntityProvidingValue("far");
    var a =
        new TestdataListEntityProvidingEntity(
            "a", List.of(source, far), new ArrayList<>(List.of(source)));
    var b =
        new TestdataListEntityProvidingEntity(
            "b", List.of(source, near, far), new ArrayList<>(List.of(near, far)));
    var c = new TestdataListEntityProvidingEntity("c", List.of(source, near), new ArrayList<>());
    var solution = new TestdataListEntityProvidingSolution();
    solution.setEntityList(List.of(a, b, c));
    var sourceRecorder =
        new MimicRecordingValueSelector<>(
            SelectorTestUtils.mockIterableValueSelector(variable, source));
    var distanceRecorder =
        new MimicRecordingValueSelector<>(
            SelectorTestUtils.mockIterableValueSelector(variable, near));
    policy.addValueMimicRecorder("source", sourceRecorder);
    policy.addValueMimicRecorder("distance", distanceRecorder);
    var selector =
        (IterableValueSelector<TestdataListEntityProvidingSolution>)
            ValueSelectorFactory.<TestdataListEntityProvidingSolution>create(nearbyConfig())
                .buildValueSelector(
                    policy,
                    descriptor,
                    SelectionCacheType.JUST_IN_TIME,
                    SelectionOrder.RANDOM,
                    false,
                    ListValueFilteringType.NONE,
                    "source",
                    true);
    ListVariableState<TestdataListEntityProvidingSolution, Object, Object> state =
        mock(ListVariableState.class);
    when(state.getInverseSingleton(any()))
        .thenAnswer(call -> ((TestdataListEntityProvidingValue) call.getArgument(0)).getEntity());
    try (var lifecycle = start(policy, solution, selector, state)) {
      sourceRecorder.iterator().next();
      distanceRecorder.iterator().next();
      var iterator = selector.iterator();
      assertThat(iterator.next()).isSameAs(far);
      // Same source and distance origin, but a new owner changes the reverse range check.
      a.getValueList().clear();
      c.getValueList().add(source);
      source.setEntity(c);
      assertThat(iterator.next()).isSameAs(near);
    }
  }

  @Test
  void pinnedAndUnassignedValuesDoNotConsumeAnAssignedNeighborhoodCap() throws Exception {
    var policy =
        buildHeuristicConfigPolicy(
            TestdataPinnedAllowsUnassignedValuesListSolution.buildSolutionDescriptor());
    var descriptor =
        policy
            .getSolutionDescriptor()
            .findEntityDescriptorOrFail(TestdataPinnedAllowsUnassignedValuesListEntity.class);
    var variable = descriptor.getListVariableDescriptor();
    var source = new TestdataPinnedAllowsUnassignedValuesListValue("source");
    var pinned = new TestdataPinnedAllowsUnassignedValuesListValue("near");
    var unassigned = new TestdataPinnedAllowsUnassignedValuesListValue("unassigned");
    var far = new TestdataPinnedAllowsUnassignedValuesListValue("far");
    var pinnedEntity = new TestdataPinnedAllowsUnassignedValuesListEntity("pinned", pinned);
    pinnedEntity.setPinned(true);
    var entity = new TestdataPinnedAllowsUnassignedValuesListEntity("entity", source, far);
    pinned.setEntity(pinnedEntity);
    far.setEntity(entity);
    source.setEntity(entity);
    var solution = new TestdataPinnedAllowsUnassignedValuesListSolution();
    solution.setEntityList(List.of(pinnedEntity, entity));
    solution.setValueList(List.of(source, pinned, unassigned, far));
    var recorder =
        new MimicRecordingValueSelector<>(
            SelectorTestUtils.mockIterableValueSelector(variable, source));
    policy.addValueMimicRecorder("distance", recorder);
    var selector =
        (IterableValueSelector<TestdataPinnedAllowsUnassignedValuesListSolution>)
            ValueSelectorFactory.<TestdataPinnedAllowsUnassignedValuesListSolution>create(
                    nearbyConfig())
                .buildValueSelector(
                    policy,
                    descriptor,
                    SelectionCacheType.JUST_IN_TIME,
                    SelectionOrder.RANDOM,
                    false,
                    ListValueFilteringType.ACCEPT_ASSIGNED);
    ListVariableState<TestdataPinnedAllowsUnassignedValuesListSolution, Object, Object> state =
        mock(ListVariableState.class);
    when(state.isPinned(pinned)).thenReturn(true);
    when(state.isAssigned(pinned)).thenReturn(true);
    when(state.isAssigned(far)).thenReturn(true);
    when(state.isAssigned(source)).thenReturn(true);
    try (var lifecycle = start(policy, solution, selector, state)) {
      recorder.iterator().next();
      assertThat(selector.iterator().next()).isSameAs(far);
    }
  }

  private static ValueSelectorConfig nearbyConfig() {
    return new ValueSelectorConfig()
        .withVariableName("valueList")
        .withNearbySelectionConfig(
            new NearbySelectionConfig()
                .withOriginValueSelectorConfig(
                    new ValueSelectorConfig().withMimicSelectorRef("distance"))
                .withNearbyDistanceMeterClass(CodeDistanceMeter.class)
                .withMaxNearbySortSize(1));
  }

  private static <Solution_> AutoCloseable start(
      HeuristicConfigPolicy<Solution_> policy,
      Solution_ solution,
      Selector<Solution_> selector,
      ListVariableState<Solution_, Object, Object> state) {
    InnerScoreDirector<Solution_, ?> director = mock(InnerScoreDirector.class);
    NearbyTestUtils.mockSupplyManager(director, state);
    when(director.getWorkingSolution()).thenReturn(solution);
    doReturn(ValueRangeManager.of(policy.getSolutionDescriptor(), solution))
        .when(director)
        .getValueRangeManager();
    var scope = SelectorTestUtils.solvingStarted(selector, director, new Random(0));
    var phase = PlannerTestUtils.delegatingPhaseScope(scope);
    selector.phaseStarted(phase);
    return () -> {
      selector.phaseEnded(phase);
      selector.solvingEnded(scope);
    };
  }

  public static class CodeDistanceMeter
      implements NearbyDistanceMeter<TestdataObject, TestdataObject> {
    @Override
    public double getNearbyDistance(TestdataObject origin, TestdataObject destination) {
      return switch (destination.getCode()) {
        case "near" -> 0;
        case "unassigned" -> 1;
        case "far" -> 10;
        default -> 20;
      };
    }
  }
}
