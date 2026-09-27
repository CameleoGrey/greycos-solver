package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.termination.DiminishedReturnsTerminationConfig;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.islandmodel.IslandModelPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.custom.scope.CustomPhaseScope;
import greycos.solver.core.impl.phase.custom.scope.CustomStepScope;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class IslandNestedWorkQuotaTest {

  @Test
  void siblingsAndParentConsumeOneRemainderAndRetiredContributionsSurvive() {
    var fixture = new Fixture(new TerminationConfig().withStepCountLimit(5));
    var parent = fixture.island();
    parent.step();
    parent.step();
    var first = parent.child();
    var second = parent.child();
    first.step();
    second.step();
    first.end();
    second.end();
    assertThat(parent.gradient()).isEqualTo(0.8);
    assertThat(parent.terminated()).isFalse();
    parent.search(1);
    parent.step();
    assertThat(parent.terminated()).isTrue();
    assertThat(first.terminated()).isTrue();
    assertThat(second.terminated()).isTrue();
    assertThat(fixture.island().terminated()).isFalse();
  }

  @Test
  void grandchildrenContributeOnceAtEveryInheritedBoundary() {
    var outer = new Fixture(new TerminationConfig().withStepCountLimit(4));
    var parent = outer.island();
    parent.step();
    var child = parent.child();
    child.step();
    var grandchild = child.child();
    grandchild.step();
    assertThat(parent.gradient()).isEqualTo(0.75);
    assertThat(child.gradient()).isEqualTo(0.75);
    grandchild.step();
    assertThat(parent.terminated()).isTrue();
    assertThat(child.terminated()).isTrue();
  }

  @Test
  void nestedPhasesKeepIndependentPerChildLimits() {
    var fixture = new Fixture(new TerminationConfig().withStepCountLimit(7));
    var parent = fixture.island();
    parent.step();
    var first = parent.child();
    var second = parent.child();
    var nested = fixture.budget(new TerminationConfig().withStepCountLimit(2));
    var firstInner = nested.createIslandTermination(first.scope);
    var secondInner = nested.createIslandTermination(second.scope);
    firstInner.solvingStarted(first.scope);
    secondInner.solvingStarted(second.scope);
    firstInner.phaseStarted(first.phase);
    secondInner.phaseStarted(second.phase);
    for (int index = 0; index < 2; index++) {
      var step = first.step();
      firstInner.stepStarted(step);
      firstInner.stepEnded(step);
    }
    assertThat(firstInner.isSolverTerminated(first.scope)).isTrue();
    assertThat(secondInner.isSolverTerminated(second.scope)).isFalse();
    assertThat(parent.terminated()).isFalse();
    var step = second.step();
    secondInner.stepStarted(step);
    secondInner.stepEnded(step);
    assertThat(secondInner.isSolverTerminated(second.scope)).isFalse();
    assertThat(parent.gradient()).isEqualTo(4.0 / 7.0);
  }

  @Test
  void sourceLocalCalculationsAndMovesAggregateWithoutSpeculationOrDoubleCredit() {
    var fixture =
        new Fixture(
            and(
                new TerminationConfig().withScoreCalculationCountLimit(11L),
                new TerminationConfig().withMoveCountLimit(7L)));
    var parent = fixture.island();
    parent.work(2, 3);
    var first = parent.child();
    var second = parent.child();
    first.work(2, 3);
    second.work(2, 4);
    for (var island : List.of(parent, first, second)) {
      island.scope.addChildThreadsScoreCalculationCount(10_000);
      island.termination.publishWork();
      island.termination.publishWork();
    }
    assertThat(parent.terminated()).isFalse();
    assertThat(parent.termination.workProgress().scoreCalculationCount()).isEqualTo(10);
    assertThat(parent.termination.workProgress().moveEvaluationCount()).isEqualTo(6);
    first.work(1, 0);
    first.end();
    assertThat(parent.terminated()).isFalse();
    second.work(0, 1);
    second.end();
    assertThat(parent.terminated()).isTrue();
    assertThat(parent.termination.workProgress().scoreCalculationCount()).isEqualTo(11);
    assertThat(parent.termination.workProgress().moveEvaluationCount()).isEqualTo(7);
  }

  @Test
  void moveWorkersReadQuotaWithoutRegisteringTheirDirector() {
    var fixture = new Fixture(new TerminationConfig().withScoreCalculationCountLimit(4L));
    var parent = fixture.island();
    parent.work(0, 2);
    var worker = parent.child(ChildThreadType.MOVE_THREAD);
    worker.work(10_000, 10_000);
    assertThat(worker.terminated()).isFalse();
    assertThat(parent.gradient()).isEqualTo(0.5);
    parent.work(0, 2); // Only consumed worker calculations are credited by the owner.
    assertThat(worker.terminated()).isTrue();
    assertThat(parent.terminated()).isTrue();
    assertThat(parent.termination.workProgress().scoreCalculationCount()).isEqualTo(4);
  }

  @Test
  void repeatedCopiesOfOneScopeShareOneSourceContribution() {
    var fixture = new Fixture(new TerminationConfig().withStepCountLimit(2));
    var parent = fixture.island();
    var child = parent.child();
    var copy =
        (IslandSequenceTermination<TestdataSolution>)
            parent.termination.createChildThreadTermination(
                child.scope, ChildThreadType.PART_THREAD);
    copy.solvingStarted(child.scope);
    copy.phaseStarted(child.phase);
    var step = child.step();
    copy.stepStarted(step);
    copy.stepEnded(step);
    assertThat(parent.gradient()).isEqualTo(0.5);
    step = child.step();
    copy.stepStarted(step);
    copy.stepEnded(step);
    assertThat(parent.terminated()).isTrue();
  }

  @Test
  void andOrUsesSharedCountersButOnlyLatchesTheWholeExpression() {
    var fixture =
        new Fixture(
            or(
                and(
                    new TerminationConfig().withStepCountLimit(3),
                    new TerminationConfig().withUnimprovedSpentLimit(Duration.ofMillis(100))),
                new TerminationConfig().withMoveCountLimit(10L)));
    var parent = fixture.island();
    parent.step();
    var first = parent.child();
    var second = parent.child();
    first.step();
    fixture.clock.now = 1_100;
    assertThat(first.terminated()).isFalse();
    fixture.improve(1, 1);
    second.step();
    assertThat(first.terminated()).isFalse();
    assertThat(parent.terminated()).isFalse();
    fixture.clock.now = 1_200;
    assertThat(first.terminated()).isTrue();
    assertThat(parent.terminated()).isTrue();
    fixture.improve(2, 2);
    assertThat(first.terminated()).isTrue();
  }

  @Test
  void descendantSearchStartsOriginalIdleClockButContainerDoesNot() {
    var fixture =
        new Fixture(new TerminationConfig().withUnimprovedSpentLimit(Duration.ofMillis(100)));
    var parent = fixture.island(false);
    parent.termination.phaseStarted(new IslandModelPhaseScope<>(parent.scope, 0));
    fixture.clock.now = 4_000;
    assertThat(parent.terminated()).isFalse();
    var child = parent.child();
    fixture.clock.now = 4_099;
    assertThat(child.terminated()).isFalse();
    fixture.clock.now = 4_100;
    assertThat(parent.terminated()).isTrue();
    assertThat(child.terminated()).isTrue();
  }

  @Test
  void inheritedUnimprovedHistorySurvivesChildStartAndEnd() {
    var fixture = new Fixture(new TerminationConfig().withUnimprovedStepCountLimit(3));
    var parent = fixture.island();
    parent.step();
    var first = parent.child();
    first.step();
    first.end();
    var second = parent.child();
    assertThat(second.terminated()).isFalse();
    second.step();
    assertThat(second.terminated()).isTrue();
    assertThat(parent.terminated()).isTrue();
  }

  @Test
  void descendantImprovementResetsSharedUnimprovedHistory() {
    var fixture = new Fixture(new TerminationConfig().withUnimprovedStepCountLimit(2));
    var parent = fixture.island();
    parent.step();
    var first = parent.child();
    first.scope.setInitializedBestScore(SimpleScore.ONE);
    first.step();
    var second = parent.child();
    second.step();
    assertThat(parent.terminated()).isFalse();
    second.step();
    assertThat(parent.terminated()).isTrue();
  }

  @Test
  void diminishedReturnsHistorySurvivesAChildStartBeforeAnyChildStep() {
    var fixture =
        new Fixture(
            new TerminationConfig()
                .withDiminishedReturnsConfig(
                    new DiminishedReturnsTerminationConfig().withSlidingWindowMilliseconds(0L)));
    var parent = fixture.island();
    parent.step();
    var child = parent.child();
    // A fresh diminished-return history would not be started until this child's first step.
    assertThat(child.terminated()).isTrue();
    assertThat(parent.terminated()).isTrue();
  }

  @Test
  void customStepsCountAsWorkButDoNotAdvanceSearchHistory() {
    var fixture =
        new Fixture(
            and(
                new TerminationConfig().withStepCountLimit(4),
                new TerminationConfig().withUnimprovedStepCountLimit(2)));
    var parent = fixture.island();
    parent.step();
    var child = parent.child();
    var custom = new CustomPhaseScope<>(child.scope, 1);
    child.termination.phaseStarted(custom);
    for (int index = 0; index < 3; index++) {
      var step = new CustomStepScope<>(custom, index);
      child.termination.stepStarted(step);
      child.termination.stepEnded(step);
    }
    assertThat(parent.terminated()).isFalse();
    parent.step();
    assertThat(parent.terminated()).isTrue();
  }

  @Test
  void finalPublicationAndPollsAfterLatchRetainInFlightWork() {
    var fixture = new Fixture(new TerminationConfig().withScoreCalculationCountLimit(2L));
    var parent = fixture.island();
    var child = parent.child();
    child.work(0, 2);
    assertThat(child.terminated()).isTrue();
    child.calculations.addAndGet(3);
    child.end();
    assertThat(parent.terminated()).isTrue();
    assertThat(parent.termination.workProgress().scoreCalculationCount()).isEqualTo(5);
    child.calculations.incrementAndGet();
    assertThat(child.terminated()).isTrue();
    assertThat(parent.terminated()).isTrue();
    assertThat(parent.termination.workProgress().scoreCalculationCount()).isEqualTo(6);
  }

  @Test
  void concurrentMembersCreditEachCumulativeCounterOnce() throws Exception {
    var fixture = new Fixture(new TerminationConfig().withStepCountLimit(201));
    var parent = fixture.island();
    parent.step();
    var first = parent.child();
    var second = parent.child();
    var ready = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures =
          List.of(first, second).stream()
              .map(
                  child ->
                      executor.submit(
                          () -> {
                            ready.await(10, TimeUnit.SECONDS);
                            for (int step = 0; step < 100; step++) {
                              child.work(2, 3);
                              child.step();
                              child.termination.publishWork();
                            }
                            child.end();
                            return null;
                          }))
              .toList();
      for (var future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    }
    assertThat(parent.terminated()).isTrue();
    assertThat(parent.termination.workProgress().completedSteps()).isEqualTo(201);
    assertThat(parent.termination.workProgress().moveEvaluationCount()).isEqualTo(400);
    assertThat(parent.termination.workProgress().scoreCalculationCount()).isEqualTo(600);
  }

  @Test
  void partitionBindingPublishesItsOwnDirectorWithoutReadingTheSourceDirector() {
    var fixture = new Fixture(new TerminationConfig().withScoreCalculationCountLimit(5L));
    var parent = fixture.island();
    parent.work(1, 2);
    clearInvocations(parent.scope.getScoreDirector());
    var partition = parent.partitionChild();
    verifyNoInteractions(parent.scope.getScoreDirector());
    partition.work(4, 3);
    partition.scope.addChildThreadsScoreCalculationCount(10_000);
    assertThat(parent.terminated()).isTrue();
    assertThat(parent.termination.workProgress().scoreCalculationCount()).isEqualTo(5);
    assertThat(parent.termination.workProgress().moveEvaluationCount()).isEqualTo(5);
  }

  @Test
  void partitionScoresCannotResetTheOwnersUnimprovedHistory() {
    var fixture = new Fixture(new TerminationConfig().withUnimprovedStepCountLimit(3), -100);
    var parent = fixture.island();
    parent.step();
    parent.step();
    var partition = parent.partitionChild();
    partition.scope.setInitializedBestScore(SimpleScore.of(-20));
    partition.search(1);
    partition.termination.bestScoreImproved(new LocalSearchStepScope<>(partition.phase, -1));
    var descendant = partition.child();
    descendant.scope.setInitializedBestScore(SimpleScore.of(-10));
    descendant.search(1);
    descendant.step();
    assertThat(parent.terminated()).isTrue();
    assertThat(partition.terminated()).isTrue();
    assertThat(descendant.terminated()).isTrue();
    assertThat(parent.termination.workProgress().completedSteps()).isEqualTo(3);
  }

  @Test
  void partitionScoresCannotStartAFalseDiminishedReturnsImprovement() {
    var fixture =
        new Fixture(
            new TerminationConfig()
                .withDiminishedReturnsConfig(
                    new DiminishedReturnsTerminationConfig().withSlidingWindowMilliseconds(0L)),
            -100);
    var parent = fixture.island();
    parent.step();
    var partition = parent.partitionChild();
    partition.scope.setInitializedBestScore(SimpleScore.of(-20));
    partition.search(1);
    assertThat(partition.terminated()).isTrue();
    assertThat(parent.terminated()).isTrue();
  }

  @Test
  void mergedFullProblemImprovementStillResetsHistoryAfterPartitionScores() {
    var fixture = new Fixture(new TerminationConfig().withUnimprovedStepCountLimit(3), -100);
    var parent = fixture.island();
    parent.step();
    parent.step();
    var partition = parent.partitionChild();
    partition.scope.setInitializedBestScore(SimpleScore.of(-20));
    partition.search(1);
    parent.scope.setInitializedBestScore(SimpleScore.of(-90));
    parent.step();
    parent.step();
    parent.step();
    assertThat(parent.terminated()).isFalse();
    parent.step();
    assertThat(parent.terminated()).isTrue();
  }

  @Test
  void independentSolvesStartWithFreshQuotasAndSearchHistory() {
    var fixture = new Fixture(new TerminationConfig().withUnimprovedStepCountLimit(2));
    for (int run = 0; run < 2; run++) {
      var parent = fixture.island();
      assertThat(parent.terminated()).isFalse();
      parent.step();
      var child = parent.child();
      child.step();
      assertThat(parent.terminated()).isTrue();
      child.end();
      parent.end();
    }
  }

  private static TerminationConfig and(TerminationConfig... children) {
    return new TerminationConfig()
        .withTerminationCompositionStyle(TerminationCompositionStyle.AND)
        .withTerminationConfigList(List.of(children));
  }

  private static TerminationConfig or(TerminationConfig... children) {
    return new TerminationConfig().withTerminationConfigList(List.of(children));
  }

  private static final class Fixture {
    private final MutableClock clock = new MutableClock();
    private final IslandTerminationBudget<TestdataSolution> budget;
    private final long initialScore;

    private Fixture(TerminationConfig config) {
      this(config, 0);
    }

    private Fixture(TerminationConfig config, long initialScore) {
      this.initialScore = initialScore;
      budget = budget(config);
    }

    @SuppressWarnings("unchecked")
    private IslandTerminationBudget<TestdataSolution> budget(TerminationConfig config) {
      var policy = (HeuristicConfigPolicy<TestdataSolution>) mock(HeuristicConfigPolicy.class);
      when(policy.getScoreDefinition()).thenReturn(new SimpleScoreDefinition());
      var result = new IslandTerminationBudget<>(config, policy, clock, 1_000);
      result.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(initialScore)), 1_000, 0);
      return result;
    }

    private Island island() {
      return island(true);
    }

    private Island island(boolean startSearch) {
      return new Island(this, null, ChildThreadType.PART_THREAD, startSearch);
    }

    private void improve(long score, long version) {
      budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(score)), clock.now, version);
    }
  }

  private static final class Island {
    private final Fixture fixture;
    private final SolverScope<TestdataSolution> scope;
    private final AtomicLong calculations = new AtomicLong();
    private final IslandSequenceTermination<TestdataSolution> termination;
    private LocalSearchPhaseScope<TestdataSolution> phase;
    private int stepIndex;

    private Island(Fixture fixture, Island parent, ChildThreadType type, boolean startSearch) {
      this(fixture, parent, type, startSearch, false);
    }

    @SuppressWarnings("unchecked")
    private Island(
        Fixture fixture,
        Island parent,
        ChildThreadType type,
        boolean startSearch,
        boolean partition) {
      this.fixture = fixture;
      scope = new SolverScope<>(fixture.clock);
      var director =
          (InnerScoreDirector<TestdataSolution, SimpleScore>) mock(InnerScoreDirector.class);
      when(director.getCalculationCount()).thenAnswer(ignored -> calculations.get());
      scope.setScoreDirector(director);
      scope.setInitializedBestScore(SimpleScore.of(fixture.initialScore));
      scope.setBestSolutionTimeMillis(fixture.clock.now);
      termination =
          parent == null
              ? fixture.budget.createIslandTermination(scope)
              : partition
                  ? parent.termination.createPartitionChildTermination(scope)
                  : (IslandSequenceTermination<TestdataSolution>)
                      parent.termination.createChildThreadTermination(scope, type);
      termination.solvingStarted(scope);
      if (startSearch) {
        search(0);
      }
    }

    private Island child() {
      return child(ChildThreadType.PART_THREAD);
    }

    private Island partitionChild() {
      return new Island(fixture, this, ChildThreadType.PART_THREAD, true, true);
    }

    private Island child(ChildThreadType type) {
      return new Island(fixture, this, type, true);
    }

    private void search(int index) {
      phase = new LocalSearchPhaseScope<>(scope, index);
      phase.startingNow();
      termination.phaseStarted(phase);
      stepIndex = 0;
    }

    private LocalSearchStepScope<TestdataSolution> step() {
      var step = new LocalSearchStepScope<>(phase, stepIndex++);
      termination.stepStarted(step);
      termination.stepEnded(step);
      return step;
    }

    private void work(long moves, long newCalculations) {
      scope.addMoveEvaluationCount(moves);
      calculations.addAndGet(newCalculations);
      termination.publishWork();
    }

    private boolean terminated() {
      return termination.isSolverTerminated(scope);
    }

    private double gradient() {
      return termination.calculateSolverTimeGradient(scope);
    }

    private void end() {
      termination.phaseEnded(phase);
      termination.solvingEnded(scope);
    }
  }

  private static final class MutableClock extends Clock {
    private long now = 1_000;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(now);
    }

    @Override
    public long millis() {
      return now;
    }
  }
}
