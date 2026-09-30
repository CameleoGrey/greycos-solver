package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureConsumer;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureSession;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureUpdater;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListSwapMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListUnassignMove;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirectorFactoryFactory;
import greycos.solver.core.impl.score.director.WorkingSolutionMutationObserver;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GuidedLocalSearchAutomaticFeatureTrackerTest {

  @Test
  void scalarTokensDoNotRetainRepeatedRangeBoxes() {
    try (var director = modelDirector()) {
      director.setWorkingSolution(new Model());
      var registry = GuidedLocalSearchIdentityRegistry.create(director);
      var expected = registry.token(new BigDecimal("123456789.125"));
      for (int i = 0; i < 10_000; i++) {
        assertThat(registry.token(new BigDecimal("123456789.125"))).isEqualTo(expected);
        assertThat(registry.token(Long.valueOf("123456789")))
            .isEqualTo(registry.token(Long.valueOf("123456789")));
      }
      assertThat(registry)
          .extracting("tokens")
          .satisfies(
              value ->
                  assertThat((Map<?, ?>) value)
                      .hasSize(2)); // Only the two mutable planning entities.
    }
  }

  @Test
  void detachingTrackerClearsRelationshipCacheWithoutAnotherMove() {
    try (var director = modelDirector()) {
      director.setWorkingSolution(new Model());
      for (int phase = 0; phase < 2; phase++) {
        var tracker =
            GuidedLocalSearchFeatureTracker.attachAutomatic(
                director, null, emptySnapshots(), null, 1);
        var state = initializeRelationshipCache(director, tracker);
        assertThat(state).extracting("stateCarrier.relationshipObserver").isSameAs(tracker);
        assertThat(state).extracting("stateCarrier.relationships").isNotNull();
        tracker.close();
        assertRelationshipCacheReleased(state);
        assertThat(director.getWorkingSolutionMutationObserver()).isNull();
      }
    }
  }

  @Test
  void failedSessionCloseStillReleasesRelationshipCache() {
    var failure = new IllegalStateException("session close failure");
    GuidedLocalSearchFeatureProvider<Model, Object> provider =
        new GuidedLocalSearchFeatureProvider<>() {
          @Override
          public void extractFeatures(
              Model solution, GuidedLocalSearchFeatureConsumer<Object> consumer) {}

          @Override
          public GuidedLocalSearchFeatureSession<Model, Object> newSession() {
            return new GuidedLocalSearchFeatureSession<>() {
              @Override
              public void resetWorkingSolution(Model solution) {}

              @Override
              public void flushChanges(GuidedLocalSearchFeatureUpdater<Object> updater) {}

              @Override
              public void close() {
                throw failure;
              }
            };
          }
        };
    try (var director = modelDirector()) {
      director.setWorkingSolution(new Model());
      var tracker =
          GuidedLocalSearchFeatureTracker.attachAutomatic(
              director, provider, emptySnapshots(), null, 1);
      var state = initializeRelationshipCache(director, tracker);
      assertThatThrownBy(tracker::close).isSameAs(failure);
      assertRelationshipCacheReleased(state);
      assertThat(director.getWorkingSolutionMutationObserver()).isNull();
      tracker.close();
    }
  }

  @Test
  void closingWorkerDirectorReleasesRelationshipCache() {
    try (var director = modelDirector()) {
      director.setWorkingSolution(new Model());
      var worker = director.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
      Object state;
      try {
        var tracker =
            GuidedLocalSearchFeatureTracker.attachAutomatic(
                worker, null, emptySnapshots(), null, 1);
        state = initializeRelationshipCache(worker, tracker);
      } finally {
        worker.close();
      }
      assertRelationshipCacheReleased(state);
      assertThat(worker.getWorkingSolutionMutationObserver()).isNull();
    }
  }

  private static Object initializeRelationshipCache(
      InnerScoreDirector<Model, HardSoftScore> director,
      GuidedLocalSearchFeatureTracker<Model, Object> tracker) {
    tracker.markBaseline();
    var descriptor = director.getSolutionDescriptor().getListVariableDescriptor();
    var owner = director.getWorkingSolution().nodes.getFirst();
    director.executeTemporaryMove(
        new ListChangeMove<>(descriptor, owner, 0, owner, 3),
        ignored -> tracker.assertFromScratch(),
        true);
    return director.getListVariableState(descriptor);
  }

  private static void assertRelationshipCacheReleased(Object state) {
    assertThat(state).extracting("stateCarrier.relationshipObserver").isNull();
    assertThat(state).extracting("stateCarrier.relationships").isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"easy", "incremental", "streams"})
  void automaticAssignmentParityAcrossBackendsAndUndo(String backend) {
    var solution = TestdataSolution.generateSolution(2, 1);
    var config =
        switch (backend) {
          case "easy" ->
              new ScoreDirectorFactoryConfig()
                  .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
          case "incremental" ->
              new ScoreDirectorFactoryConfig()
                  .withIncrementalScoreCalculatorClass(
                      GuidedLocalSearchFeatureTrackerTest.BackendIncrementalCalculator.class);
          case "streams" ->
              new ScoreDirectorFactoryConfig()
                  .withConstraintProviderClass(TestdataConstraintProvider.class);
          default -> throw new IllegalArgumentException(backend);
        };
    var factory =
        new ScoreDirectorFactoryFactory<TestdataSolution, SimpleScore>(config)
            .buildScoreDirectorFactory(
                EnvironmentMode.PHASE_ASSERT, TestdataSolution.buildSolutionDescriptor());
    var table = new GuidedLocalSearchPenaltyTable<Object>();
    try (var director = factory.buildScoreDirector()) {
      director.setWorkingSolution(solution);
      try (var tracker =
          GuidedLocalSearchFeatureTracker.attachAutomatic(
              director, null, List.of(table.snapshot()), null, 0)) {
        tracker.markBaseline();
        assertThat(tracker.automaticFeatures()).hasSize(1);
        table.incrementMaximumUtility(tracker.automaticFeatures());
        tracker.updatePenalties(List.of(table.snapshot()));
        assertThat(tracker.aggregates().automatic()).containsExactly(GuidedLocalSearchNumber.ONE);
        var move =
            new ChangeMove<>(
                TestdataEntity.buildVariableDescriptorForValue(),
                solution.getEntityList().getFirst(),
                solution.getValueList().getLast());
        for (int i = 0; i < 2; i++) {
          director.executeTemporaryMove(
              move,
              ignored -> {
                assertThat(tracker.automaticDifferenceCount()).isEqualTo(2);
                assertThat(tracker.aggregates().automatic())
                    .containsExactly(GuidedLocalSearchNumber.ZERO);
                tracker.assertFromScratch();
              },
              true);
          // The undo and next candidate deliberately coalesce before a tracker flush.
        }
        tracker.assertFromScratch();
        assertThat(tracker.automaticDifferenceCount()).isZero();
        try (var worker = director.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
            var workerTracker =
                GuidedLocalSearchFeatureTracker.attachAutomatic(
                    worker, null, List.of(table.snapshot()), tracker.identityRegistry(), 0)) {
          workerTracker.markBaseline();
          assertThat(workerTracker.automaticFeatures()).isEqualTo(tracker.automaticFeatures());
          assertThat(workerTracker.aggregates()).isEqualTo(tracker.aggregates());
          workerTracker.assertFromScratch();
        }
      }
    }
  }

  @Test
  void mixedNoIdModelTracksInternalAdjacencyPinsUnassignmentAndUndo() {
    var solution = new Model();
    var movable = solution.nodes.getFirst();
    movable.pinIndex = 2;
    solution.nodes.getLast().pinned = true;
    try (var director = modelDirector()) {
      director.setWorkingSolution(solution);
      var descriptor = director.getSolutionDescriptor().getListVariableDescriptor();
      try (var tracker =
          GuidedLocalSearchFeatureTracker.attachAutomatic(
              director, null, emptySnapshots(), null, 1)) {
        tracker.markBaseline();
        var initial = Map.copyOf(tracker.automaticFeatures());
        assertThat(initial).hasSize(8);
        director.executeTemporaryMove(
            new ListChangeMove<>(descriptor, movable, 2, movable, 4),
            ignored -> {
              tracker.assertFromScratch();
              assertThat(tracker.automaticDifferenceCount()).isEqualTo(6);
              assertThat(tracker.automaticFeatures()).hasSize(8);
            },
            true);
        director.executeTemporaryMove(
            new ListSwapMove<>(descriptor, movable, 2, movable, 4),
            ignored -> {
              tracker.assertFromScratch();
              assertThat(tracker.automaticDifferenceCount()).isPositive();
            },
            true);
        director.executeTemporaryMove(
            new ListUnassignMove<>(descriptor, movable, 2),
            ignored -> {
              tracker.assertFromScratch();
              assertThat(tracker.automaticDifferenceCount()).isPositive();
            },
            true);
        tracker.assertFromScratch();
        assertThat(tracker.automaticFeatures()).isEqualTo(initial);
        assertThat(tracker.automaticDifferenceCount()).isZero();
        var basic =
            director
                .getSolutionDescriptor()
                .findEntityDescriptorOrFail(Node.class)
                .getGenuineVariableDescriptor("choice");
        director.beforeVariableChanged(basic, movable);
        movable.choice = 2;
        director.afterVariableChanged(basic, movable);
        assertThat(tracker.automaticDifferenceCount()).isEqualTo(2);
        tracker.markBaseline();
        tracker.assertFromScratch();
        assertThat(tracker.automaticDifferenceCount()).isZero();
      }
    }
  }

  @Test
  void emptyListsAndPinnedEndBoundariesRemainMutable() {
    var solution = new Model();
    var first = solution.nodes.getFirst();
    var second = solution.nodes.getLast();
    first.values.addAll(second.values);
    second.values.clear();
    first.pinIndex = first.values.size();
    try (var director = modelDirector()) {
      director.setWorkingSolution(solution);
      var descriptor = director.getSolutionDescriptor().getListVariableDescriptor();
      try (var tracker =
          GuidedLocalSearchFeatureTracker.attachAutomatic(
              director, null, emptySnapshots(), null, 1)) {
        tracker.markBaseline();
        assertThat(tracker.automaticFeatures())
            .hasSize(4); // Two basics, pinned return arc, empty arc.
        tracker.assertFromScratch();
        director.beforeListVariableChanged(
            descriptor, first, first.values.size(), first.values.size());
        director.afterListVariableChanged(
            descriptor, first, first.values.size(), first.values.size());
        tracker.assertFromScratch();
        assertThat(tracker.automaticDifferenceCount()).isZero();
      }
    }
  }

  @Test
  void internalRelationshipEventsIgnoreIndexOnlyChanges() {
    var solution = new Model();
    solution.values = IntStream.range(0, 100).mapToObj(Integer::toString).toList();
    var owner = solution.nodes.getFirst();
    owner.values = new ArrayList<>(solution.values);
    solution.nodes.getLast().values.clear();
    var changes = new AtomicInteger();
    try (var director = modelDirector()) {
      director.setWorkingSolution(solution);
      director.setWorkingSolutionMutationObserver(
          new WorkingSolutionMutationObserver<>() {
            @Override
            public void workingSolutionChanged() {}

            @Override
            public boolean requiresListVariableRelationshipChanges() {
              return true;
            }

            @Override
            public void afterListVariableRelationshipChanged(
                ListVariableDescriptor<Model> variable, Object element) {
              changes.incrementAndGet();
            }

            @Override
            public void close() {}
          });
      var descriptor = director.getSolutionDescriptor().getListVariableDescriptor();
      director.executeTemporaryMove(
          new ListChangeMove<>(descriptor, owner, 2, owner, 99),
          ignored -> {
            assertThat(changes.get()).isEqualTo(4);
          },
          true);
      assertThat(changes.get()).isEqualTo(8);
    }
  }

  @Test
  void vectorCostsSupplementAutomaticFeaturesAndReplaceAtomically() {
    var solution = new Model();
    var provider = new VectorProvider();
    var hard = new GuidedLocalSearchPenaltyTable<Object>();
    var soft = new GuidedLocalSearchPenaltyTable<Object>();
    try (var director = modelDirector()) {
      director.setWorkingSolution(solution);
      try (var tracker =
          GuidedLocalSearchFeatureTracker.attachAutomatic(
              director, provider, List.of(hard.snapshot(), soft.snapshot()), null, 1)) {
        tracker.markBaseline();
        assertThat(tracker.automaticFeatures()).isNotEmpty();
        hard.incrementMaximumUtility(tracker.customFeatures(0));
        soft.incrementMaximumUtility(tracker.customFeatures(1));
        tracker.updatePenalties(List.of(hard.snapshot(), soft.snapshot()));
        assertThat(tracker.aggregates().custom())
            .containsExactly(GuidedLocalSearchNumber.ONE, GuidedLocalSearchNumber.of(2));
        provider.cost = HardSoftScore.of(3, 0);
        provider.dirty = true;
        tracker.markBaseline();
        tracker.assertFromScratch();
        assertThat(tracker.aggregates().custom())
            .containsExactly(GuidedLocalSearchNumber.of(3), GuidedLocalSearchNumber.ZERO);
        assertThat(tracker.customFeatures(1)).isEmpty();
        provider.cost = SimpleScore.ONE;
        provider.dirty = true;
        assertThatThrownBy(tracker::aggregates)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("incompatible score cost");
        provider.cost = HardSoftScore.of(3, 4);
        tracker.assertFromScratch();
        assertThat(tracker.aggregates().custom())
            .containsExactly(GuidedLocalSearchNumber.of(3), GuidedLocalSearchNumber.of(4));
      }
    }
  }

  @Test
  void floatingCostsPreserveRepresentedValuesAndRejectNonfinite() {
    var values = new ArrayList<BigDecimal>();
    GuidedLocalSearchFeatureConsumer<String> consumer =
        new GuidedLocalSearchFeatureConsumer<>() {
          @Override
          public void accept(String key, long cost) {
            values.add(BigDecimal.valueOf(cost));
          }

          @Override
          public void accept(String key, BigDecimal cost) {
            values.add(cost);
          }
        };
    consumer.acceptFloat("f", 0.1f);
    consumer.acceptDouble("d", 0.1d);
    assertThat(values).containsExactly(new BigDecimal((double) 0.1f), new BigDecimal(0.1d));
    assertThatThrownBy(() -> consumer.acceptFloat("f", Float.NaN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> consumer.acceptDouble("d", Double.POSITIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> consumer.acceptScore("s", SimpleScore.ONE))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static List<GuidedLocalSearchPenaltyTable.Snapshot<Object>> emptySnapshots() {
    return List.of(
        new GuidedLocalSearchPenaltyTable<Object>().snapshot(),
        new GuidedLocalSearchPenaltyTable<Object>().snapshot());
  }

  private static InnerScoreDirector<Model, HardSoftScore> modelDirector() {
    return new ScoreDirectorFactoryFactory<Model, HardSoftScore>(
            new ScoreDirectorFactoryConfig().withEasyScoreCalculatorClass(ModelScore.class))
        .buildScoreDirectorFactory(
            EnvironmentMode.PHASE_ASSERT,
            SolutionDescriptor.buildSolutionDescriptor(Model.class, Node.class))
        .buildScoreDirector();
  }

  @PlanningSolution
  public static class Model {
    @PlanningEntityCollectionProperty
    public List<Node> nodes =
        new ArrayList<>(List.of(new Node("a", "b", "c", "d", "e"), new Node("f", "g", "h")));

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "values")
    public List<String> values = List.of("a", "b", "c", "d", "e", "f", "g", "h");

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "choices")
    public List<Integer> choices = List.of(1, 2);

    @PlanningScore public HardSoftScore score;
  }

  @PlanningEntity
  public static class Node {
    @PlanningVariable(valueRangeProviderRefs = "choices")
    public Integer choice = 1;

    @PlanningListVariable(valueRangeProviderRefs = "values")
    public List<String> values = new ArrayList<>();

    @PlanningPin public boolean pinned;
    @PlanningPinToIndex public int pinIndex;

    public Node() {}

    Node(String... values) {
      this.values.addAll(List.of(values));
    }
  }

  public static class ModelScore implements EasyScoreCalculator<Model, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(Model solution) {
      return HardSoftScore.ofSoft(
          -solution.nodes.stream().mapToLong(node -> node.choice == null ? 0 : node.choice).sum());
    }
  }

  private static final class VectorProvider
      implements GuidedLocalSearchFeatureProvider<Model, Object> {
    Score<?> cost = HardSoftScore.of(1, 2);
    boolean dirty = true;

    @Override
    public void extractFeatures(Model solution, GuidedLocalSearchFeatureConsumer<Object> consumer) {
      consumer.acceptScore("custom", cost);
    }

    @Override
    public GuidedLocalSearchFeatureSession<Model, Object> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        @Override
        public void resetWorkingSolution(Model solution) {
          dirty = true;
        }

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<Object> updater) {
          if (dirty) {
            updater.acceptScore("custom", cost);
            dirty = false;
          }
        }
      };
    }
  }
}
