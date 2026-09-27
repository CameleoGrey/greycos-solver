package greycos.solver.core.impl.heuristic.selector.common.nearby;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionDistributionType;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NearbySelectionSharingTest {

  @Test
  void sharedPopulationReusesDistancesWithoutSharingLiveEligibility() {
    try (var fixture = new Fixture()) {
      var population = fixture.population(fixture.a, fixture.b);
      var reversed = new AtomicBoolean();
      var first =
          fixture.selector(
              population, (director, value) -> value == (reversed.get() ? fixture.b : fixture.a));
      var second =
          fixture.selector(
              population, (director, value) -> value == (reversed.get() ? fixture.a : fixture.b));
      fixture.start();
      var demands = fixture.demands();
      assertThat(demands.get(0)).isEqualTo(demands.get(1));
      assertThat(fixture.supplyManager.getActiveCount(demands.get(0))).isEqualTo(2);
      fixture.assertSupplyIdentity(demands, true);

      assertThat(first.iterator().next()).isEqualTo(ElementPosition.of(fixture.entity, 1));
      assertThat(second.iterator().next()).isEqualTo(ElementPosition.of(fixture.entity, 2));
      assertThat(CountingMeter.calls).hasValue(3);
      reversed.set(true);
      assertThat(first.iterator().next()).isEqualTo(ElementPosition.of(fixture.entity, 2));
      assertThat(second.iterator().next()).isEqualTo(ElementPosition.of(fixture.entity, 1));
      assertThat(CountingMeter.calls).hasValue(3);
      fixture.endPhases();
      assertThat(fixture.supplyManager.getActiveCount(demands.get(0))).isZero();
    }
  }

  @Test
  void distinctPopulationsHaveSeparateSuppliesAndRows() {
    try (var fixture = new Fixture()) {
      var first = fixture.selector(fixture.population(fixture.a), (director, value) -> true);
      var second = fixture.selector(fixture.population(fixture.b), (director, value) -> true);
      fixture.start();
      var demands = fixture.demands();
      assertThat(demands.get(0)).isNotEqualTo(demands.get(1));
      assertThat(fixture.supplyManager.getActiveCount(demands.get(0))).isEqualTo(1);
      assertThat(fixture.supplyManager.getActiveCount(demands.get(1))).isEqualTo(1);
      fixture.assertSupplyIdentity(demands, false);

      assertThat(first.iterator().next()).isEqualTo(ElementPosition.of(fixture.entity, 1));
      assertThat(second.iterator().next()).isEqualTo(ElementPosition.of(fixture.entity, 2));
      assertThat(CountingMeter.calls).hasValue(4);
      assertThat(first.iterator().next()).isEqualTo(ElementPosition.of(fixture.entity, 1));
      assertThat(second.iterator().next()).isEqualTo(ElementPosition.of(fixture.entity, 2));
      assertThat(CountingMeter.calls).hasValue(4);
      fixture.endPhases();
      assertThat(fixture.supplyManager.getActiveCount(demands.get(0))).isZero();
      assertThat(fixture.supplyManager.getActiveCount(demands.get(1))).isZero();
    }
  }

  public static final class CountingMeter implements NearbyDistanceMeter<Object, Object> {
    static final AtomicInteger calls = new AtomicInteger();

    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      calls.incrementAndGet();
      if (destination instanceof TestdataListEntity) {
        return 10;
      }
      return ((TestdataListValue) destination).getCode().equals("a") ? 0 : 1;
    }
  }

  private static final class Fixture implements AutoCloseable {
    final TestdataListValue a = new TestdataListValue("a");
    final TestdataListValue b = new TestdataListValue("b");
    final TestdataListEntity entity = TestdataListEntity.createWithValues("entity", a, b);
    final HeuristicConfigPolicy<TestdataListSolution> policy;
    final InnerScoreDirector<TestdataListSolution, SimpleScore> scoreDirector;
    final SupplyManager supplyManager;
    final EntitySelector<TestdataListSolution> entities;
    final IterableValueSelector<TestdataListSolution> origin;
    final List<NearbyDestinationSelector<TestdataListSolution>> selectors = new ArrayList<>();
    SolverScope<TestdataListSolution> solverScope;
    AbstractPhaseScope<TestdataListSolution> phaseScope;
    boolean phasesEnded;

    Fixture() {
      CountingMeter.calls.set(0);
      var descriptor = TestdataListSolution.buildSolutionDescriptor();
      policy = buildHeuristicConfigPolicy(descriptor);
      // PlannerTestUtils delegates to a real director, whose VariableSupport owns the supply map.
      scoreDirector = PlannerTestUtils.mockScoreDirector(descriptor);
      var solution = new TestdataListSolution();
      solution.setEntityList(List.of(entity));
      solution.setValueList(List.of(a, b));
      scoreDirector.setWorkingSolution(solution);
      supplyManager = spy(scoreDirector.getSupplyManager());
      doReturn(supplyManager).when(scoreDirector).getSupplyManager();
      entities =
          SelectorTestUtils.mockEntitySelector(
              descriptor.findEntityDescriptorOrFail(TestdataListEntity.class), entity);
      origin = population(a);
    }

    IterableValueSelector<TestdataListSolution> population(TestdataListValue... values) {
      return SelectorTestUtils.mockIterableValueSelector(
          policy.getSolutionDescriptor().getListVariableDescriptor(), (Object[]) values);
    }

    NearbyDestinationSelector<TestdataListSolution> selector(
        IterableValueSelector<TestdataListSolution> population,
        SelectionFilter<TestdataListSolution, Object> filter) {
      var selector =
          new NearbyDestinationSelector<>(
              new DestinationSelectorConfig(),
              policy,
              new NearbySelectionConfig()
                  .withNearbyDistanceMeterClass(CountingMeter.class)
                  .withNearbySelectionDistributionType(
                      NearbySelectionDistributionType.BLOCK_DISTRIBUTION)
                  .withBlockDistributionSizeMaximum(1),
              SelectionCacheType.JUST_IN_TIME,
              SelectionOrder.RANDOM,
              new ElementDestinationSelector<>(entities, population, true),
              entities,
              population,
              null,
              null,
              origin);
      selector.configureSelectionSources(
          new NearbySelectionSource<>(entities, entities, (director, candidate) -> false),
          new NearbySelectionSource<>(population, population, filter));
      selectors.add(selector);
      return selector;
    }

    void start() {
      solverScope =
          SelectorTestUtils.solvingStarted(selectors.getFirst(), scoreDirector, new Random(0));
      selectors.get(1).solvingStarted(solverScope);
      phaseScope = PlannerTestUtils.delegatingPhaseScope(solverScope);
      selectors.forEach(selector -> selector.phaseStarted(phaseScope));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    List<NearbyDistanceMatrixDemand<Object, Object>> demands() {
      var captor = ArgumentCaptor.forClass(NearbyDistanceMatrixDemand.class);
      verify(supplyManager, times(2)).demand(captor.capture());
      return (List) captor.getAllValues();
    }

    void assertSupplyIdentity(
        List<NearbyDistanceMatrixDemand<Object, Object>> demands, boolean shared) {
      var firstSupply = supplyManager.demand(demands.get(0));
      var secondSupply = supplyManager.demand(demands.get(1));
      try {
        if (shared) {
          assertThat(firstSupply).isSameAs(secondSupply);
        } else {
          assertThat(firstSupply).isNotSameAs(secondSupply);
        }
      } finally {
        assertThat(supplyManager.cancel(demands.get(0))).isTrue();
        assertThat(supplyManager.cancel(demands.get(1))).isTrue();
      }
    }

    void endPhases() {
      selectors.forEach(selector -> selector.phaseEnded(phaseScope));
      phasesEnded = true;
    }

    @Override
    public void close() {
      if (phaseScope != null && !phasesEnded) {
        endPhases();
      }
      if (solverScope != null) {
        selectors.forEach(selector -> selector.solvingEnded(solverScope));
      }
      scoreDirector.close();
    }
  }
}
