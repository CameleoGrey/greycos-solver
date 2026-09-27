package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.solvingStarted;
import static greycos.solver.core.testcotwin.list.TestdataListUtils.getListVariableDescriptor;
import static greycos.solver.core.testutil.PlannerTestUtils.mockScoreDirector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.list.DestinationSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.list.mimic.MimicRecordingSubListSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NearbyListEmptyOriginTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void listChangesRecreateTheExhaustedDestinationForTheNextOrigin(boolean subListChange) {
    var isolated = new TestdataListValue("isolated");
    var source = new TestdataListValue("source");
    var entity = TestdataListEntity.createWithValues("entity", isolated, source);
    var solution = new TestdataListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(isolated, source));
    var director = mockScoreDirector(TestdataListSolution.buildSolutionDescriptor());
    director.setWorkingSolution(solution);
    var descriptor = getListVariableDescriptor(director);
    var selectedOrigin = new AtomicReference<Object>();
    IterableValueSelector<TestdataListSolution> values = mock(IterableValueSelector.class);
    when(values.iterator())
        .thenAnswer(
            ignored ->
                new Iterator<Object>() {
                  private final Iterator<Object> values =
                      List.<Object>of(isolated, source).iterator();

                  @Override
                  public boolean hasNext() {
                    return values.hasNext();
                  }

                  @Override
                  public Object next() {
                    var value = values.next();
                    selectedOrigin.set(value);
                    return value;
                  }
                });
    DestinationSelector<TestdataListSolution> destinations = mock(DestinationSelector.class);
    when(destinations.getSize()).thenReturn(3L);
    when(destinations.iterator())
        .thenAnswer(
            ignored ->
                new UpcomingSelectionIterator<ElementPosition>() {
                  @Override
                  protected ElementPosition createUpcomingSelection() {
                    return selectedOrigin.get() == source
                        ? ElementPosition.of(entity, 0)
                        : noUpcomingSelection();
                  }
                });
    Iterator<?> iterator;
    if (subListChange) {
      SubListSelector<TestdataListSolution> subLists = mock(SubListSelector.class);
      when(subLists.getVariableDescriptor()).thenReturn(descriptor);
      when(subLists.iterator())
          .thenAnswer(
              ignored ->
                  new Iterator<SubList>() {
                    private final Iterator<Object> originValues = values.iterator();

                    @Override
                    public boolean hasNext() {
                      return originValues.hasNext();
                    }

                    @Override
                    public SubList next() {
                      return new SubList(entity, originValues.next() == isolated ? 0 : 1, 1);
                    }
                  });
      iterator =
          new RandomSubListChangeMoveIterator<>(subLists, destinations, new Random(0), false, true);
    } else {
      iterator =
          new RandomListChangeIterator<>(
              director.getListVariableState(descriptor), values, destinations, true);
    }
    assertThat(iterator.next()).isSameAs(SelectorBasedNoChangeMove.getInstance());
    assertThat(iterator.next())
        .isInstanceOf(
            subListChange
                ? SelectorBasedSubListChangeMove.class
                : SelectorBasedListChangeMove.class);
    assertThat(iterator.hasNext()).isFalse();
    director.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void globallyEmptyListSwapSecondaryRemainsExhausted(boolean originDependent) {
    IterableValueSelector<TestdataListSolution> left = mock(IterableValueSelector.class);
    when(left.iterator())
        .thenAnswer(ignored -> List.<Object>of(new TestdataListValue("source")).iterator());
    IterableValueSelector<TestdataListSolution> right = mock(IterableValueSelector.class);
    when(right.getSize()).thenReturn(0L);
    when(right.iterator()).thenAnswer(ignored -> List.of().iterator());
    var iterator = new RandomListSwapIterator<>(null, left, right, originDependent);
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void listSwapRecreatesTheExhaustedSecondaryForTheNextOrigin() {
    var isolated = new TestdataListValue("isolated");
    var source = new TestdataListValue("source");
    var destination = new TestdataListValue("destination");
    var entity = TestdataListEntity.createWithValues("entity", isolated, source, destination);
    var solution = new TestdataListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(isolated, source, destination));
    var director = mockScoreDirector(TestdataListSolution.buildSolutionDescriptor());
    director.setWorkingSolution(solution);
    var descriptor = getListVariableDescriptor(director);
    var selectedOrigin = new AtomicReference<Object>();
    IterableValueSelector<TestdataListSolution> left = mock(IterableValueSelector.class);
    when(left.iterator())
        .thenAnswer(
            ignored ->
                new Iterator<Object>() {
                  private final Iterator<Object> values =
                      List.<Object>of(isolated, source).iterator();

                  @Override
                  public boolean hasNext() {
                    return values.hasNext();
                  }

                  @Override
                  public Object next() {
                    var value = values.next();
                    selectedOrigin.set(value);
                    return value;
                  }
                });
    IterableValueSelector<TestdataListSolution> right = mock(IterableValueSelector.class);
    when(right.getSize()).thenReturn(3L);
    when(right.iterator())
        .thenAnswer(
            ignored ->
                new UpcomingSelectionIterator<Object>() {
                  @Override
                  protected Object createUpcomingSelection() {
                    return selectedOrigin.get() == source ? destination : noUpcomingSelection();
                  }
                });
    var iterator =
        new RandomListSwapIterator<>(director.getListVariableState(descriptor), left, right, true);
    assertThat(iterator.next()).isSameAs(SelectorBasedNoChangeMove.getInstance());
    assertThat(iterator.next()).isInstanceOf(SelectorBasedListSwapMove.class);
    assertThat(iterator.hasNext()).isFalse();
    director.close();
  }

  @Test
  void nearbySubListMinimumTwoDoesNotTurnAnEmptyOriginIntoAnIllegalSubList() {
    var isolatedValue = new TestdataListEntityProvidingValue("isolated");
    var x = new TestdataListEntityProvidingValue("x");
    var y = new TestdataListEntityProvidingValue("y");
    var z = new TestdataListEntityProvidingValue("z");
    var isolated =
        new TestdataListEntityProvidingEntity(
            "isolated", List.of(isolatedValue), List.of(isolatedValue));
    var source = new TestdataListEntityProvidingEntity("source", List.of(x, y, z), List.of(x));
    var destination =
        new TestdataListEntityProvidingEntity("destination", List.of(x, y, z), List.of(y, z));
    var solution = new TestdataListEntityProvidingSolution();
    solution.setEntityList(List.of(isolated, source, destination));
    var policy =
        buildHeuristicConfigPolicy(TestdataListEntityProvidingSolution.buildSolutionDescriptor());
    var entityDescriptor =
        policy
            .getSolutionDescriptor()
            .findEntityDescriptorOrFail(TestdataListEntityProvidingEntity.class);
    var descriptor = entityDescriptor.getListVariableDescriptor();
    SubListSelector<TestdataListEntityProvidingSolution> sourceSelector =
        mock(SubListSelector.class);
    when(sourceSelector.getVariableDescriptor()).thenReturn(descriptor);
    when(sourceSelector.getSize()).thenReturn(2L);
    when(sourceSelector.getValueCount()).thenReturn(4L);
    when(sourceSelector.iterator())
        .thenAnswer(
            ignored -> List.of(new SubList(isolated, 0, 1), new SubList(source, 0, 1)).iterator());
    when(sourceSelector.endingValueIterator())
        .thenAnswer(ignored -> List.<Object>of(isolatedValue, x, y, z).iterator());
    var recorder = new MimicRecordingSubListSelector<>(sourceSelector);
    policy.addSubListMimicRecorder("origin", recorder);
    var right =
        SubListSelectorFactory.<TestdataListEntityProvidingSolution>create(
                new SubListSelectorConfig()
                    .withMinimumSubListSize(2)
                    .withMaximumSubListSize(2)
                    .withNearbySelectionConfig(
                        new NearbySelectionConfig()
                            .withOriginSubListSelectorConfig(
                                new SubListSelectorConfig().withMimicSelectorRef("origin"))
                            .withNearbyDistanceMeterClass(DistanceMeter.class)
                            .withMaxNearbySortSize(1)))
            .buildSubListSelector(
                policy,
                SelectorTestUtils.mockEntitySelector(
                    entityDescriptor, isolated, source, destination),
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM);
    var selector = new RandomSubListSwapMoveSelector<>(recorder, right, false, true);
    var director = mockScoreDirector(policy.getSolutionDescriptor());
    director.setWorkingSolution(solution);
    var scope = solvingStarted(selector, director, new Random(0));
    var phase = PlannerTestUtils.delegatingPhaseScope(scope);
    selector.phaseStarted(phase);
    try {
      var iterator = selector.iterator();
      assertThat(iterator.next()).isSameAs(SelectorBasedNoChangeMove.getInstance());
      var next = iterator.next();
      assertThat(next).isInstanceOf(SelectorBasedSubListSwapMove.class);
      var move = (SelectorBasedSubListSwapMove<?>) next;
      assertThat(move.getLeftSubList().length()).isEqualTo(1);
      assertThat(move.getRightSubList().length()).isEqualTo(2);
    } finally {
      selector.phaseEnded(phase);
      selector.solvingEnded(scope);
      director.close();
    }
  }

  public static class DistanceMeter
      implements NearbyDistanceMeter<
          TestdataListEntityProvidingValue, TestdataListEntityProvidingValue> {
    @Override
    public double getNearbyDistance(
        TestdataListEntityProvidingValue origin, TestdataListEntityProvidingValue destination) {
      return 0;
    }
  }
}
