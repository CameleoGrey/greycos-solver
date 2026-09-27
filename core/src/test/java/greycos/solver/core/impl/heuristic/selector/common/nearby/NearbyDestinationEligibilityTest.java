package greycos.solver.core.impl.heuristic.selector.common.nearby;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionDistributionType;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.valuerange.buildin.collection.ListValueRange;
import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListValue;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class NearbyDestinationEligibilityTest {

  @Test
  void subListOriginsUseCurrentFirstValueAndReuseThatValuesRow() {
    var a = new TestdataListValue("a");
    var b = new TestdataListValue("b");
    var c = new TestdataListValue("c");
    var entity = TestdataListEntity.createWithValues("entity", a, b, c);
    var length = new AtomicInteger(2);
    CountingValueDistanceMeter.calls.set(0);
    var nearby =
        new NearbySelectionConfig().withNearbyDistanceMeterClass(CountingValueDistanceMeter.class);
    try (var fixture =
        new Fixture<>(
            TestdataListSolution.buildSolutionDescriptor(),
            TestdataListEntity.class,
            List.of(entity),
            List.of(a, b, c),
            nearby,
            SelectionOrder.ORIGINAL,
            () -> new SubList(entity, 0, length.get()),
            true)) {
      assertThat(fixture.selector.iterator().next()).isEqualTo(ElementPosition.of(entity, 1));
      assertThat(CountingValueDistanceMeter.calls).hasValue(4);
      length.set(1);
      assertThat(fixture.selector.iterator().next()).isEqualTo(ElementPosition.of(entity, 1));
      assertThat(CountingValueDistanceMeter.calls).hasValue(4);
      Collections.swap(entity.getValueList(), 0, 2);
      assertThat(fixture.selector.iterator().next()).isEqualTo(ElementPosition.of(entity, 1));
      assertThat(CountingValueDistanceMeter.calls).hasValue(8);
    }
  }

  @Test
  void eagerSubListOriginsEnumerateValuesWithoutReplayingASubList() {
    var a = new TestdataListValue("a");
    var b = new TestdataListValue("b");
    var entity = TestdataListEntity.createWithValues("entity", a, b);
    CountingValueDistanceMeter.calls.set(0);
    var nearby =
        new NearbySelectionConfig()
            .withNearbyDistanceMeterClass(CountingValueDistanceMeter.class)
            .withEagerInitialization(true);
    try (var fixture =
        new Fixture<>(
            TestdataListSolution.buildSolutionDescriptor(),
            TestdataListEntity.class,
            List.of(entity),
            List.of(a, b),
            nearby,
            SelectionOrder.ORIGINAL,
            () -> new SubList(entity, 0, 2),
            true)) {
      assertThat(CountingValueDistanceMeter.calls).hasValue(6);
      assertThat(fixture.selector.iterator().next()).isEqualTo(ElementPosition.of(entity, 1));
      assertThat(CountingValueDistanceMeter.calls).hasValue(6);
    }
  }

  @Test
  void cappedEligibilityTracksAssignmentsAndPositionsForTheSameOrigin() {
    var a = new TestdataAllowsUnassignedValuesListValue("a");
    var u = new TestdataAllowsUnassignedValuesListValue("u");
    var firstEntity = new TestdataAllowsUnassignedValuesListEntity("first", a);
    var secondEntity = new TestdataAllowsUnassignedValuesListEntity("second");
    var nearby =
        new NearbySelectionConfig()
            .withNearbyDistanceMeterClass(UnassignedFirstDistanceMeter.class)
            .withNearbySelectionDistributionType(NearbySelectionDistributionType.BLOCK_DISTRIBUTION)
            .withBlockDistributionSizeMinimum(1)
            .withBlockDistributionSizeMaximum(1)
            .withBlockDistributionUniformDistributionProbability(0.0);
    try (var fixture =
        new Fixture<>(
            TestdataAllowsUnassignedValuesListSolution.buildSolutionDescriptor(),
            TestdataAllowsUnassignedValuesListEntity.class,
            List.of(firstEntity, secondEntity),
            List.of(a, u),
            nearby,
            SelectionOrder.RANDOM,
            () -> a,
            false)) {
      var iterator = fixture.selector.iterator();
      // The closest value is unassigned; truncation must retain the next eligible value.
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(firstEntity, 1));
      secondEntity.getValueList().add(u);
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(secondEntity, 1));
      secondEntity.getValueList().clear();
      firstEntity.getValueList().addFirst(u);
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(firstEntity, 1));
      firstEntity.getValueList().remove(u);
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(firstEntity, 1));
    }
  }

  @Test
  void entityRangeEligibilityUsesTheCandidatesCurrentOwnerBeforeApplyingTheCap() {
    var a = new TestdataListEntityProvidingValue("a");
    var b = new TestdataListEntityProvidingValue("b");
    var c = new TestdataListEntityProvidingValue("c");
    var sourceEntity =
        new TestdataListEntityProvidingEntity("source", List.of(a), new ArrayList<>(List.of(a)));
    var forbiddenEntity =
        new TestdataListEntityProvidingEntity("forbidden", List.of(b), new ArrayList<>(List.of(b)));
    var allowedEntity =
        new TestdataListEntityProvidingEntity(
            "allowed", List.of(a, b, c), new ArrayList<>(List.of(c)));
    var nearby =
        new NearbySelectionConfig()
            .withNearbyDistanceMeterClass(EntityRangeDistanceMeter.class)
            .withNearbySelectionDistributionType(NearbySelectionDistributionType.BLOCK_DISTRIBUTION)
            .withBlockDistributionSizeMinimum(1)
            .withBlockDistributionSizeMaximum(1)
            .withBlockDistributionUniformDistributionProbability(0.0);
    try (var fixture =
        new Fixture<>(
            TestdataListEntityProvidingSolution.buildSolutionDescriptor(),
            TestdataListEntityProvidingEntity.class,
            List.of(sourceEntity, forbiddenEntity, allowedEntity),
            List.of(a, b, c),
            nearby,
            SelectionOrder.RANDOM,
            () -> a,
            false)) {
      var iterator = fixture.selector.iterator();
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(allowedEntity, 1));
      forbiddenEntity.getValueList().remove(b);
      allowedEntity.getValueList().add(b);
      assertThat(iterator.next()).isEqualTo(ElementPosition.of(allowedEntity, 2));
    }
  }

  @Test
  void deterministicSelectionHasExactlyOneUnassignedOptionAndEnds() {
    var a = new TestdataAllowsUnassignedValuesListValue("a");
    var u = new TestdataAllowsUnassignedValuesListValue("u");
    var entity = new TestdataAllowsUnassignedValuesListEntity("entity", a);
    try (var fixture =
        new Fixture<>(
            TestdataAllowsUnassignedValuesListSolution.buildSolutionDescriptor(),
            TestdataAllowsUnassignedValuesListEntity.class,
            List.of(entity),
            List.of(a, u),
            new NearbySelectionConfig()
                .withNearbyDistanceMeterClass(UnassignedFirstDistanceMeter.class),
            SelectionOrder.ORIGINAL,
            () -> a,
            false)) {
      var iterator = fixture.selector.iterator();
      var positions = new ArrayList<ElementPosition>();
      iterator.forEachRemaining(positions::add);
      assertThat(positions)
          .containsExactly(
              ElementPosition.of(entity, 1),
              ElementPosition.of(entity, 0),
              ElementPosition.unassigned());
      assertThat(iterator.hasNext()).isFalse();
      assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
      assertThat(fixture.selector.isNeverEnding()).isFalse();
    }
  }

  @Test
  void emptyRandomDestinationPopulationHasNoNextSelection() {
    var origin = new TestdataListValue("origin");
    try (var fixture =
        new Fixture<>(
            TestdataListSolution.buildSolutionDescriptor(),
            TestdataListEntity.class,
            List.of(),
            List.of(),
            new NearbySelectionConfig()
                .withNearbyDistanceMeterClass(CountingValueDistanceMeter.class),
            SelectionOrder.RANDOM,
            () -> origin,
            false)) {
      var iterator = fixture.selector.iterator();
      assertThat(iterator.hasNext()).isFalse();
      assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }
  }

  public static final class CountingValueDistanceMeter
      implements NearbyDistanceMeter<TestdataListValue, Object> {
    static final AtomicInteger calls = new AtomicInteger();

    @Override
    public double getNearbyDistance(TestdataListValue origin, Object destination) {
      calls.incrementAndGet();
      return origin == destination ? 0 : 100;
    }
  }

  public static final class UnassignedFirstDistanceMeter
      implements NearbyDistanceMeter<TestdataAllowsUnassignedValuesListValue, Object> {
    @Override
    public double getNearbyDistance(
        TestdataAllowsUnassignedValuesListValue origin, Object destination) {
      if (destination instanceof TestdataAllowsUnassignedValuesListValue value) {
        return value.getCode().equals("u") ? 0 : 1;
      }
      return 100;
    }
  }

  public static final class EntityRangeDistanceMeter
      implements NearbyDistanceMeter<TestdataListEntityProvidingValue, Object> {
    @Override
    public double getNearbyDistance(TestdataListEntityProvidingValue origin, Object destination) {
      if (destination instanceof TestdataListEntityProvidingValue value) {
        return switch (value.getCode()) {
          case "b" -> 0;
          case "c" -> 1;
          default -> 2;
        };
      }
      return 100;
    }
  }

  /** Keep random tests on the geographic branch; unassignment is exercised by the real solver. */
  private static final class AssignedDestinationRandom extends Random {
    @Override
    public long nextLong(long bound) {
      return bound > 1 ? 1 : 0;
    }

    @Override
    public int nextInt(int bound) {
      return 0;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final class Fixture<Solution_> implements AutoCloseable {
    final NearbyDestinationSelector<Solution_> selector;
    private final SolverScope<Solution_> solverScope;
    private final AbstractPhaseScope<Solution_> phaseScope;

    Fixture(
        SolutionDescriptor<Solution_> solutionDescriptor,
        Class<?> entityClass,
        List<Object> entities,
        List<Object> values,
        NearbySelectionConfig nearby,
        SelectionOrder order,
        Supplier<Object> originSupplier,
        boolean subListOrigin) {
      var descriptor = solutionDescriptor.getListVariableDescriptor();
      var entityDescriptor = solutionDescriptor.findEntityDescriptorOrFail(entityClass);
      var entitySelector =
          SelectorTestUtils.mockEntitySelector(entityDescriptor, entities.toArray());
      var valueSelector = SelectorTestUtils.mockIterableValueSelector(descriptor, values.toArray());
      IterableValueSelector<Solution_> originValueSelector = null;
      SubListSelector<Solution_> originSubListSelector = null;
      if (subListOrigin) {
        originSubListSelector = mock(SubListSelector.class);
        when(originSubListSelector.iterator())
            .thenAnswer(ignored -> List.of((SubList) originSupplier.get()).iterator());
        when(originSubListSelector.endingValueIterator()).thenAnswer(ignored -> values.iterator());
        when(originSubListSelector.getValueCount()).thenReturn((long) values.size());
      } else {
        originValueSelector = mock(IterableValueSelector.class);
        when(originValueSelector.iterator())
            .thenAnswer(ignored -> List.of(originSupplier.get()).iterator());
        when(originValueSelector.endingIterator(null)).thenAnswer(ignored -> values.iterator());
        when(originValueSelector.getSize()).thenReturn((long) values.size());
      }
      selector =
          new NearbyDestinationSelector<>(
              new DestinationSelectorConfig(),
              buildHeuristicConfigPolicy(solutionDescriptor),
              nearby,
              SelectionCacheType.JUST_IN_TIME,
              order,
              mock(ElementDestinationSelector.class),
              entitySelector,
              valueSelector,
              null,
              originSubListSelector,
              originValueSelector);
      InnerScoreDirector<Solution_, ?> scoreDirector = mock(InnerScoreDirector.class);
      if (!descriptor.canExtractValueRangeFromSolution()) {
        ValueRangeManager<Solution_> valueRangeManager = mock(ValueRangeManager.class);
        when(scoreDirector.getValueRangeManager()).thenReturn(valueRangeManager);
        when(valueRangeManager.getFromEntity(any(), any()))
            .thenAnswer(
                invocation ->
                    new ListValueRange<>(
                        ((TestdataListEntityProvidingEntity) invocation.getArgument(1))
                            .getValueRange()));
      }
      ListVariableState<Solution_, Object, Object> state = mock(ListVariableState.class);
      when(state.getElementPosition(any()))
          .thenAnswer(
              invocation -> {
                Object value = invocation.getArgument(0);
                for (Object entity : entities) {
                  int index = descriptor.getValue(entity).indexOf(value);
                  if (index >= 0) {
                    return ElementPosition.of(entity, index);
                  }
                }
                return ElementPosition.unassigned();
              });
      NearbyTestUtils.mockSupplyManager(scoreDirector, state);
      solverScope =
          SelectorTestUtils.solvingStarted(
              selector, scoreDirector, new AssignedDestinationRandom());
      phaseScope = PlannerTestUtils.delegatingPhaseScope(solverScope);
      selector.phaseStarted(phaseScope);
    }

    @Override
    public void close() {
      selector.phaseEnded(phaseScope);
      selector.solvingEnded(solverScope);
    }
  }
}
