package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;

import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListChangeMove;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;

class GuidedLocalSearchMultipleListTest {

  @Test
  void tracksBothFieldsOnOneOwnerThroughMovesUndoAndReplacement() {
    var solution = TestdataMultipleListSolution.generateInitializedSolution(2, 3, 2);
    try (var director = TestdataMultipleListSolution.buildScoreDirector()) {
      director.setWorkingSolution(solution);
      try (var tracker =
          GuidedLocalSearchFeatureTracker.attachAutomatic(
              director,
              null,
              List.of(new GuidedLocalSearchPenaltyTable.Snapshot<>(0, java.util.Map.of())),
              null,
              0,
              true,
              true)) {
        tracker.markBaseline();
        var baseline = new LinkedHashMap<>(tracker.automaticFeatures());
        // Five basic assignments, five ownership features and nine list arcs.
        assertThat(baseline).hasSize(19);
        tracker.assertFromScratch();
        var counts = new LinkedHashMap<Object, Long>();
        baseline.keySet().forEach(key -> counts.put(key, 1L));
        tracker.updatePenalties(List.of(new GuidedLocalSearchPenaltyTable.Snapshot<>(1, counts)));
        var first = solution.getEntityList().getFirst();
        var second = solution.getEntityList().getLast();
        var priorities = tracker.originPriorities(0);
        assertThat(priorities.subListPriority(first, "firstValueList", 0, 1).numerator().signum())
            .isPositive();
        assertThat(priorities.subListPriority(first, "secondValueList", 0, 2).numerator().signum())
            .isPositive();
        for (var name : List.of("firstValueList", "secondValueList")) {
          var descriptor =
              director
                  .getSolutionDescriptor()
                  .resolveListVariableDescriptor(TestdataMultipleListEntity.class, name);
          director.executeTemporaryMove(
              new ListChangeMove<>(descriptor, first, 0, second, 1),
              ignored -> {
                tracker.assertFromScratch();
                assertThat(tracker.automaticDifferenceCount()).isPositive();
              },
              true);
          tracker.assertFromScratch();
          assertThat(tracker.automaticFeatures()).isEqualTo(baseline);
          assertThat(tracker.automaticDifferenceCount()).isZero();
        }
        director.setWorkingSolution(
            TestdataMultipleListSolution.generateInitializedSolution(2, 3, 2));
        tracker.assertFromScratch();
        assertThat(tracker.automaticFeatures()).hasSize(19);
      }
    }
  }

  @Test
  void emptyListFeaturesAndPrioritiesStaySeparateOnTheSameOwner() {
    var solution = TestdataMultipleListSolution.generateInitializedSolution(1, 1, 2);
    try (var director = TestdataMultipleListSolution.buildScoreDirector()) {
      director.setWorkingSolution(solution);
      try (var tracker =
          GuidedLocalSearchFeatureTracker.attachAutomatic(
              director,
              null,
              List.of(new GuidedLocalSearchPenaltyTable.Snapshot<>(0, java.util.Map.of())),
              null,
              0)) {
        tracker.markBaseline();
        var counts = new LinkedHashMap<Object, Long>();
        tracker.automaticFeatures().keySet().forEach(key -> counts.put(key, 1L));
        tracker.updatePenalties(List.of(new GuidedLocalSearchPenaltyTable.Snapshot<>(1, counts)));
        var empty = solution.getEntityList().getLast();
        var before = tracker.originPriorities(0);
        assertThat(before.boundaryPriority(empty, "firstValueList").numerator().signum())
            .isPositive();
        assertThat(before.boundaryPriority(empty, "secondValueList").numerator().signum())
            .isPositive();
        var descriptor =
            director
                .getSolutionDescriptor()
                .resolveListVariableDescriptor(TestdataMultipleListEntity.class, "firstValueList");
        director.executeTemporaryMove(
            new ListChangeMove<>(descriptor, solution.getEntityList().getFirst(), 0, empty, 0),
            ignored -> {
              tracker.assertFromScratch();
              assertThat(tracker.originPriorities(0).boundaryPriority(empty, "secondValueList"))
                  .isEqualTo(before.boundaryPriority(empty, "secondValueList"));
            },
            true);
        tracker.assertFromScratch();
      }
    }
  }
}
