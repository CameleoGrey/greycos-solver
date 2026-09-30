package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureConsumer;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureSession;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureUpdater;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListUnassignMove;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirectorFactoryFactory;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.TestdataListVarEasyScoreCalculator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GuidedLocalSearchFeatureTrackerTest {

  @ParameterizedTest
  @ValueSource(strings = {"easy", "incremental", "streams"})
  void observesTemporaryMoveUndoAndCoalescedChangesAcrossScoreBackends(String backend) {
    var solution = TestdataSolution.generateSolution(2, 1);
    var provider = new AssignmentProvider();
    var table = new GuidedLocalSearchPenaltyTable<String>();
    try (var director = director(backend);
        var tracker =
            GuidedLocalSearchFeatureTracker.attach(director, provider, table.snapshot())) {
      director.setWorkingSolution(solution);
      assertThat(provider.session.resetCount).isZero();
      assertThat(provider.session.callbackCount).isZero();
      var originalScore = director.calculateScore();
      assertThat(tracker.aggregate()).isEqualTo(GuidedLocalSearchNumber.ZERO);
      assertThat(provider.session.resetCount).isOne();
      table.incrementMaximumUtility(tracker.activeFeatures());
      tracker.updatePenalties(table.snapshot());
      assertThat(tracker.aggregate()).isEqualTo(GuidedLocalSearchNumber.of(10));
      var entity = solution.getEntityList().getFirst();
      var originalValue = entity.getValue();
      var move =
          new ChangeMove<>(
              TestdataEntity.buildVariableDescriptorForValue(),
              entity,
              solution.getValueList().get(1));
      for (int i = 0; i < 2; i++) {
        director.executeTemporaryMove(
            move,
            ignored -> {
              assertThat(tracker.aggregate()).isEqualTo(GuidedLocalSearchNumber.ZERO);
              tracker.assertFromScratch();
            },
            true);
        // Deliberately do not flush after undo: the next trial must coalesce both changes.
        assertThat(entity.getValue()).isSameAs(originalValue);
        assertThat(solution.getScore()).isEqualTo(originalScore.raw());
      }
      assertThat(tracker.aggregate()).isEqualTo(GuidedLocalSearchNumber.of(10));
      tracker.assertFromScratch();
      assertThat(director.calculateScore()).isEqualTo(originalScore);
      assertThat(provider.session.callbackCount).isGreaterThanOrEqualTo(8);
    }
    assertThat(provider.session.closeCount).isOne();
  }

  @Test
  void costReplacementKeepsCountAndRefreshesGenerationBeforeDirtyChanges() {
    var solution = TestdataSolution.generateSolution(2, 1);
    var provider = new AssignmentProvider();
    provider.contextualCost = true;
    var table = new GuidedLocalSearchPenaltyTable<String>();
    try (var director = director("easy")) {
      director.setWorkingSolution(solution);
      try (var tracker =
          GuidedLocalSearchFeatureTracker.attach(director, provider, table.snapshot())) {
        table.incrementMaximumUtility(tracker.activeFeatures());
        tracker.updatePenalties(table.snapshot());
        var entity = solution.getEntityList().getFirst();
        var move =
            new ChangeMove<>(
                TestdataEntity.buildVariableDescriptorForValue(),
                entity,
                solution.getValueList().get(1));
        director.executeTemporaryMove(
            move,
            ignored -> {
              assertThat(tracker.aggregate()).isEqualTo(GuidedLocalSearchNumber.of(20));
              tracker.assertFromScratch();
            },
            false);
        // Cached features are the trial's cost 20, while undo has dirtied it back to 10.
        table.incrementMaximumUtility(Map.of(entity.getCode(), GuidedLocalSearchNumber.of(10)));
        tracker.updatePenalties(table.snapshot());
        assertThat(tracker.aggregate()).isEqualTo(GuidedLocalSearchNumber.of(20));
        tracker.assertFromScratch();
        assertThat(table.count(entity.getCode())).isEqualTo(2);
      }
    }
  }

  @Test
  void workingCloneAndProblemChangesRebuildSessionWithoutLosingPenaltyIdentity() {
    var solution = TestdataSolution.generateSolution(2, 1);
    var provider = new AssignmentProvider();
    var table = new GuidedLocalSearchPenaltyTable<String>();
    try (var director = director("easy")) {
      director.setWorkingSolution(solution);
      try (var tracker =
          GuidedLocalSearchFeatureTracker.attach(director, provider, table.snapshot())) {
        table.incrementMaximumUtility(tracker.activeFeatures());
        tracker.updatePenalties(table.snapshot());
        director.setWorkingSolution(director.cloneWorkingSolution());
        assertThat(tracker.aggregate()).isEqualTo(GuidedLocalSearchNumber.of(10));
        assertThat(provider.session.resetCount).isEqualTo(2);
        tracker.assertFromScratch();
        var workingEntity = director.getWorkingSolution().getEntityList().getFirst();
        director.beforeProblemPropertyChanged(workingEntity);
        director.afterProblemPropertyChanged(workingEntity);
        assertThat(tracker.aggregate()).isEqualTo(GuidedLocalSearchNumber.of(10));
        assertThat(provider.session.resetCount).isEqualTo(3);
      }
    }
  }

  @Test
  void flushFailureKeepsOriginalCauseAndUndoesMoveWithoutCallingDamagedSession() {
    var solution = TestdataSolution.generateSolution(2, 1);
    var provider = new AssignmentProvider();
    var table = new GuidedLocalSearchPenaltyTable<String>();
    try (var director = director("easy")) {
      director.setWorkingSolution(solution);
      var originalScore = director.calculateScore();
      try (var tracker =
          GuidedLocalSearchFeatureTracker.attach(director, provider, table.snapshot())) {
        tracker.aggregate();
        var entity = solution.getEntityList().getFirst();
        var originalValue = entity.getValue();
        var failure = new IllegalStateException("primary provider failure");
        provider.session.flushFailure = failure;
        var move =
            new ChangeMove<>(
                TestdataEntity.buildVariableDescriptorForValue(),
                entity,
                solution.getValueList().get(1));
        assertThatThrownBy(
                () -> director.executeTemporaryMove(move, ignored -> tracker.aggregate(), false))
            .isSameAs(failure);
        assertThat(entity.getValue()).isSameAs(originalValue);
        assertThat(solution.getScore()).isEqualTo(originalScore.raw());
        assertThat(provider.session.callbackCountAfterFailure).isZero();
        // A deliberate reset can recover the session, independently of the failed partial flush.
        provider.session.flushFailure = null;
        tracker.assertFromScratch();
      }
    }
  }

  @Test
  void independentExtractorDetectsMissingUpdatesAndDuplicateKeys() {
    var solution = TestdataSolution.generateSolution(2, 1);
    var provider = new AssignmentProvider();
    try (var director = director("easy")) {
      director.setWorkingSolution(solution);
      try (var tracker =
          GuidedLocalSearchFeatureTracker.attach(
              director, provider, new GuidedLocalSearchPenaltyTable<String>().snapshot())) {
        tracker.assertFromScratch();
        provider.session.ignoreUpdates = true;
        director.executeMove(
            new ChangeMove<>(
                TestdataEntity.buildVariableDescriptorForValue(),
                solution.getEntityList().getFirst(),
                solution.getValueList().get(1)));
        assertThatIllegalStateException()
            .isThrownBy(tracker::assertFromScratch)
            .withMessageContaining("feature corruption");
        tracker.reset();
        provider.duplicate = true;
        assertThatIllegalStateException()
            .isThrownBy(tracker::assertFromScratch)
            .withMessageContaining("duplicate key");
      }
    }
  }

  @Test
  void invalidCostsAndAbsentRemovalFailWithContext() {
    var solution = TestdataSolution.generateSolution(2, 1);
    var provider = new AssignmentProvider();
    try (var director = director("easy")) {
      director.setWorkingSolution(solution);
      try (var tracker =
          GuidedLocalSearchFeatureTracker.attach(
              director, provider, new GuidedLocalSearchPenaltyTable<String>().snapshot())) {
        provider.session.negativeCost = true;
        assertThatIllegalArgumentException()
            .isThrownBy(tracker::aggregate)
            .withMessageContaining("AssignmentProvider")
            .withMessageContaining("nonnegative");
        provider.session.negativeCost = false;
        provider.session.nullCost = true;
        assertThatIllegalArgumentException()
            .isThrownBy(tracker::aggregate)
            .withMessageContaining("AssignmentProvider")
            .withMessageContaining("null cost");
        provider.session.nullCost = false;
        provider.session.removeAbsent = true;
        assertThatIllegalStateException()
            .isThrownBy(tracker::aggregate)
            .withMessageContaining("removed absent feature");
      }
    }
  }

  @Test
  void listShadowsAreSettledBeforeResetAndEventsIncludeTrialAndUndo() {
    var solution = TestdataListSolution.generateUninitializedSolution(4, 2);
    solution.getEntityList().get(0).getValueList().addAll(solution.getValueList());
    var provider = new ListProvider();
    var factory =
        new ScoreDirectorFactoryFactory<TestdataListSolution, SimpleScore>(
                new ScoreDirectorFactoryConfig()
                    .withEasyScoreCalculatorClass(TestdataListVarEasyScoreCalculator.class))
            .buildScoreDirectorFactory(
                EnvironmentMode.PHASE_ASSERT, TestdataListSolution.buildSolutionDescriptor());
    try (var director = factory.buildScoreDirector();
        var tracker =
            GuidedLocalSearchFeatureTracker.attach(
                director, provider, new GuidedLocalSearchPenaltyTable<String>().snapshot())) {
      director.setWorkingSolution(solution);
      assertThat(provider.resetCount).isZero();
      tracker.assertFromScratch();
      assertThat(provider.resetCount).isOne();
      var move =
          new ListChangeMove<>(
              TestdataListEntity.buildVariableDescriptorForValueList(),
              solution.getEntityList().get(0),
              0,
              solution.getEntityList().get(1),
              0);
      director.executeTemporaryMove(move, ignored -> tracker.assertFromScratch(), true);
      tracker.assertFromScratch();
      director.executeTemporaryMove(
          new ListUnassignMove<>(
              TestdataListEntity.buildVariableDescriptorForValueList(),
              solution.getEntityList().get(0),
              0),
          ignored -> {
            tracker.assertFromScratch();
            assertThat(tracker.activeFeatures()).hasSize(3);
          },
          true);
      tracker.assertFromScratch();
      assertThat(provider.shadowEvents).isGreaterThan(0);
      assertThat(provider.listEvents).isGreaterThanOrEqualTo(4);
      assertThat(provider.assignmentEvents).isPositive();
      assertThat(provider.unassignmentEvents).isPositive();
    }
  }

  @Test
  void closingDirectorClosesSessionOnceAndDetachesObserver() {
    var provider = new AssignmentProvider();
    var director = director("easy");
    var tracker =
        GuidedLocalSearchFeatureTracker.attach(
            director, provider, new GuidedLocalSearchPenaltyTable<String>().snapshot());
    director.close();
    tracker.close();
    assertThat(provider.session.closeCount).isOne();
    assertThat(director.getWorkingSolutionMutationObserver()).isNull();
  }

  @Test
  void sharedSessionsAreRejectedBeforeTheyCanCorruptTwoWorkingClones() {
    var delegate = new AssignmentProvider();
    var sharedSession = delegate.newSession();
    var provider =
        new GuidedLocalSearchFeatureProvider<TestdataSolution, String>() {
          @Override
          public void extractFeatures(
              TestdataSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {
            delegate.extractFeatures(solution, consumer);
          }

          @Override
          public GuidedLocalSearchFeatureSession<TestdataSolution, String> newSession() {
            return sharedSession;
          }
        };
    try (var first = director("easy");
        var second = director("easy");
        var tracker =
            GuidedLocalSearchFeatureTracker.attach(
                first, provider, new GuidedLocalSearchPenaltyTable<String>().snapshot())) {
      assertThatIllegalStateException()
          .isThrownBy(
              () ->
                  GuidedLocalSearchFeatureTracker.attach(
                      second, provider, new GuidedLocalSearchPenaltyTable<String>().snapshot()))
          .withMessageContaining("reused an active session");
      assertThat(second.getWorkingSolutionMutationObserver()).isNull();
    }
    assertThat(delegate.session.closeCount).isOne();
  }

  private static InnerScoreDirector<TestdataSolution, SimpleScore> director(String backend) {
    var config =
        switch (backend) {
          case "easy" ->
              new ScoreDirectorFactoryConfig()
                  .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
          case "incremental" ->
              new ScoreDirectorFactoryConfig()
                  .withIncrementalScoreCalculatorClass(BackendIncrementalCalculator.class);
          case "streams" ->
              new ScoreDirectorFactoryConfig()
                  .withConstraintProviderClass(TestdataConstraintProvider.class);
          default -> throw new IllegalArgumentException(backend);
        };
    return new ScoreDirectorFactoryFactory<TestdataSolution, SimpleScore>(config)
        .buildScoreDirectorFactory(
            EnvironmentMode.PHASE_ASSERT, TestdataSolution.buildSolutionDescriptor())
        .buildScoreDirector();
  }

  public static class BackendIncrementalCalculator
      implements IncrementalScoreCalculator<TestdataSolution, SimpleScore> {
    private TestdataSolution solution;

    @Override
    public void resetWorkingSolution(TestdataSolution solution) {
      this.solution = solution;
    }

    @Override
    public void beforeVariableChanged(Object entity, String variableName) {}

    @Override
    public void afterVariableChanged(Object entity, String variableName) {}

    @Override
    public SimpleScore calculateScore() {
      return new TestdataEasyScoreCalculator().calculateScore(solution);
    }
  }

  private static final class AssignmentProvider
      implements GuidedLocalSearchFeatureProvider<TestdataSolution, String> {
    private AssignmentSession session;
    private boolean contextualCost;
    private boolean duplicate;

    private String key(TestdataEntity entity) {
      return contextualCost
          ? entity.getCode()
          : entity.getCode() + "/" + entity.getValue().getCode();
    }

    private long cost(TestdataEntity entity) {
      return entity.getValue().getCode().endsWith("0") ? 10L : 20L;
    }

    @Override
    public void extractFeatures(
        TestdataSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {
      for (var entity : solution.getEntityList()) {
        if (entity.getValue() != null) {
          consumer.accept(key(entity), cost(entity));
          if (duplicate) {
            consumer.accept(key(entity), cost(entity));
          }
        }
      }
    }

    @Override
    public GuidedLocalSearchFeatureSession<TestdataSolution, String> newSession() {
      session = new AssignmentSession();
      return session;
    }

    private final class AssignmentSession
        implements GuidedLocalSearchFeatureSession<TestdataSolution, String> {
      private final Map<TestdataEntity, String> previousKeys = new IdentityHashMap<>();
      private final Set<TestdataEntity> dirty = new LinkedHashSet<>();
      private int resetCount;
      private int callbackCount;
      private int closeCount;
      private int callbackCountAfterFailure;
      private boolean poisoned;
      private boolean ignoreUpdates;
      private boolean negativeCost;
      private boolean nullCost;
      private boolean removeAbsent;
      private RuntimeException flushFailure;

      @Override
      public void resetWorkingSolution(TestdataSolution solution) {
        resetCount++;
        poisoned = false;
        previousKeys.clear();
        dirty.clear();
        dirty.addAll(solution.getEntityList());
      }

      @Override
      public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {
        if (removeAbsent) {
          updater.remove("absent");
        }
        for (var entity : dirty) {
          var oldKey = previousKeys.remove(entity);
          if (oldKey != null) {
            updater.remove(oldKey);
          }
          if (entity.getValue() != null) {
            if (nullCost) {
              updater.accept(key(entity), (BigDecimal) null);
            }
            updater.accept(key(entity), negativeCost ? -1L : cost(entity));
            previousKeys.put(entity, key(entity));
          }
          if (flushFailure != null) {
            poisoned = true;
            throw flushFailure;
          }
        }
        dirty.clear();
      }

      @Override
      public void beforeVariableChanged(Object entity, String variableName) {
        if (poisoned) {
          callbackCountAfterFailure++;
          throw new IllegalStateException("secondary callback failure must not replace primary");
        }
        callbackCount++;
      }

      @Override
      public void afterVariableChanged(Object entity, String variableName) {
        callbackCount++;
        if (!ignoreUpdates) {
          dirty.add((TestdataEntity) entity);
        }
      }

      @Override
      public void close() {
        closeCount++;
      }
    }
  }

  private static final class ListProvider
      implements GuidedLocalSearchFeatureProvider<TestdataListSolution, String> {
    private int resetCount;
    private int shadowEvents;
    private int listEvents;
    private int assignmentEvents;
    private int unassignmentEvents;

    @Override
    public void extractFeatures(
        TestdataListSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {
      for (var entity : solution.getEntityList()) {
        for (int index = 0; index < entity.getValueList().size(); index++) {
          consumer.accept(
              entity.getValueList().get(index).getCode() + "/" + entity.getCode(), index + 1L);
        }
      }
    }

    @Override
    public GuidedLocalSearchFeatureSession<TestdataListSolution, String> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        private TestdataListSolution solution;
        private final List<String> keys = new ArrayList<>();

        @Override
        public void resetWorkingSolution(TestdataListSolution solution) {
          resetCount++;
          this.solution = solution;
          keys.clear();
          for (var value : solution.getValueList()) {
            assertThat(value.getEntity()).isNotNull();
            assertThat(value.getIndex()).isNotNull();
          }
        }

        @Override
        public void afterVariableChanged(Object entity, String variableName) {
          assertThat(entity).isInstanceOf(TestdataListValue.class);
          shadowEvents++;
        }

        @Override
        public void afterListVariableChanged(Object entity, String name, int from, int to) {
          listEvents++;
        }

        @Override
        public void beforeListVariableElementAssigned(String name, Object element) {
          assignmentEvents++;
        }

        @Override
        public void afterListVariableElementAssigned(String name, Object element) {
          assignmentEvents++;
        }

        @Override
        public void beforeListVariableElementUnassigned(String name, Object element) {
          unassignmentEvents++;
        }

        @Override
        public void afterListVariableElementUnassigned(String name, Object element) {
          unassignmentEvents++;
        }

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {
          keys.forEach(updater::remove);
          keys.clear();
          // This fixture deliberately reads shadows; the independent extractor reads genuine lists.
          for (var value : solution.getValueList()) {
            if (value.getEntity() == null) {
              assertThat(value.getIndex()).isNull();
              continue;
            }
            var key = value.getCode() + "/" + value.getEntity().getCode();
            keys.add(key);
            updater.accept(key, value.getIndex() + 1L);
          }
        }
      };
    }
  }
}
