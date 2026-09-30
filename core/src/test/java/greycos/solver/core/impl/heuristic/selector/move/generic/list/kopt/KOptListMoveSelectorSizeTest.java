package greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt;

import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.phaseStarted;
import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.solvingStarted;
import static greycos.solver.core.testcotwin.list.TestdataListUtils.getListVariableDescriptor;
import static greycos.solver.core.testcotwin.list.TestdataListUtils.mockIterableValueSelector;
import static greycos.solver.core.testutil.PlannerTestUtils.mockScoreDirector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.heuristic.selector.move.decorator.FilteringMoveSelector;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(10)
class KOptListMoveSelectorSizeTest {

  @ParameterizedTest
  @CsvSource({"0,2,2,0", "2,2,2,4", "8,2,2,64", "8,3,3,140", "8,2,3,204", "3,3,3,0", "4,3,3,4"})
  void includesConfiguredMaximumAndTwoOptEndpointPairs(
      long values, int minimumK, int maximumK, long expected) {
    assertThat(selector(values, minimumK, maximumK).getSize()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"4000000000,2,2", "3000000,3,3", "8000000,3,3", "43,2,11", "19,18,18", "23,22,22"})
  void overflowingEstimatesSaturateInsteadOfDisablingFiltering(
      long values, int minimumK, int maximumK) {
    assertThat(selector(values, minimumK, maximumK).getSize()).isEqualTo(Long.MAX_VALUE);
  }

  private static KOptListMoveSelector<TestdataListSolution> selector(
      long values, int minimumK, int maximumK) {
    var descriptor = TestdataListEntity.buildVariableDescriptorForValueList();
    var origins = mockIterableValueSelector(descriptor);
    var destinations = mockIterableValueSelector(descriptor);
    when(origins.getSize()).thenReturn(values);
    when(destinations.getSize()).thenReturn(values);
    var distribution = new int[maximumK - minimumK + 1];
    Arrays.fill(distribution, 1);
    return new KOptListMoveSelector<>(
        descriptor, origins, destinations, minimumK, maximumK, distribution);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void filteredDefaultTwoOptOffersADoableMove(boolean singletonRoutes) {
    var first = new TestdataListValue("first");
    var second = new TestdataListValue("second");
    var third = new TestdataListValue("third");
    var fourth = new TestdataListValue("fourth");
    var solution = new TestdataListSolution();
    if (singletonRoutes) {
      solution.setValueList(List.of(first, second));
      solution.setEntityList(
          List.of(
              TestdataListEntity.createWithValues("one", first),
              TestdataListEntity.createWithValues("two", second)));
    } else {
      solution.setValueList(List.of(first, second, third, fourth));
      solution.setEntityList(
          List.of(TestdataListEntity.createWithValues("one", first, second, third, fourth)));
    }
    try (var director = mockScoreDirector(TestdataListSolution.buildSolutionDescriptor())) {
      director.setWorkingSolution(solution);
      var descriptor = getListVariableDescriptor(director);
      var origins = mockIterableValueSelector(descriptor, solution.getValueList().toArray());
      var destinations =
          mockIterableValueSelector(descriptor, singletonRoutes ? second : third, first);
      var selector =
          new KOptListMoveSelector<>(descriptor, origins, destinations, 2, 2, new int[] {1});
      var filtered =
          FilteringMoveSelector.of(
              selector,
              (scoreDirector, move) ->
                  ((AbstractSelectorBasedMove<TestdataListSolution>) move)
                      .isMoveDoable(scoreDirector));
      var solverScope = solvingStarted(filtered, director, new Random(0L));
      var phaseScope = phaseStarted(filtered, solverScope);
      var originalFirstRoute = List.copyOf(solution.getEntityList().getFirst().getValueList());

      assertThat(filtered.getSize()).isPositive();
      var iterator = filtered.iterator();
      assertThat(iterator.hasNext()).isTrue();
      var move = (AbstractSelectorBasedMove<TestdataListSolution>) iterator.next();
      assertThat(move.isMoveDoable(director)).isTrue();
      director.executeMove(move);
      assertThat(solution.getEntityList().getFirst().getValueList())
          .isNotEqualTo(originalFirstRoute);

      filtered.phaseEnded(phaseScope);
      filtered.solvingEnded(solverScope);
    }
  }

  @Test
  void filteredTwoOptStillExcludesPinnedEndpoints() {
    var solution = TestdataPinnedWithIndexListSolution.generateInitializedSolution(10, 2);
    var firstRoute = solution.getEntityList().getFirst();
    var secondRoute = solution.getEntityList().getLast();
    firstRoute.setPinIndex(2);
    secondRoute.setPinned(true);
    var prefix = List.copyOf(firstRoute.getValueList().subList(0, 2));
    var pinnedRoute = List.copyOf(secondRoute.getValueList());
    try (var director =
        mockScoreDirector(TestdataPinnedWithIndexListSolution.buildSolutionDescriptor())) {
      director.setWorkingSolution(solution);
      var descriptor = getListVariableDescriptor(director);
      var origins =
          mockIterableValueSelector(
              descriptor, prefix.getFirst(), firstRoute.getValueList().get(2));
      var destinations =
          mockIterableValueSelector(
              descriptor, pinnedRoute.getFirst(), firstRoute.getValueList().get(4));
      var selector =
          new KOptListMoveSelector<>(descriptor, origins, destinations, 2, 2, new int[] {1});
      var filtered =
          FilteringMoveSelector.of(
              selector,
              (scoreDirector, move) ->
                  ((AbstractSelectorBasedMove<TestdataPinnedWithIndexListSolution>) move)
                      .isMoveDoable(scoreDirector));
      var solverScope = solvingStarted(filtered, director, new Random(0L));
      var phaseScope = phaseStarted(filtered, solverScope);

      var iterator = filtered.iterator();
      assertThat(iterator.hasNext()).isTrue();
      var move = iterator.next();
      director.executeMove(move);
      assertThat(firstRoute.getValueList().subList(0, 2)).containsExactlyElementsOf(prefix);
      assertThat(secondRoute.getValueList()).containsExactlyElementsOf(pinnedRoute);
      assertThat(
              solution.getEntityList().stream()
                  .flatMap(entity -> entity.getValueList().stream())
                  .map(TestdataPinnedWithIndexListValue::getCode)
                  .toList())
          .containsExactlyInAnyOrderElementsOf(
              solution.getValueList().stream()
                  .map(TestdataPinnedWithIndexListValue::getCode)
                  .toList());

      filtered.phaseEnded(phaseScope);
      filtered.solvingEnded(solverScope);
    }
  }
}
