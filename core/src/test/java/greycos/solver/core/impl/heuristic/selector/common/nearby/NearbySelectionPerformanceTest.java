package greycos.solver.core.impl.heuristic.selector.common.nearby;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.heuristic.selector.Selector;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.entity.nearby.NearEntityNearbyEntitySelector;
import greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import greycos.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.nearby.NearEntityNearbyValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListValue;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedEntity;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedSolution;
import greycos.solver.core.testutil.PlannerTestUtils;
import greycos.solver.core.testutil.TestNearbyRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class NearbySelectionPerformanceTest {

  private static Stream<Arguments> boundedDistributions() {
    return Stream.of(100, 1000)
        .flatMap(
            population ->
                Stream.of(
                        new LinearDistributionNearbyRandom(40),
                        new ParabolicDistributionNearbyRandom(40),
                        new BetaDistributionNearbyRandom(1, 5))
                    .map(distribution -> Arguments.of(population, distribution)));
  }

  @ParameterizedTest
  @MethodSource("boundedDistributions")
  void warmBoundedSelectionOnlyChecksTheEligiblePrefix(int population, NearbyRandom distribution) {
    assertThat(distribution.requiresPopulationSize()).isFalse();
    try (var fixture = new SubListFixture(population, 0, distribution, 40, new Random(37))) {
      fixture.iterator.next();
      int distanceCalls = fixture.distanceCalls.get();
      assertThat(distanceCalls).isEqualTo(population);

      for (int draw = 0; draw < 10; draw++) {
        fixture.inverseReads.set(0);
        assertThat(fixture.iterator.next().length()).isEqualTo(2);
        // Forty eligible candidates, then one lookup to construct the selected sublist.
        assertThat(fixture.inverseReads.get()).isLessThanOrEqualTo(41);
      }
      assertThat(fixture.distanceCalls.get()).isEqualTo(distanceCalls);
    }
  }

  @Test
  void rejectedPrefixDoesNotConsumeTheRetainedLimit() {
    try (var fixture =
        new SubListFixture(
            1000, 7, new ParabolicDistributionNearbyRandom(40), 40, new Random(37))) {
      fixture.iterator.next();
      fixture.inverseReads.set(0);

      var selected = fixture.iterator.next();

      assertThat(selected.entity()).isSameAs(fixture.mainEntity);
      assertThat(selected.fromIndex()).isBetween(0, 39);
      assertThat(fixture.inverseReads.get()).isEqualTo(7 + 40 + 1);
      assertThat(fixture.distanceCalls.get()).isEqualTo(1000);
    }
  }

  @Test
  void sparseEligibilityExhaustsTheRowAndKeepsTheAvailableCandidates() {
    var random = new LastRankRandom();
    try (var fixture =
        new SubListFixture(100, 94, new LinearDistributionNearbyRandom(40), 40, random)) {
      fixture.iterator.next();
      fixture.inverseReads.set(0);

      var selected = fixture.iterator.next();

      assertThat(selected).isEqualTo(new SubList(fixture.mainEntity, 4, 2));
      assertThat(fixture.inverseReads.get()).isEqualTo(101);
      assertThat(fixture.distanceCalls.get()).isEqualTo(100);
    }
  }

  @Test
  void blockRatioStillUsesTheCompleteEligiblePopulation() {
    var distribution = new BlockDistributionNearbyRandom(1, 40, 0.01, 0);
    assertThat(distribution.requiresPopulationSize()).isTrue();
    assertThat(new TestNearbyRandom().requiresPopulationSize()).isTrue();
    try (var fixture = new SubListFixture(1000, 0, distribution, 40, new LastRankRandom())) {
      var selected = fixture.iterator.next();

      // There are 999 eligible starts: floor(999 * .01) = 9, rather than floor(40 * .01).
      assertThat(selected).isEqualTo(new SubList(fixture.mainEntity, 8, 2));
      assertThat(fixture.inverseReads.get()).isEqualTo(1001);
    }
  }

  @ParameterizedTest
  @CsvSource({"true, 100", "true, 1000", "false, 100", "false, 1000"})
  @SuppressWarnings({"unchecked", "rawtypes"})
  void entityAndValueFiltersOnlyCheckTheRetainedPrefix(boolean selectEntities, int population) {
    var descriptor = TestdataEntity.buildEntityDescriptor();
    var entity = new TestdataEntity("origin");
    var origin = SelectorTestUtils.mockReplayingEntitySelector(descriptor, entity);
    var candidates = new Object[population];
    for (int index = 0; index < population; index++) {
      candidates[index] =
          selectEntities
              ? new TestdataEntity(Integer.toString(index))
              : new TestdataValue(Integer.toString(index));
    }
    var filterCalls = new AtomicInteger();
    var distanceCalls = new AtomicInteger();
    SelectionFilter<TestdataSolution, Object> filter =
        (director, candidate) -> {
          filterCalls.incrementAndGet();
          return true;
        };
    NearbyDistanceMeter<Object, TestdataObject> meter =
        (source, destination) -> {
          distanceCalls.incrementAndGet();
          return Integer.parseInt(destination.getCode());
        };
    Selector<TestdataSolution> selector;
    Supplier<Iterator<Object>> iteratorSupplier;
    if (selectEntities) {
      var nearby =
          new NearEntityNearbyEntitySelector<>(
              SelectorTestUtils.mockEntitySelector(descriptor, candidates),
              origin,
              meter,
              new ParabolicDistributionNearbyRandom(40),
              true,
              40,
              false);
      nearby.configureSelectionFilter(filter);
      selector = nearby;
      iteratorSupplier = nearby::iterator;
    } else {
      var nearby =
          new NearEntityNearbyValueSelector<>(
              SelectorTestUtils.mockIterableValueSelector(
                  descriptor.getGenuineVariableDescriptor("value"), candidates),
              origin,
              meter,
              new ParabolicDistributionNearbyRandom(40),
              true,
              40,
              false);
      nearby.configureSelectionFilter(filter);
      selector = nearby;
      iteratorSupplier = () -> nearby.iterator(entity);
    }
    InnerScoreDirector<TestdataSolution, ?> director = mock(InnerScoreDirector.class);
    NearbyTestUtils.mockSupplyManager(director, null);
    try (var lifecycle = new Lifecycle<>(selector, director, new Random(37))) {
      var iterator = iteratorSupplier.get();
      iterator.next();
      assertThat(distanceCalls.get()).isEqualTo(population);
      for (int draw = 0; draw < 10; draw++) {
        filterCalls.set(0);
        iterator.next();
        assertThat(filterCalls.get()).isEqualTo(40);
      }
      assertThat(distanceCalls.get()).isEqualTo(population);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 1000})
  @SuppressWarnings({"unchecked", "rawtypes"})
  void destinationFilterOnlyChecksTheRetainedPrefix(int population) {
    var values = new TestdataListValue[population];
    for (int index = 0; index < population; index++) {
      values[index] = new TestdataListValue(Integer.toString(index));
    }
    var entity = TestdataListEntity.createWithValues("entity", values);
    var descriptor = TestdataListEntity.buildVariableDescriptorForValueList();
    var entitySelector =
        SelectorTestUtils.mockEntitySelector(descriptor.getEntityDescriptor(), entity);
    var valueSelector = SelectorTestUtils.mockIterableValueSelector(descriptor, (Object[]) values);
    IterableValueSelector<TestdataListSolution> origin = mock(IterableValueSelector.class);
    when(origin.iterator()).thenAnswer(ignored -> List.<Object>of(values[0]).iterator());
    when(origin.getSize()).thenReturn((long) population);
    var selector =
        new NearbyDestinationSelector<>(
            new DestinationSelectorConfig(),
            buildHeuristicConfigPolicy(TestdataListSolution.buildSolutionDescriptor()),
            new NearbySelectionConfig()
                .withNearbyDistanceMeterClass(DestinationDistanceMeter.class)
                .withParabolicDistributionSizeMaximum(40)
                .withMaxNearbySortSize(40),
            SelectionCacheType.JUST_IN_TIME,
            SelectionOrder.RANDOM,
            mock(ElementDestinationSelector.class),
            entitySelector,
            valueSelector,
            null,
            null,
            origin);
    var filterCalls = new AtomicInteger();
    selector.configureSelectionSources(
        new NearbySelectionSource<>(entitySelector, entitySelector, null),
        new NearbySelectionSource<>(
            valueSelector,
            valueSelector,
            (director, candidate) -> {
              filterCalls.incrementAndGet();
              return true;
            }));
    InnerScoreDirector<TestdataListSolution, ?> director = mock(InnerScoreDirector.class);
    ListVariableState<TestdataListSolution, Object, Object> state = mock(ListVariableState.class);
    when(state.getElementPosition(any()))
        .thenAnswer(
            invocation ->
                ElementPosition.of(
                    entity,
                    Integer.parseInt(((TestdataListValue) invocation.getArgument(0)).getCode())));
    NearbyTestUtils.mockSupplyManager(director, state);
    try (var lifecycle = new Lifecycle<>(selector, director, new Random(37))) {
      var iterator = selector.iterator();
      iterator.next();
      for (int draw = 0; draw < 10; draw++) {
        filterCalls.set(0);
        iterator.next();
        assertThat(filterCalls.get()).isEqualTo(40);
      }
    }
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void basicUnassignmentUsesTheCompleteFilteredPopulationAndPreservesRandomState() {
    var descriptor = TestdataAllowsUnassignedEntity.buildVariableDescriptorForValue();
    var entity = new TestdataAllowsUnassignedEntity("origin");
    var values = new ArrayList<Object>();
    for (int index = 0; index < 100; index++) {
      values.add(new TestdataValue(Integer.toString(index)));
    }
    values.add(null);
    var acceptedPopulation = new AtomicInteger(100);
    NearbyDistanceMeter<Object, TestdataValue> meter =
        (source, destination) -> Integer.parseInt(destination.getCode());
    var distribution = new ParabolicDistributionNearbyRandom(40);
    var selector =
        new NearEntityNearbyValueSelector<>(
            SelectorTestUtils.mockIterableValueSelector(descriptor, values.toArray()),
            SelectorTestUtils.mockReplayingEntitySelector(descriptor.getEntityDescriptor(), entity),
            meter,
            distribution,
            true,
            40,
            false);
    selector.configureSelectionFilter(
        (director, candidate) ->
            candidate == null
                || Integer.parseInt(((TestdataValue) candidate).getCode())
                    < acceptedPopulation.get());
    InnerScoreDirector<TestdataAllowsUnassignedSolution, ?> director =
        mock(InnerScoreDirector.class);
    NearbyTestUtils.mockSupplyManager(director, null);
    var random = new PopulationBoundRandom(37);
    var referenceRandom = new Random(37);
    try (var lifecycle = new Lifecycle<>(selector, director, random)) {
      var iterator = selector.iterator(entity);
      for (int draw = 0; draw < 100; draw++) {
        if (draw == 50) {
          acceptedPopulation.set(99);
        }
        int population = acceptedPopulation.get();
        Object expected =
            referenceRandom.nextLong((long) population + 1) == population
                ? null
                : values.get(distribution.nextInt(referenceRandom, population, 40));
        assertThat(iterator.next()).isSameAs(expected);
        assertThat(random.lastBound).isEqualTo((long) population + 1);
      }
      assertThat(random.nextLong()).isEqualTo(referenceRandom.nextLong());
    }
  }

  private static Stream<Arguments> referenceDistributions() {
    return Stream.of(
        Arguments.of(new LinearDistributionNearbyRandom(40), 40),
        Arguments.of(new ParabolicDistributionNearbyRandom(40), 40),
        Arguments.of(new BetaDistributionNearbyRandom(1, 5), 40),
        Arguments.of(new BlockDistributionNearbyRandom(1, 40, 0.1, 0), 40),
        Arguments.of(new BlockDistributionNearbyRandom(1, 5, 0.1, 0.5), 40),
        Arguments.of(new BlockDistributionNearbyRandom(1, 5, 0.1, 0.5), Integer.MAX_VALUE),
        Arguments.of(new BetaDistributionNearbyRandom(1, 5), Integer.MAX_VALUE),
        Arguments.of(new LinearDistributionNearbyRandom(Integer.MAX_VALUE), Integer.MAX_VALUE));
  }

  @ParameterizedTest
  @MethodSource("referenceDistributions")
  void matchesFullScanReferenceAndRandomState(NearbyRandom distribution, int cap) {
    var random = new Random(37);
    var referenceRandom = new Random(37);
    try (var fixture = new SubListFixture(100, 5, distribution, cap, random)) {
      // Independently enumerate all valid fixed-length sublists in distance order.
      var completeCandidates = new ArrayList<SubList>();
      for (int start = 0; start + 2 <= fixture.mainEntity.getValueList().size(); start++) {
        completeCandidates.add(new SubList(fixture.mainEntity, start, 2));
      }
      int retainedSize =
          Math.min(completeCandidates.size(), Math.min(cap, distribution.getOverallSizeMaximum()));
      for (int draw = 0; draw < 100; draw++) {
        int rank = distribution.nextInt(referenceRandom, completeCandidates.size(), retainedSize);
        assertThat(fixture.iterator.next()).isEqualTo(completeCandidates.get(rank));
      }
      assertThat(random.nextLong()).isEqualTo(referenceRandom.nextLong());
      assertThat(fixture.distanceCalls.get()).isEqualTo(100);
    }
  }

  @Test
  void unassignmentProbabilityAndRandomStateUseTheCompleteChangingPopulation() {
    var random = new PopulationBoundRandom(37);
    var referenceRandom = new Random(37);
    var distribution = new ParabolicDistributionNearbyRandom(40);
    try (var fixture = new DestinationFixture(100, random)) {
      for (int draw = 0; draw < 100; draw++) {
        if (draw == 50) {
          fixture.entity.getValueList().removeLast();
        }
        int population = fixture.entity.getValueList().size() + 1;
        ElementPosition expected;
        if (referenceRandom.nextLong((long) population + 1) == 0) {
          expected = ElementPosition.unassigned();
        } else {
          int rank = distribution.nextInt(referenceRandom, population, 40);
          expected = ElementPosition.of(fixture.entity, rank + 1);
        }
        assertThat(fixture.iterator.next()).isEqualTo(expected);
        assertThat(random.lastBound).isEqualTo((long) population + 1);
      }
      assertThat(random.nextLong()).isEqualTo(referenceRandom.nextLong());
    }
  }

  private static final class PopulationBoundRandom extends Random {
    private long lastBound;

    PopulationBoundRandom(long seed) {
      super(seed);
    }

    @Override
    public long nextLong(long bound) {
      lastBound = bound;
      return super.nextLong(bound);
    }
  }

  private static final class LastRankRandom extends Random {
    @Override
    public double nextDouble() {
      return Math.nextDown(1.0);
    }

    @Override
    public int nextInt(int bound) {
      return bound - 1;
    }
  }

  private static final class Lifecycle<Solution_> implements AutoCloseable {
    private final Selector<Solution_> selector;
    private final SolverScope<Solution_> solverScope;
    private final AbstractPhaseScope<Solution_> phaseScope;

    Lifecycle(
        Selector<Solution_> selector, InnerScoreDirector<Solution_, ?> director, Random random) {
      this.selector = selector;
      solverScope = SelectorTestUtils.solvingStarted(selector, director, random);
      phaseScope = PlannerTestUtils.delegatingPhaseScope(solverScope);
      selector.phaseStarted(phaseScope);
    }

    @Override
    public void close() {
      selector.phaseEnded(phaseScope);
      selector.solvingEnded(solverScope);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final class SubListFixture implements AutoCloseable {
    final AtomicInteger inverseReads = new AtomicInteger();
    final AtomicInteger distanceCalls = new AtomicInteger();
    final TestdataListEntity mainEntity;
    final Iterator<SubList> iterator;
    private final NearbySubListSelector<TestdataListSolution> selector;
    private final SolverScope<TestdataListSolution> solverScope;
    private final AbstractPhaseScope<TestdataListSolution> phaseScope;

    SubListFixture(
        int population, int rejectedPrefix, NearbyRandom distribution, int cap, Random random) {
      var values = new ArrayList<TestdataListValue>();
      var entities = new ArrayList<TestdataListEntity>();
      var ranks = new IdentityHashMap<Object, Integer>();
      var owners = new IdentityHashMap<Object, TestdataListEntity>();
      var indices = new IdentityHashMap<Object, Integer>();
      for (int rank = 0; rank < population; rank++) {
        var value = new TestdataListValue(Integer.toString(rank));
        values.add(value);
        ranks.put(value, rank);
        if (rank < rejectedPrefix) {
          entities.add(TestdataListEntity.createWithValues("singleton-" + rank, value));
        }
      }
      mainEntity =
          TestdataListEntity.createWithValues(
              "main", values.subList(rejectedPrefix, population).toArray(TestdataListValue[]::new));
      entities.add(mainEntity);
      for (var entity : entities) {
        for (int index = 0; index < entity.getValueList().size(); index++) {
          var value = entity.getValueList().get(index);
          owners.put(value, entity);
          indices.put(value, index);
        }
      }
      var descriptor = TestdataListEntity.buildVariableDescriptorForValueList();
      var entitySelector =
          SelectorTestUtils.mockEntitySelector(
              TestdataListEntity.buildEntityDescriptor(), entities.toArray());
      var valueSelector = SelectorTestUtils.mockIterableValueSelector(descriptor, values.toArray());
      var child = new RandomSubListSelector<>(entitySelector, valueSelector, 2, 2);
      SubListSelector<TestdataListSolution> origin = mock(SubListSelector.class);
      when(origin.getValueCount()).thenReturn((long) population);
      when(origin.iterator())
          .thenAnswer(ignored -> List.of(new SubList(mainEntity, 0, 1)).iterator());
      NearbyDistanceMeter<Object, Object> meter =
          (source, destination) -> {
            distanceCalls.incrementAndGet();
            return ranks.get(destination);
          };
      selector = new NearbySubListSelector<>(child, origin, meter, distribution, true, cap, false);
      InnerScoreDirector<TestdataListSolution, ?> director = mock(InnerScoreDirector.class);
      ListVariableState<TestdataListSolution, Object, Object> state =
          mock(ListVariableState.class, withSettings().stubOnly());
      when(state.getInverseSingleton(any()))
          .thenAnswer(
              invocation -> {
                inverseReads.incrementAndGet();
                return owners.get(invocation.getArgument(0));
              });
      when(state.getIndexOrElse(any(), eq(-1)))
          .thenAnswer(invocation -> indices.get(invocation.getArgument(0)));
      NearbyTestUtils.mockSupplyManager(director, state);
      solverScope = SelectorTestUtils.solvingStarted(selector, director, random);
      phaseScope = PlannerTestUtils.delegatingPhaseScope(solverScope);
      selector.phaseStarted(phaseScope);
      iterator = selector.iterator();
    }

    @Override
    public void close() {
      selector.phaseEnded(phaseScope);
      selector.solvingEnded(solverScope);
    }
  }

  public static final class DestinationDistanceMeter
      implements NearbyDistanceMeter<Object, Object> {
    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      if (destination instanceof TestdataListValue value) {
        return Integer.parseInt(value.getCode());
      }
      return destination instanceof TestdataAllowsUnassignedValuesListValue value
          ? Integer.parseInt(value.getCode())
          : Integer.MAX_VALUE;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final class DestinationFixture implements AutoCloseable {
    final TestdataAllowsUnassignedValuesListEntity entity;
    final Iterator<ElementPosition> iterator;
    private final NearbyDestinationSelector<TestdataAllowsUnassignedValuesListSolution> selector;
    private final SolverScope<TestdataAllowsUnassignedValuesListSolution> solverScope;
    private final AbstractPhaseScope<TestdataAllowsUnassignedValuesListSolution> phaseScope;

    DestinationFixture(int population, Random random) {
      var values = new ArrayList<TestdataAllowsUnassignedValuesListValue>();
      for (int index = 0; index < population; index++) {
        values.add(new TestdataAllowsUnassignedValuesListValue(Integer.toString(index)));
      }
      entity = new TestdataAllowsUnassignedValuesListEntity("entity", new ArrayList<>(values));
      var descriptor =
          TestdataAllowsUnassignedValuesListEntity.buildVariableDescriptorForValueList();
      var entitySelector =
          SelectorTestUtils.mockEntitySelector(
              TestdataAllowsUnassignedValuesListEntity.buildEntityDescriptor(), entity);
      var valueSelector = SelectorTestUtils.mockIterableValueSelector(descriptor, values.toArray());
      IterableValueSelector<TestdataAllowsUnassignedValuesListSolution> origin =
          mock(IterableValueSelector.class);
      when(origin.iterator()).thenAnswer(ignored -> List.<Object>of(values.getFirst()).iterator());
      when(origin.getSize()).thenReturn((long) population);
      selector =
          new NearbyDestinationSelector<>(
              new DestinationSelectorConfig(),
              buildHeuristicConfigPolicy(
                  TestdataAllowsUnassignedValuesListSolution.buildSolutionDescriptor()),
              new NearbySelectionConfig()
                  .withNearbyDistanceMeterClass(DestinationDistanceMeter.class)
                  .withParabolicDistributionSizeMaximum(40)
                  .withMaxNearbySortSize(40),
              SelectionCacheType.JUST_IN_TIME,
              SelectionOrder.RANDOM,
              mock(ElementDestinationSelector.class),
              entitySelector,
              valueSelector,
              null,
              null,
              origin);
      InnerScoreDirector<TestdataAllowsUnassignedValuesListSolution, ?> director =
          mock(InnerScoreDirector.class);
      ListVariableState<TestdataAllowsUnassignedValuesListSolution, Object, Object> state =
          mock(ListVariableState.class, withSettings().stubOnly());
      when(state.getElementPosition(any()))
          .thenAnswer(
              invocation -> {
                int index = entity.getValueList().indexOf(invocation.getArgument(0));
                return index < 0 ? ElementPosition.unassigned() : ElementPosition.of(entity, index);
              });
      NearbyTestUtils.mockSupplyManager(director, state);
      solverScope = SelectorTestUtils.solvingStarted(selector, director, random);
      phaseScope = PlannerTestUtils.delegatingPhaseScope(solverScope);
      selector.phaseStarted(phaseScope);
      iterator = selector.iterator();
    }

    @Override
    public void close() {
      selector.phaseEnded(phaseScope);
      selector.solvingEnded(solverScope);
    }
  }
}
