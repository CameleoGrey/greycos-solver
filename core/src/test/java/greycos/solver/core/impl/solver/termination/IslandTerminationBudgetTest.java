package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withPrecision;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.alns.AlnsPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.custom.scope.CustomPhaseScope;
import greycos.solver.core.impl.phase.custom.scope.CustomStepScope;
import greycos.solver.core.impl.score.definition.HardSoftScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class IslandTerminationBudgetTest {

  @Test
  void elapsedTimeStartsAtEnclosingEntryAndSurvivesInnerTransitions() {
    var fixture = new Fixture(new TerminationConfig().withSpentLimit(Duration.ofMillis(100)));
    fixture.clock.now = 1_030;
    var island = fixture.island();
    var first = island.localSearch(0);
    assertThat(island.termination.calculatePhaseTimeGradient(first))
        .isEqualTo(0.3, withPrecision(0.0));
    fixture.clock.now = 1_070;
    island.termination.phaseEnded(first);
    var second = island.localSearch(1);
    assertThat(island.termination.calculatePhaseTimeGradient(second))
        .isEqualTo(0.7, withPrecision(0.0));
    fixture.clock.now = 1_100;
    assertThat(island.termination.isPhaseTerminated(second)).isTrue();
    assertThat(island.termination.isSolverTerminated(island.scope)).isTrue();
  }

  @Test
  void expiredElapsedLimitIsCheckedBeforeAnyInnerPhaseStarts() {
    var fixture = new Fixture(new TerminationConfig().withSpentLimit(Duration.ZERO));
    var island = fixture.island();
    assertThat(island.termination.isSolverTerminated(island.scope)).isTrue();
    assertThat(island.termination.calculateSolverTimeGradient(island.scope)).isEqualTo(1.0);
  }

  @Test
  void workQuotaIsPerIslandCumulativeAndLifecycleDuplicatesDoNotAdvanceIt() {
    var fixture = new Fixture(new TerminationConfig().withStepCountLimit(3));
    var firstIsland = fixture.island();
    var secondIsland = fixture.island();
    var firstPhase = firstIsland.localSearch(0);
    firstIsland.step(firstPhase, 0);
    firstIsland.step(firstPhase, 1);
    firstIsland.termination.phaseEnded(firstPhase);
    firstIsland.termination.solvingStarted(firstIsland.scope);
    var nextPhase = firstIsland.localSearch(1);
    assertThat(firstIsland.termination.isPhaseTerminated(nextPhase)).isFalse();
    var finalStep = new LocalSearchStepScope<>(nextPhase, 0);
    firstIsland.termination.stepStarted(finalStep);
    firstIsland.termination.stepStarted(finalStep);
    firstIsland.termination.stepEnded(finalStep);
    firstIsland.termination.stepEnded(finalStep);
    assertThat(firstIsland.termination.isPhaseTerminated(nextPhase)).isTrue();
    assertThat(secondIsland.termination.isSolverTerminated(secondIsland.scope)).isFalse();
    var secondPhase = secondIsland.localSearch(0);
    secondIsland.step(secondPhase, 0);
    assertThat(secondIsland.termination.calculatePhaseTimeGradient(secondPhase))
        .isEqualTo(1.0 / 3.0);
  }

  @Test
  void nestedAndOrDoesNotLatchAnExpiredIdleLeafBeforeItsLocalQuota() {
    var fixture =
        new Fixture(
            or(
                and(
                    new TerminationConfig().withStepCountLimit(2),
                    new TerminationConfig().withUnimprovedSpentLimit(Duration.ofMillis(100))),
                new TerminationConfig().withSpentLimit(Duration.ofMillis(1_000))));
    var island = fixture.island();
    var firstPhase = island.localSearch(0);
    island.step(firstPhase, 0);
    fixture.clock.now = 1_110;
    assertThat(island.termination.isPhaseTerminated(firstPhase)).isFalse();
    fixture.improve(1, 1);
    island.step(firstPhase, 1);
    assertThat(island.termination.isPhaseTerminated(firstPhase)).isFalse();
    fixture.clock.now = 1_210;
    assertThat(island.termination.isPhaseTerminated(firstPhase)).isTrue();
    fixture.improve(2, 2);
    assertThat(island.termination.isPhaseTerminated(firstPhase)).isTrue();
  }

  @Test
  void sharedIdleStartsAtFirstSearchPhaseAndOnlyStrictGlobalPublicationsResetIt() {
    var fixture =
        new Fixture(new TerminationConfig().withUnimprovedSpentLimit(Duration.ofMillis(100)));
    var first = fixture.island();
    var second = fixture.island();
    first.termination.phaseStarted(new CustomPhaseScope<>(first.scope, 0));
    second.termination.phaseStarted(new ConstructionHeuristicPhaseScope<>(second.scope, 0));
    fixture.clock.now = 5_000;
    assertThat(first.termination.isSolverTerminated(first.scope)).isFalse();
    var phase = first.localSearch(1);
    // Search initialization, before any step, belongs to the same global idle interval.
    fixture.clock.now = 5_060;
    assertThat(first.termination.calculatePhaseTimeGradient(phase)).isEqualTo(0.6);
    fixture.improve(1, 1);
    fixture.clock.now = 5_120;
    second.localSearch(1);
    second.scope.setInitializedBestScore(SimpleScore.of(1));
    second.termination.bestScoreImproved(new LocalSearchStepScope<>(phase, -1));
    fixture.improve(1, 2); // A tie must not extend the deadline, even if misreported upstream.
    fixture.budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(2)), 5_120, 0);
    assertThat(first.termination.isSolverTerminated(first.scope)).isFalse();
    fixture.clock.now = 5_160;
    assertThat(first.termination.isSolverTerminated(first.scope)).isTrue();
    assertThat(second.termination.isSolverTerminated(second.scope)).isTrue();
  }

  @Test
  void thresholdHistoryMatchesExistingPredicateIncludingHarderLevelImprovements() {
    var clock = new MutableClock(1_000);
    @SuppressWarnings("unchecked")
    var policy = (HeuristicConfigPolicy<TestdataSolution>) mock(HeuristicConfigPolicy.class);
    when(policy.getScoreDefinition()).thenReturn(new HardSoftScoreDefinition());
    var config =
        new TerminationConfig()
            .withUnimprovedSpentLimit(Duration.ofMillis(100))
            .withUnimprovedScoreDifferenceThreshold("0hard/3soft");
    var budget = new IslandTerminationBudget<>(config, policy, clock, 1_000);
    var initial = InnerScore.fullyAssigned(HardSoftScore.of(-2, 0));
    budget.bestScoreImproved(initial, 1_000, 0);
    var scope = new SolverScope<TestdataSolution>(clock);
    scope.setBestScore(initial);
    var bridge = budget.createIslandTermination(scope);
    bridge.solvingStarted(scope);
    bridge.phaseStarted(new LocalSearchPhaseScope<>(scope, 0));

    var expectedScope = new SolverScope<TestdataSolution>(clock);
    expectedScope.setBestScore(initial);
    expectedScope.setBestSolutionTimeMillis(1_000L);
    var expectedPhase = new LocalSearchPhaseScope<>(expectedScope, 0);
    // startingNow only needs the director for its count baseline.
    expectedScope.setScoreDirector(mock(InnerScoreDirector.class));
    expectedPhase.startingNow();
    var expected =
        new UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<TestdataSolution>(
            100, HardSoftScore.of(0, 3), clock);
    expected.solvingStarted(expectedScope);
    expected.phaseStarted(expectedPhase);
    var expectedStep = new LocalSearchStepScope<>(expectedPhase, 0);

    var scores =
        List.of(HardSoftScore.of(-2, 1), HardSoftScore.of(-2, 2), HardSoftScore.of(-1, -100));
    for (int index = 0; index < scores.size(); index++) {
      clock.now = 1_020 + index * 20;
      var score = InnerScore.fullyAssigned(scores.get(index));
      budget.bestScoreImproved(score, clock.now, index + 1);
      expectedScope.setBestScore(score);
      expectedScope.setBestSolutionTimeMillis(clock.now);
      expected.bestScoreImproved(expectedStep);
      assertThat(bridge.calculateSolverTimeGradient(scope))
          .isEqualTo(expected.calculatePhaseTimeGradient(expectedPhase));
    }
    clock.now = 1_130;
    assertThat(bridge.isSolverTerminated(scope))
        .isEqualTo(expected.isPhaseTerminated(expectedPhase));
    assertThat(bridge.isSolverTerminated(scope)).isFalse();
    clock.now = 1_161;
    assertThat(bridge.isSolverTerminated(scope)).isTrue();
  }

  @Test
  void cumulativeLogicalCalculationsAndMovesUseTheOwningIslandCounters() {
    var fixture =
        new Fixture(
            and(
                new TerminationConfig().withScoreCalculationCountLimit(5L),
                new TerminationConfig().withMoveCountLimit(3L)));
    var first = fixture.island();
    var second = fixture.island();
    first.calculations.set(5);
    first.scope.addMoveEvaluationCount(2);
    first.scope.addChildThreadsScoreCalculationCount(10_000);
    assertThat(first.termination.isSolverTerminated(first.scope)).isFalse();
    first.scope.addMoveEvaluationCount(1);
    assertThat(first.termination.isSolverTerminated(first.scope)).isTrue();
    assertThat(second.termination.isSolverTerminated(second.scope)).isFalse();
    second.scope.addChildThreadsScoreCalculationCount(10_000);
    second.scope.addMoveEvaluationCount(3);
    assertThat(second.termination.isSolverTerminated(second.scope)).isFalse();
    second.calculations.set(5);
    assertThat(second.termination.isSolverTerminated(second.scope)).isTrue();
  }

  @Test
  void localUnimprovedStepsSpanSearchPhasesAndExcludeCustomSteps() {
    var fixture = new Fixture(new TerminationConfig().withUnimprovedStepCountLimit(2));
    var island = fixture.island();
    var custom = new CustomPhaseScope<>(island.scope, 0);
    island.termination.phaseStarted(custom);
    for (int index = 0; index < 5; index++) {
      var step = new CustomStepScope<>(custom, index);
      island.termination.stepStarted(step);
      island.termination.stepEnded(step);
    }
    var first = island.localSearch(1);
    island.step(first, 0);
    assertThat(island.termination.isPhaseTerminated(first)).isFalse();
    island.termination.phaseEnded(first);
    var second = island.localSearch(2);
    fixture.improve(5, 1); // Another island's global best does not reset local unimproved steps.
    island.step(second, 0);
    assertThat(island.termination.isPhaseTerminated(second)).isTrue();
  }

  @Test
  void whollyInapplicableAndSubtreesDoNotTerminateBeforeSearch() {
    var searchOnly =
        and(
            new TerminationConfig().withUnimprovedStepCountLimit(1),
            new TerminationConfig().withDiminishedReturns());
    for (var config :
        List.of(searchOnly, or(searchOnly, new TerminationConfig().withStepCountLimit(5)))) {
      var fixture = new Fixture(config);
      var island = fixture.island();
      assertThat(island.termination.isSolverTerminated(island.scope)).isFalse();
      var custom = new CustomPhaseScope<>(island.scope, 0);
      island.termination.phaseStarted(custom);
      assertThat(island.termination.isPhaseTerminated(custom)).isFalse();
      var search = island.localSearch(1);
      assertThat(island.termination.isPhaseTerminated(search)).isFalse();
    }
  }

  @Test
  void globalBestScoreIsVisibleWithoutAdoption() {
    var fixture = new Fixture(new TerminationConfig().withBestScoreLimit("3"));
    var island = fixture.island();
    assertThat(island.termination.isSolverTerminated(island.scope)).isFalse();
    fixture.improve(3, 1);
    assertThat(island.scope.getBestScore().raw()).isEqualTo(SimpleScore.ZERO);
    assertThat(island.termination.isSolverTerminated(island.scope)).isTrue();
  }

  @Test
  void alnsPollingKeepsSequenceBindingsAndRejectsStatefulRepairAttemptGraphs() {
    var fixture =
        new Fixture(
            and(
                new TerminationConfig().withStepCountLimit(2),
                new TerminationConfig().withSpentLimit(Duration.ofMillis(100))));
    var island = fixture.island();
    var prior = island.localSearch(0);
    island.step(prior, 0);
    island.step(prior, 1);
    fixture.clock.now = 1_100;
    var phase = new AlnsPhaseScope<>(island.scope, 1);
    phase.startingNow();
    island.termination.phaseStarted(phase);
    var polling = new AlnsTerminationPolling<>(phase, PhaseTermination.bridge(island.termination));
    assertThat(polling.supportedForRepairAttempts()).isTrue();
    assertThat(polling.checkProbe()).isTrue();

    var stateful = new Fixture(new TerminationConfig().withDiminishedReturns()).island();
    var statefulPhase = new AlnsPhaseScope<>(stateful.scope, 0);
    assertThat(
            new AlnsTerminationPolling<>(statefulPhase, stateful.termination)
                .supportedForRepairAttempts())
        .isFalse();
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
    private final MutableClock clock = new MutableClock(1_000);
    private final IslandTerminationBudget<TestdataSolution> budget;

    @SuppressWarnings("unchecked")
    private Fixture(TerminationConfig config) {
      var policy = (HeuristicConfigPolicy<TestdataSolution>) mock(HeuristicConfigPolicy.class);
      when(policy.getScoreDefinition()).thenReturn(new SimpleScoreDefinition());
      budget = new IslandTerminationBudget<>(config, policy, clock, 1_000);
      budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.ZERO), 1_000, 0);
    }

    private Island island() {
      return new Island(this);
    }

    private void improve(long score, long version) {
      budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(score)), clock.now, version);
    }
  }

  private static final class Island {
    private final SolverScope<TestdataSolution> scope;
    private final AtomicLong calculations = new AtomicLong();
    private final IslandSequenceTermination<TestdataSolution> termination;

    @SuppressWarnings("unchecked")
    private Island(Fixture fixture) {
      scope = new SolverScope<>(fixture.clock);
      var director =
          (InnerScoreDirector<TestdataSolution, SimpleScore>) mock(InnerScoreDirector.class);
      when(director.getCalculationCount()).thenAnswer(ignored -> calculations.get());
      scope.setScoreDirector(director);
      scope.setInitializedBestScore(SimpleScore.ZERO);
      scope.setBestSolutionTimeMillis(fixture.clock.now);
      termination = fixture.budget.createIslandTermination(scope);
      termination.solvingStarted(scope);
    }

    private LocalSearchPhaseScope<TestdataSolution> localSearch(int index) {
      var phase = new LocalSearchPhaseScope<>(scope, index);
      phase.startingNow();
      termination.phaseStarted(phase);
      return phase;
    }

    private void step(LocalSearchPhaseScope<TestdataSolution> phase, int index) {
      var step = new LocalSearchStepScope<>(phase, index);
      termination.stepStarted(step);
      termination.stepEnded(step);
    }
  }

  private static final class MutableClock extends Clock {
    private long now;

    private MutableClock(long now) {
      this.now = now;
    }

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
