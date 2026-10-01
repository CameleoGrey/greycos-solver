package greycos.solver.core.impl.localsearch.decider.gls;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.mockEntitySelector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelectorFactory;
import greycos.solver.core.impl.heuristic.selector.entity.decorator.GuidedLocalSearchEntitySelector;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.composite.UnionMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.GuidedLocalSearchMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedSwapMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.SwapMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedListChangeMove;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchSelectionContext.Priority;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEntity;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListValue;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.pinned.TestdataPinnedEntity;
import greycos.solver.core.testcotwin.pinned.TestdataPinnedSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GuidedLocalSearchDirectedSelectorsTest {

  @Test
  void entityFiltersPinsAndFiniteCountPrecedeTournamentAndWinningMimicRecording() {
    var solution = TestdataPinnedSolution.generateSolution(1, 5);
    var entities = solution.getEntityList();
    entities.get(0).setPinned(true);
    entities.get(1).setLocked(true);
    var probed = new ArrayList<Object>();
    var context = new GuidedLocalSearchSelectionContext<TestdataPinnedSolution>();
    context.publish(
        GuidedLocalSearchSelectionContextTest.snapshot(
            entity -> {
              probed.add(entity);
              return Priority.of(GuidedLocalSearchNumber.of(entity == entities.get(2) ? 9 : 1));
            }));
    var policy =
        buildHeuristicConfigPolicy(TestdataPinnedSolution.buildSolutionDescriptor())
            .cloneBuilder()
            .withGuidedLocalSearchSelectionContext(context)
            .build();
    var source =
        EntitySelectorFactory.<TestdataPinnedSolution>create(
                new EntitySelectorConfig()
                    .withId("origin")
                    .withFilterClass(UnlockedEntityFilter.class)
                    .withSelectedCountLimit(2L))
            .buildOriginEntitySelector(
                policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL, List.of("value"));
    var replay =
        EntitySelectorFactory.<TestdataPinnedSolution>create(
                new EntitySelectorConfig().withMimicSelectorRef("origin"))
            .buildEntitySelector(policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL);
    InnerScoreDirector<TestdataPinnedSolution, SimpleScore> director =
        mock(InnerScoreDirector.class);
    when(director.getWorkingSolution()).thenReturn(solution);
    var solverScope = SelectorTestUtils.solvingStarted(source, director, new Random(0), replay);
    var phaseScope = SelectorTestUtils.phaseStarted(source, solverScope);
    replay.phaseStarted(phaseScope);
    var iterator = source.iterator();
    assertThat(iterator.next()).isSameAs(entities.get(2));
    assertThat(replay.iterator().next()).isSameAs(entities.get(2));
    assertThat(probed).containsExactly(entities.get(2), entities.get(3));
    assertThat(iterator.next()).isSameAs(entities.get(3));
    assertThat(iterator.hasNext()).isFalse();
    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(iterator::next);
    assertThat(source.endingIterator())
        .toIterable()
        .containsExactly(entities.get(2), entities.get(3));
    assertThat(
            policy
                .getEntityMimicRecorder("origin")
                .getNearbySelectionSource()
                .membershipSupplier()
                .get())
        .containsExactlyInAnyOrder(entities.get(2), entities.get(3));
  }

  @Test
  void listPinsAssignmentsAndLiveFilterPrecedeProbingAndMimicRecording() {
    var solution = TestdataPinnedWithIndexListSolution.generateInitializedSolution(6, 1);
    var values = solution.getValueList();
    solution.getEntityList().getFirst().setPinIndex(2);
    var descriptor = TestdataPinnedWithIndexListSolution.buildSolutionDescriptor();
    var variable = descriptor.getListVariableDescriptor();
    var probed = new ArrayList<Object>();
    var context = new GuidedLocalSearchSelectionContext<TestdataPinnedWithIndexListSolution>();
    context.publish(
        GuidedLocalSearchSelectionContextTest.snapshot(
            value -> {
              probed.add(value);
              return Priority.of(GuidedLocalSearchNumber.of(value == values.get(3) ? 9 : 1));
            }));
    var policy =
        buildHeuristicConfigPolicy(descriptor)
            .cloneBuilder()
            .withGuidedLocalSearchSelectionContext(context)
            .build();
    var entityDescriptor =
        descriptor.findEntityDescriptorOrFail(TestdataPinnedWithIndexListEntity.class);
    var source =
        (IterableValueSelector<TestdataPinnedWithIndexListSolution>)
            ValueSelectorFactory.<TestdataPinnedWithIndexListSolution>create(
                    new ValueSelectorConfig("valueList")
                        .withId("origin")
                        .withFilterClass(SkipThirdValueFilter.class)
                        .withSelectedCountLimit(2L))
                .buildOriginValueSelector(
                    policy,
                    entityDescriptor,
                    SelectionCacheType.JUST_IN_TIME,
                    SelectionOrder.ORIGINAL,
                    ValueSelectorFactory.ListValueFilteringType.ACCEPT_ASSIGNED);
    var replay =
        ValueSelectorFactory.<TestdataPinnedWithIndexListSolution>create(
                new ValueSelectorConfig().withMimicSelectorRef("origin"))
            .buildValueSelector(
                policy, entityDescriptor, SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL);
    InnerScoreDirector<TestdataPinnedWithIndexListSolution, SimpleScore> director =
        mock(InnerScoreDirector.class);
    when(director.getWorkingSolution()).thenReturn(solution);
    when(director.getValueRangeManager()).thenReturn(ValueRangeManager.of(descriptor, solution));
    ListVariableState<TestdataPinnedWithIndexListSolution, Object, Object> state =
        mock(ListVariableState.class);
    when(director.getListVariableState(variable)).thenReturn(state);
    when(state.isPinned(values.get(0))).thenReturn(true);
    when(state.isPinned(values.get(1))).thenReturn(true);
    when(state.isAssigned(any()))
        .thenAnswer(invocation -> invocation.getArgument(0) != values.get(5));
    var solverScope = SelectorTestUtils.solvingStarted(source, director, new Random(0), replay);
    var phaseScope = SelectorTestUtils.phaseStarted(source, solverScope);
    replay.phaseStarted(phaseScope);
    var iterator = source.iterator();
    assertThat(iterator.next()).isSameAs(values.get(3));
    assertThat(replay.iterator(null).next()).isSameAs(values.get(3));
    assertThat(probed).containsExactly(values.get(3), values.get(4));
    assertThat(iterator.next()).isSameAs(values.get(4));
    assertThat(iterator.hasNext()).isFalse();
    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(iterator::next);
    assertThat(source.endingIterator(null))
        .toIterable()
        .containsExactly(values.get(3), values.get(4));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void destinationRangeAndNearbyReplayTheWinningOrigin(boolean nearby) {
    var solution = TestdataListEntityProvidingSolution.generateSolution(4, 2, false);
    for (var entity : solution.getEntityList()) {
      entity.setValueList(new ArrayList<>(entity.getValueRange()));
      entity.setUpShadowVariables();
    }
    var winner = solution.getValueList().getFirst();
    var descriptor = TestdataListEntityProvidingSolution.buildSolutionDescriptor();
    var context = new GuidedLocalSearchSelectionContext<TestdataListEntityProvidingSolution>();
    context.publish(
        GuidedLocalSearchSelectionContextTest.snapshot(
            value -> Priority.of(GuidedLocalSearchNumber.of(value == winner ? 9 : 1))));
    var policy =
        buildHeuristicConfigPolicy(descriptor)
            .cloneBuilder()
            .withGuidedLocalSearchSelectionContext(context)
            .build();
    var destination = new DestinationSelectorConfig();
    if (nearby) {
      destination.withNearbySelectionConfig(
          new NearbySelectionConfig()
              .withOriginValueSelectorConfig(
                  new ValueSelectorConfig().withMimicSelectorRef("origin"))
              .withNearbyDistanceMeterClass(StableListDistance.class)
              .withMaxNearbySortSize(1));
    }
    var config =
        new ListChangeMoveSelectorConfig()
            .withValueSelectorConfig(
                new ValueSelectorConfig("valueList").withId("origin").withSelectedCountLimit(4L))
            .withDestinationSelectorConfig(destination)
            .withSelectionOrder(SelectionOrder.ORIGINAL);
    GuidedLocalSearchDirectedSelectionValidator.validate(config);
    var selector =
        MoveSelectorFactory.<TestdataListEntityProvidingSolution>create(config)
            .buildMoveSelector(
                policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL, false);
    var director = greycos.solver.core.testutil.PlannerTestUtils.mockScoreDirector(descriptor);
    director.setWorkingSolution(solution);
    var solverScope = SelectorTestUtils.solvingStarted(selector, director, new Random(0));
    SelectorTestUtils.phaseStarted(selector, solverScope);
    var iterator = selector.iterator();
    assertThat(iterator.hasNext()).isTrue();
    var move = (SelectorBasedListChangeMove<TestdataListEntityProvidingSolution>) iterator.next();
    assertThat(move.getMovedValue()).isSameAs(winner);
    assertThat(move.getDestinationEntity()).isSameAs(solution.getEntityList().getFirst());
    assertThat(((TestdataListEntityProvidingEntity) move.getDestinationEntity()).getValueRange())
        .contains(winner);
  }

  @Test
  void mixedModelEntityPriorityIncludesOnlyChangedBasicVariables() {
    var solution = TestdataMixedSolution.generateUninitializedSolution(2, 2, 2);
    var descriptor = TestdataMixedSolution.buildSolutionDescriptor();
    var context = new GuidedLocalSearchSelectionContext<TestdataMixedSolution>();
    var snapshot = mock(GuidedLocalSearchSelectionContext.PrioritySnapshot.class);
    when(snapshot.isEmpty()).thenReturn(false);
    when(snapshot.entityPriority(any(), any()))
        .thenAnswer(
            invocation -> {
              assertThat((java.util.Collection<String>) invocation.getArgument(1))
                  .containsExactly("secondBasicValue");
              return Priority.of(GuidedLocalSearchNumber.ONE);
            });
    context.publish(snapshot);
    var policy =
        buildHeuristicConfigPolicy(descriptor)
            .cloneBuilder()
            .withGuidedLocalSearchSelectionContext(context)
            .build();
    var selector =
        EntitySelectorFactory.<TestdataMixedSolution>create(new EntitySelectorConfig())
            .buildOriginEntitySelector(
                policy,
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.ORIGINAL,
                List.of("secondBasicValue"));
    InnerScoreDirector<TestdataMixedSolution, SimpleScore> director =
        mock(InnerScoreDirector.class);
    when(director.getWorkingSolution()).thenReturn(solution);
    var solverScope = SelectorTestUtils.solvingStarted(selector, director, new Random(0));
    SelectorTestUtils.phaseStarted(selector, solverScope);
    assertThat(selector.iterator())
        .toIterable()
        .containsExactlyElementsOf(solution.getEntityList());
  }

  @Test
  void originalSwapPreservesAllConfiguredPairsWithDirectedFinitePrimary() {
    var descriptor = TestdataEntity.buildEntityDescriptor();
    var a = new TestdataEntity("a");
    var b = new TestdataEntity("b");
    var c = new TestdataEntity("c");
    var context = new GuidedLocalSearchSelectionContext<TestdataSolution>();
    context.publish(
        GuidedLocalSearchSelectionContextTest.snapshot(
            entity ->
                Priority.of(GuidedLocalSearchNumber.of(entity == c ? 3 : entity == b ? 2 : 1))));
    var left =
        new GuidedLocalSearchEntitySelector<>(
            mockEntitySelector(descriptor, a, b, c), context, List.of("value"), false);
    var right = mockEntitySelector(descriptor, a, b, c);
    var selector =
        new SwapMoveSelector<>(left, right, descriptor.getBasicVariableDescriptorList(), false);
    var actual = new ArrayList<List<Object>>();
    var iterator = selector.iterator();
    iterator.forEachRemaining(
        move -> {
          var swap = (SelectorBasedSwapMove<TestdataSolution>) move;
          actual.add(List.of(swap.getLeftEntity(), swap.getRightEntity()));
        });
    assertThat(actual)
        .containsExactly(
            List.of(c, a),
            List.of(c, b),
            List.of(c, c),
            List.of(b, a),
            List.of(b, b),
            List.of(b, c),
            List.of(a, a),
            List.of(a, b),
            List.of(a, c));
    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(iterator::next);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void unionLookaheadKeepsEachCandidatesOwnOriginProvenance(int childCount) {
    var context =
        GuidedLocalSearchSelectionContextTest.context(
            origin -> Priority.of(GuidedLocalSearchNumber.of((int) origin)));
    var originCounter = new AtomicInteger();
    var children = new ArrayList<MoveSelector<Object>>();
    for (int i = 0; i < childCount; i++) {
      children.add(
          new GuidedLocalSearchMoveSelector<>(
              new OriginMoveSelector(context, originCounter, i == 0 ? 3 : 1), context));
    }
    var union = new UnionMoveSelector<>(children, true, (director, selector) -> 1.0);
    SelectorTestUtils.solvingStarted(union, null, new Random(0));
    var iterator = union.iterator();
    for (int i = 0; i < 20; i++) {
      assertThat(iterator.hasNext()).isTrue();
      var move = (OriginMove) iterator.next();
      assertThat(context.isOrdinaryCandidate(move)).isEqualTo(move.expectedOrdinary);
    }
  }

  @Test
  void contextDoesNotLeakIntoAnotherPhase() {
    var context = new GuidedLocalSearchSelectionContext<TestdataSolution>();
    var policy =
        buildHeuristicConfigPolicy()
            .cloneBuilder()
            .withGuidedLocalSearchSelectionContext(context)
            .build();
    assertThat(policy.copyConfigPolicy().getGuidedLocalSearchSelectionContext()).isSameAs(context);
    assertThat(policy.copyConfigPolicyWithoutNearbySetting().getGuidedLocalSearchSelectionContext())
        .isSameAs(context);
    assertThat(policy.copyPhaseConfigPolicy().getGuidedLocalSearchSelectionContext()).isNull();
    var selector =
        EntitySelectorFactory.<TestdataSolution>create(new EntitySelectorConfig())
            .buildOriginEntitySelector(
                policy.copyPhaseConfigPolicy(),
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.ORIGINAL,
                List.of("value"));
    assertThat(selector).isNotInstanceOf(GuidedLocalSearchEntitySelector.class);
  }

  public static class StableListDistance implements NearbyDistanceMeter<Object, Object> {
    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      // Stable fact codes, deliberately giving the other entity the smallest distance.
      var code = ((greycos.solver.core.testcotwin.TestdataObject) destination).getCode();
      return code.endsWith("1") ? 0 : 1;
    }
  }

  public static class UnlockedEntityFilter
      implements SelectionFilter<TestdataPinnedSolution, TestdataPinnedEntity> {
    @Override
    public boolean accept(
        ScoreDirector<TestdataPinnedSolution> director, TestdataPinnedEntity entity) {
      return !entity.isLocked();
    }
  }

  public static class SkipThirdValueFilter
      implements SelectionFilter<
          TestdataPinnedWithIndexListSolution, TestdataPinnedWithIndexListValue> {
    @Override
    public boolean accept(
        ScoreDirector<TestdataPinnedWithIndexListSolution> director,
        TestdataPinnedWithIndexListValue value) {
      return value != director.getWorkingSolution().getValueList().get(2);
    }
  }

  private static final class OriginMoveSelector extends AbstractMoveSelector<Object> {
    private final GuidedLocalSearchSelectionContext<Object> context;
    private final AtomicInteger counter;
    private final int movesPerOrigin;

    private OriginMoveSelector(
        GuidedLocalSearchSelectionContext<Object> context,
        AtomicInteger counter,
        int movesPerOrigin) {
      this.context = context;
      this.counter = counter;
      this.movesPerOrigin = movesPerOrigin;
    }

    @Override
    public boolean isNeverEnding() {
      return true;
    }

    @Override
    public long getSize() {
      return 100;
    }

    @Override
    public Iterator<Move<Object>> iterator() {
      var raw =
          new Iterator<Integer>() {
            @Override
            public boolean hasNext() {
              return true;
            }

            @Override
            public Integer next() {
              return counter.incrementAndGet();
            }
          };
      var origins = context.direct(raw, true, origin -> context.valuePriority(origin, "value"));
      return new UpcomingSelectionIterator<>() {
        private int destinationsLeft;
        private boolean originOrdinary;

        @Override
        protected Move<Object> createUpcomingSelection() {
          if (destinationsLeft == 0) {
            origins.next();
            originOrdinary = context.wasLastOriginOrdinary();
            destinationsLeft = movesPerOrigin;
          }
          destinationsLeft--;
          return new OriginMove(originOrdinary);
        }
      };
    }
  }

  private static final class OriginMove extends AbstractSelectorBasedMove<Object> {
    private final boolean expectedOrdinary;

    private OriginMove(boolean expectedOrdinary) {
      this.expectedOrdinary = expectedOrdinary;
    }

    @Override
    protected void execute(
        MutableSolutionView<Object> view, VariableDescriptorAwareScoreDirector<Object> director) {}

    @Override
    public Move<Object> rebase(greycos.solver.core.api.cotwin.lookup.Lookup lookup) {
      return this;
    }
  }
}
