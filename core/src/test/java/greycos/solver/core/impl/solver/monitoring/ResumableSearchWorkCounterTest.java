package greycos.solver.core.impl.solver.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class ResumableSearchWorkCounterTest {

  @Test
  void physicalCallsAreIndependentOfImportedLogicalCountsAndResets() {
    var calls = new AtomicInteger();
    var factory = countingFactory(calls);
    var counter = new ResumableSearchWorkCounter();
    try (var director = factory.buildScoreDirector()) {
      director.setResumableSearchWorkCounter(counter);
      director.setWorkingSolution(TestdataSolution.generateSolution());
      director.calculateScore();
      try (var ignored = counter.scope(ResumableSearchWorkCategory.SEARCH)) {
        director.calculateScore();
        // Prepared evaluations import logical counts; this is not another physical call.
        director.incrementCalculationCount(7L);
      }
      assertThat(director.getCalculationCount()).isEqualTo(9L);
      director.resetCalculationCount();
      assertThat(director.getCalculationCount()).isZero();
      assertThat(counter.snapshot())
          .isEqualTo(
              new ResumableSearchWorkSnapshot(
                  2L,
                  Map.of(
                      ResumableSearchWorkCategory.INITIALIZATION, 1L,
                      ResumableSearchWorkCategory.SEARCH, 1L)));
    }
    assertThat(counter.snapshot().totalScoreCalculationCount()).isEqualTo(calls.get());
    assertThat(counter.snapshot().totalScoreCalculationCount()).isEqualTo(2L);
  }

  @Test
  void temporaryAndAssertionDirectorsShareTheSessionCounter() {
    var calls = new AtomicInteger();
    var factory = countingFactory(calls);
    var counter = new ResumableSearchWorkCounter();
    try (var director = factory.buildScoreDirector()) {
      director.setResumableSearchWorkCounter(counter);
      director.setWorkingSolution(TestdataSolution.generateSolution());
      var initialScore = director.calculateScore();
      try (var ignored = counter.scope(ResumableSearchWorkCategory.SEARCH)) {
        try (var child = director.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD)) {
          assertThat(child.getResumableSearchWorkCounter()).isSameAs(counter);
          child.calculateScore();
          child.assertWorkingScoreFromScratch(initialScore, "child probe");
        }
        try (var child = director.createChildThreadScoreDirector(ChildThreadType.PART_THREAD)) {
          assertThat(child.getResumableSearchWorkCounter()).isSameAs(counter);
          child.setWorkingSolution(director.cloneWorkingSolution());
          child.calculateScore();
        }
        director.assertScoreFromScratch(director.getWorkingSolution());
        director.assertExpectedWorkingScore(initialScore, "step");
        director.assertShadowVariablesAreNotStale(initialScore, "step");
        director.calculateScore();
      }
      // Only calculations on this director affect its ordinary count.
      assertThat(director.getCalculationCount()).isEqualTo(4L);
    }
    assertThat(counter.snapshot().totalScoreCalculationCount()).isEqualTo(calls.get());
    assertThat(counter.snapshot())
        .isEqualTo(
            new ResumableSearchWorkSnapshot(
                8L,
                Map.of(
                    ResumableSearchWorkCategory.INITIALIZATION, 1L,
                    ResumableSearchWorkCategory.SEARCH, 3L,
                    ResumableSearchWorkCategory.ASSERTION, 4L)));
  }

  @Test
  void separateAssertionFactoryInheritsTheCounter() {
    var mainCalls = new AtomicInteger();
    var assertionCalls = new AtomicInteger();
    var factory = countingFactory(mainCalls);
    factory.setAssertionScoreDirectorFactory(countingFactory(assertionCalls));
    var counter = new ResumableSearchWorkCounter();
    try (var director = factory.buildScoreDirector()) {
      director.setResumableSearchWorkCounter(counter);
      director.setWorkingSolution(TestdataSolution.generateSolution());
      var score = director.calculateScore();
      director.assertWorkingScoreFromScratch(score, "working score");
      director.assertPredictedScoreFromScratch(score, "predicted score");
      assertThat(director.getCalculationCount()).isEqualTo(1L);
    }
    assertThat(mainCalls.get()).isEqualTo(1);
    assertThat(assertionCalls.get()).isEqualTo(2);
    assertThat(counter.snapshot().scoreCalculationCount(ResumableSearchWorkCategory.ASSERTION))
        .isEqualTo(2L);
    assertThat(counter.snapshot().totalScoreCalculationCount()).isEqualTo(3L);
  }

  @Test
  void failedPhysicalAttemptIsCountedAndAssertionPurposeIsRestored() {
    var calls = new AtomicInteger();
    var factory =
        new EasyScoreDirectorFactory<TestdataSolution, SimpleScore>(
            TestdataSolution.buildSolutionDescriptor(),
            solution -> {
              if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("calculator failed");
              }
              return SimpleScore.ZERO;
            },
            EnvironmentMode.NO_ASSERT);
    var counter = new ResumableSearchWorkCounter();
    try (var director = factory.buildScoreDirector();
        var ignored = counter.scope(ResumableSearchWorkCategory.SEARCH)) {
      director.setResumableSearchWorkCounter(counter);
      director.setWorkingSolution(TestdataSolution.generateSolution());
      assertThatThrownBy(
              () ->
                  director.assertWorkingScoreFromScratch(
                      InnerScore.fullyAssigned(SimpleScore.ZERO), "failed assertion"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("calculator failed");
      director.calculateScore();
      assertThat(director.getCalculationCount()).isEqualTo(1L);
    }
    assertThat(counter.snapshot())
        .isEqualTo(
            new ResumableSearchWorkSnapshot(
                2L,
                Map.of(
                    ResumableSearchWorkCategory.ASSERTION, 1L,
                    ResumableSearchWorkCategory.SEARCH, 1L)));
  }

  @Test
  void sessionCountersAndPublishedSnapshotsRemainIndependent() {
    var factory = countingFactory(new AtomicInteger());
    var firstCounter = new ResumableSearchWorkCounter();
    var secondCounter = new ResumableSearchWorkCounter();
    try (var first = factory.buildScoreDirector();
        var second = factory.buildScoreDirector()) {
      first.setResumableSearchWorkCounter(firstCounter);
      second.setResumableSearchWorkCounter(secondCounter);
      first.setWorkingSolution(TestdataSolution.generateSolution());
      second.setWorkingSolution(TestdataSolution.generateSolution());
      first.calculateScore();
      var published = firstCounter.snapshot();
      for (var category : ResumableSearchWorkCategory.values()) {
        try (var ignored = firstCounter.scope(category)) {
          first.calculateScore();
        }
      }
      second.calculateScore();
      assertThat(published.totalScoreCalculationCount()).isEqualTo(1L);
      assertThat(firstCounter.snapshot().totalScoreCalculationCount()).isEqualTo(7L);
      assertThat(secondCounter.snapshot().totalScoreCalculationCount()).isEqualTo(1L);
      assertThat(
              firstCounter.snapshot().plus(secondCounter.snapshot()).totalScoreCalculationCount())
          .isEqualTo(8L);
    }
  }

  @Test
  void snapshotDefensivelyCopiesAndValidatesCategoryTotals() {
    var counts = new HashMap<>(Map.of(ResumableSearchWorkCategory.SEARCH, 3L));
    var snapshot = new ResumableSearchWorkSnapshot(3L, counts);
    counts.put(ResumableSearchWorkCategory.SEARCH, 99L);
    assertThat(snapshot.scoreCalculationCount(ResumableSearchWorkCategory.SEARCH)).isEqualTo(3L);
    assertThatThrownBy(
            () ->
                snapshot
                    .scoreCalculationCountsByCategory()
                    .put(ResumableSearchWorkCategory.SEARCH, 7L))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new ResumableSearchWorkSnapshot(4L, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("category total");
    assertThatThrownBy(
            () ->
                new ResumableSearchWorkSnapshot(
                    -1L, Map.of(ResumableSearchWorkCategory.SEARCH, -1L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("negative");
  }

  private static EasyScoreDirectorFactory<TestdataSolution, SimpleScore> countingFactory(
      AtomicInteger calls) {
    return new EasyScoreDirectorFactory<>(
        TestdataSolution.buildSolutionDescriptor(),
        solution -> {
          calls.incrementAndGet();
          return SimpleScore.ZERO;
        },
        EnvironmentMode.NO_ASSERT);
  }
}
