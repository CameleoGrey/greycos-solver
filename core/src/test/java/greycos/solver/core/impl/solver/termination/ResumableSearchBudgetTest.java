package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.score.definition.HardSoftScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.ResumableSearchBudget.Metric;
import greycos.solver.core.impl.solver.termination.ResumableSearchBudget.PersistentState;
import greycos.solver.core.impl.solver.termination.ResumableSearchBudget.Work;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResumableSearchBudgetTest {

  @Test
  void nestedCompositionUsesTheExactFrozenStepFrontier() {
    var fixture = new Fixture();
    var budget =
        fixture.budget(
            and(
                or(steps(5), new TerminationConfig().withMoveCountLimit(10L)),
                or(steps(8), new TerminationConfig().withScoreCalculationCountLimit(20L))));
    assertThat(budget.remainingStepCount()).isEqualTo(8);
    budget.recordDelta(3, 10, 10);
    assertThat(budget.remainingStepCount()).isEqualTo(5);
    budget.recordDelta(0, 0, 10);
    assertThat(budget.isTerminated()).isTrue();
    assertThat(budget.consumption().completedSteps()).isEqualTo(3);
  }

  @Test
  void andDoesNotStopAtAnExpiredStepBranchWhileOtherBranchRemainsFalse() {
    var budget =
        new Fixture().budget(and(steps(5), new TerminationConfig().withMoveCountLimit(10L)));
    assertThat(budget.remainingStepCount()).isEqualTo(ResumableSearchBudget.UNBOUNDED);
    budget.recordDelta(5, 8, 0);
    assertThat(budget.isTerminated()).isFalse();
    assertThat(budget.remainingStepCount()).isEqualTo(ResumableSearchBudget.UNBOUNDED);
    budget.recordDelta(2, 2, 0);
    assertThat(budget.isTerminated()).isTrue();
  }

  @Test
  void trueNonStepBranchLeavesOnlyUnsatisfiedStepAllowanceInAnd() {
    var budget =
        new Fixture().budget(and(steps(5), new TerminationConfig().withMoveCountLimit(0L)));
    assertThat(budget.remainingStepCount()).isEqualTo(5);
    budget.recordDelta(4, 8, 9);
    assertThat(budget.remainingStepCount()).isEqualTo(1);
    budget.recordDelta(1, 2, 3);
    assertThat(budget.remainingStepCount()).isZero();
  }

  @ParameterizedTest
  @CsvSource({"AND, 11", "OR, 5"})
  void multipleStepLeavesUseCompositionRatherThanTheSmallestLimit(
      TerminationCompositionStyle composition, long expected) {
    var budget =
        new Fixture()
            .budget(
                new TerminationConfig()
                    .withTerminationCompositionStyle(composition)
                    .withTerminationConfigList(List.of(steps(7), steps(13))));
    budget.recordDelta(2, 0, 0);
    assertThat(budget.remainingStepCount()).isEqualTo(expected);
  }

  @Test
  void emptyTreesAreUnboundedIncludingEmptyAnd() {
    for (var config : List.of(new TerminationConfig(), and(), or())) {
      var budget = new Fixture().budget(config);
      assertThat(budget.isTerminated()).isFalse();
      assertThat(budget.remainingStepCount()).isEqualTo(ResumableSearchBudget.UNBOUNDED);
      assertThat(budget.report().limits()).isEmpty();
    }
  }

  @Test
  void moveAndScoreLimitsAllowAdmittedWorkAndReportItsOvershoot() {
    var budget =
        new Fixture()
            .budget(
                and(
                    new TerminationConfig().withMoveCountLimit(10L),
                    new TerminationConfig().withScoreCalculationCountLimit(20L)));
    budget.recordDelta(2, 7, 12);
    assertThat(budget.remainingStepCount()).isEqualTo(ResumableSearchBudget.UNBOUNDED);
    // Both source summaries were admitted from the same committed boundary.
    budget.recordSource("session-0/episode-0", 1, 4, 7);
    budget.recordSource("session-1/episode-0", 1, 5, 8);
    var report = budget.report();
    assertThat(report.terminated()).isTrue();
    assertThat(report.consumed()).isEqualTo(new Work(4, 16, 27));
    assertThat(report.limits())
        .extracting(ResumableSearchBudget.Limit::metric)
        .containsExactly(Metric.MOVE_EVALUATIONS, Metric.SCORE_CALCULATIONS);
    assertThat(report.limits())
        .extracting(ResumableSearchBudget.Limit::limit)
        .containsExactly(10L, 20L);
    assertThat(report.limits())
        .extracting(ResumableSearchBudget.Limit::consumed)
        .containsExactly(16L, 27L);
    assertThat(report.limits())
        .extracting(ResumableSearchBudget.Limit::overshoot)
        .containsExactly(6L, 7L);
  }

  @Test
  void cumulativeSourcesDeduplicateAndRetiredEpisodesKeepAllWork() {
    var budget = new Fixture().budget(steps(20));
    assertThat(budget.recordSource("session-0/episode-0", 4, 40, 44))
        .isEqualTo(new Work(4, 40, 44));
    assertThat(budget.recordSource("session-0/episode-0", 4, 40, 44)).isEqualTo(Work.ZERO);
    assertThat(budget.recordSource("session-0/episode-0", 7, 70, 74))
        .isEqualTo(new Work(3, 30, 30));
    budget.recordSource("session-0/episode-1", 2, 20, 26);
    assertThat(budget.consumption()).isEqualTo(new Work(9, 90, 100));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> budget.recordSource("session-0/episode-0", 1, 1, 1))
        .withMessageContaining("new source key");
    assertThat(budget.consumption()).isEqualTo(new Work(9, 90, 100));
    budget.retireSource("session-0/episode-0");
    budget.retireSource("session-0/episode-1");
    assertThat(budget.consumption()).isEqualTo(new Work(9, 90, 100));
    budget.recordSource("session-0/episode-2", 1, 5, 8);
    assertThat(budget.consumption()).isEqualTo(new Work(10, 95, 108));
  }

  @Test
  void checkedCounterOverflowDoesNotPartiallyCommitASource() {
    var budget = new Fixture().budget(steps(20));
    budget.recordDelta(0, Long.MAX_VALUE - 1, 0);
    assertThatThrownBy(() -> budget.recordSource("first", 1, 2, 3))
        .isInstanceOf(ArithmeticException.class);
    assertThat(budget.consumption()).isEqualTo(new Work(0, Long.MAX_VALUE - 1, 0));
    assertThat(budget.recordSource("first", 1, 1, 3)).isEqualTo(new Work(1, 1, 3));
  }

  @Test
  void persistentScopesSurviveReconstructionAndModelChanges() {
    var fixture = new Fixture();
    var config = and(steps(8), new TerminationConfig().withSpentLimit(Duration.ofMillis(100)));
    var original = fixture.budget(config);
    original.rebaseArchive(InnerScore.fullyAssigned(SimpleScore.of(5)));
    original.recordSource("model-0/episode-0", 5, 40, 50);
    fixture.clock.now = 1_050;
    fixture.state.modelChanged(1);
    var rebuilt = fixture.budget(config);
    rebuilt.rebaseArchive(InnerScore.fullyAssigned(SimpleScore.of(-10)));
    rebuilt.recordSource("model-1/episode-0", 3, 20, 25);
    assertThat(rebuilt.isTerminated()).isFalse();
    fixture.clock.now = 1_100;
    assertThat(rebuilt.isTerminated()).isTrue();
    assertThat(rebuilt.consumption()).isEqualTo(new Work(8, 60, 75));
    assertThat(original.consumption()).isEqualTo(rebuilt.consumption());
    assertThat(fixture.state.modelRevision()).isEqualTo(1);
    assertThatIllegalArgumentException().isThrownBy(() -> fixture.state.modelChanged(0));
  }

  @Test
  void globalUnimprovedStepsFollowStrictArchiveImprovementsInCommitOrder() {
    var budget = new Fixture().budget(new TerminationConfig().withUnimprovedStepCountLimit(4));
    budget.rebaseArchive(InnerScore.fullyAssigned(SimpleScore.of(-10)));
    budget.recordDelta(3, 10, 12);
    assertThat(budget.remainingStepCount()).isEqualTo(1);
    assertThat(budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(-9)), 1_001))
        .isTrue();
    assertThat(budget.remainingStepCount()).isEqualTo(4);
    budget.recordDelta(2, 10, 12);
    assertThat(budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(-9)), 1_002))
        .isFalse();
    assertThat(budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(-10)), 1_003))
        .isFalse();
    assertThat(budget.remainingStepCount()).isEqualTo(2);
    budget.recordDelta(2, 10, 12);
    assertThat(budget.isTerminated()).isTrue();
  }

  @Test
  void revisionRebaseDoesNotRestartUnimprovedBudgets() {
    var fixture = new Fixture();
    var budget =
        fixture.budget(
            and(
                new TerminationConfig().withUnimprovedStepCountLimit(4),
                new TerminationConfig().withUnimprovedSpentLimit(Duration.ofMillis(50))));
    budget.rebaseArchive(InnerScore.fullyAssigned(SimpleScore.of(1)));
    budget.recordDelta(2, 5, 8);
    fixture.clock.now = 1_025;
    fixture.state.modelChanged(1);
    budget.rebaseArchive(InnerScore.fullyAssigned(SimpleScore.of(-100)));
    budget.recordDelta(2, 5, 8);
    fixture.clock.now = 1_050;
    assertThat(budget.isTerminated()).isTrue();
  }

  @Test
  void archivePredicatesRequireInitializedScoresAndRevisionsInvalidateThem() {
    var fixture = new Fixture();
    var budget = fixture.budget(new TerminationConfig().withBestScoreLimit("5"));
    budget.bestScoreImproved(InnerScore.withUnassignedCount(SimpleScore.of(100), 1), 1_001);
    assertThat(budget.isTerminated()).isFalse();
    budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(5)), 1_002);
    assertThat(budget.isTerminated()).isTrue();
    fixture.state.modelChanged(1);
    assertThat(budget.isTerminated()).isFalse();
    budget.rebaseArchive(InnerScore.fullyAssigned(SimpleScore.of(1)));
    assertThat(budget.isTerminated()).isFalse();
  }

  @Test
  void feasibilityUsesTheCommittedNativeArchive() {
    var fixture = new Fixture();
    when(fixture.policy.getScoreDefinition()).thenReturn(new HardSoftScoreDefinition());
    var budget = fixture.budget(new TerminationConfig().withBestScoreFeasible(true));
    budget.rebaseArchive(InnerScore.withUnassignedCount(HardSoftScore.ZERO, 1));
    assertThat(budget.isTerminated()).isFalse();
    budget.bestScoreImproved(InnerScore.fullyAssigned(HardSoftScore.of(-1, 100)), 1_001);
    assertThat(budget.isTerminated()).isFalse();
    budget.bestScoreImproved(InnerScore.fullyAssigned(HardSoftScore.of(0, -100)), 1_002);
    assertThat(budget.isTerminated()).isTrue();
  }

  @Test
  void parentNativeScorePredicatesDoNotReplaceChildIdleHistory() {
    var fixture = new Fixture();
    var budget =
        fixture.budget(
            and(
                new TerminationConfig().withBestScoreLimit("5"),
                new TerminationConfig().withUnimprovedStepCountLimit(2)));
    budget.rebaseArchive(InnerScore.fullyAssigned(SimpleScore.of(100)));
    budget.setPredicateArchive(InnerScore.fullyAssigned(SimpleScore.of(1)));
    budget.recordDelta(2, 2, 2);
    assertThat(budget.isTerminated()).isFalse();
    budget.setPredicateArchive(InnerScore.fullyAssigned(SimpleScore.of(5)));
    assertThat(budget.isTerminated()).isTrue();
    budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(101)), 1_001);
    assertThat(budget.remainingStepCount()).isEqualTo(2);
    // This parent update cannot reset idle progress or serve as a child-local improvement.
    budget.setPredicateArchive(InnerScore.fullyAssigned(SimpleScore.of(1_000)));
    budget.recordDelta(2, 2, 2);
    assertThat(budget.isTerminated()).isTrue();
    fixture.state.modelChanged(1);
    budget.rebaseArchive(InnerScore.fullyAssigned(SimpleScore.of(200)));
    assertThat(budget.isTerminated()).isFalse();
  }

  @Test
  void legacyIslandIdleTimeCanBeSharedWithoutChangingMemberIdleSteps() {
    var fixture = new Fixture();
    var budget =
        fixture.budget(
            and(
                new TerminationConfig().withUnimprovedStepCountLimit(2),
                new TerminationConfig().withUnimprovedSpentLimit(Duration.ofMillis(50))));
    budget.rebaseArchive(InnerScore.fullyAssigned(SimpleScore.ZERO));
    budget.recordDelta(2, 2, 2);
    fixture.clock.now = 1_040;
    budget.setUnimprovedTimeOrigin(1_040);
    fixture.clock.now = 1_080;
    assertThat(budget.isTerminated()).isFalse();
    fixture.clock.now = 1_090;
    assertThat(budget.isTerminated()).isTrue();
    budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(1)), 1_090);
    assertThat(budget.remainingStepCount()).isEqualTo(2);
    budget.recordDelta(2, 2, 2);
    assertThat(budget.isTerminated()).isTrue();
  }

  @Test
  void legacyIdleConditionsRemainFalseBeforeSearchEvenForZeroLimits() {
    for (var idle :
        List.of(
            new TerminationConfig().withUnimprovedStepCountLimit(0),
            new TerminationConfig().withUnimprovedSpentLimit(Duration.ZERO))) {
      var budget = new Fixture().budget(and(idle, steps(0)));
      budget.setIdleApplicable(false);
      assertThat(budget.remainingStepCount()).isEqualTo(ResumableSearchBudget.UNBOUNDED);
      budget.setIdleApplicable(true);
      assertThat(budget.isTerminated()).isTrue();
    }
  }

  @Test
  void legacyUnimprovedStepsExcludeConstructionButRetainSearchAcrossEpisodes() {
    var fixture = new Fixture();
    var budget = fixture.budget(new TerminationConfig().withUnimprovedStepCountLimit(3));
    budget.rebaseArchive(InnerScore.fullyAssigned(SimpleScore.ZERO));
    budget.recordSearchStepSource("episode-0", 0);
    budget.recordSource("episode-0", 100, 100, 100);
    assertThat(budget.remainingStepCount()).isEqualTo(3);
    budget.recordSource("episode-0", 102, 102, 102);
    assertThat(budget.recordSearchStepSource("episode-0", 2)).isEqualTo(2);
    assertThat(budget.recordSearchStepSource("episode-0", 2)).isZero();
    assertThat(budget.remainingStepCount()).isEqualTo(1);
    budget.retireSource("episode-0");
    budget.recordSearchStepSource("episode-1", 1);
    budget.recordSource("episode-1", 1, 1, 1);
    assertThat(budget.isTerminated()).isTrue();
    assertThat(budget.consumption().completedSteps()).isEqualTo(103);
    budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(1)), 1_001);
    assertThat(budget.remainingStepCount()).isEqualTo(3);
    fixture.state.modelChanged(1);
    budget.rebaseArchive(InnerScore.fullyAssigned(SimpleScore.of(-100)));
    budget.recordSearchStepSource("model-1/episode-0", 3);
    assertThat(budget.isTerminated()).isTrue();
  }

  @Test
  void solverAndPhaseKeepTheirOwnTimeOriginsAndWorkCounters() {
    var fixture = new Fixture();
    var config = new TerminationConfig().withSpentLimit(Duration.ofMillis(100));
    var solver = fixture.solverBudget(config, 900);
    var phase = fixture.budget(config);
    assertThat(solver.isTerminated()).isTrue();
    assertThat(phase.isTerminated()).isFalse();
    assertThat(solver.consumption()).isEqualTo(new Work(0, 7, 11));
    assertThat(phase.consumption()).isEqualTo(Work.ZERO);
    assertThat(solver.report().origin()).isEqualTo(ResumableSearchBudget.Origin.SOLVER);
    assertThat(phase.report().origin()).isEqualTo(ResumableSearchBudget.Origin.PHASE);
    fixture.solverBudget(config, 900);
    assertThat(solver.consumption()).isEqualTo(new Work(0, 7, 11));
    fixture.state.modelChanged(1);
    fixture.solverBudget(config, 1_000);
    assertThat(solver.consumption()).isEqualTo(new Work(0, 14, 22));
    assertThat(solver.isTerminated()).isTrue();
  }

  @Test
  void inheritedStepLimitBelongsToEachPhaseWhileOtherWorkStaysSolverWide() {
    var fixture = new Fixture();
    // Ordinary construction has already consumed seven moves and eleven scores, but does not
    // spend either following local-search phase's inherited completed-step allowance.
    var config = and(steps(3), new TerminationConfig().withMoveCountLimit(10L));
    var first = fixture.solverBudget(config, 900, "phase-1");
    first.recordSource("phase-1/model-0", 3, 3, 4);
    assertThat(first.isTerminated()).isTrue();
    var second = fixture.solverBudget(config, 900, "phase-2");
    assertThat(second.remainingStepCount()).isEqualTo(3);
    second.recordSource("phase-2/model-0", 2, 4, 5);
    assertThat(second.remainingStepCount()).isEqualTo(1);
    assertThat(second.consumption()).isEqualTo(new Work(5, 14, 20));
    fixture.state.modelChanged(1);
    var revisedSecond = fixture.solverBudget(config, 1_000, "phase-2");
    assertThat(revisedSecond.remainingStepCount()).isEqualTo(1);
    revisedSecond.recordSource("phase-2/model-1", 1, 2, 3);
    assertThat(revisedSecond.isTerminated()).isTrue();
    assertThat(revisedSecond.report().limits())
        .extracting(ResumableSearchBudget.Limit::origin)
        .containsExactly(ResumableSearchBudget.Origin.PHASE, ResumableSearchBudget.Origin.SOLVER);
    assertThat(revisedSecond.report().limits())
        .extracting(ResumableSearchBudget.Limit::consumed)
        .containsExactly(3L, 23L);
  }

  @Test
  void problemChangeScoringSurvivesCounterResetWithoutConsumingPhaseBudgets() {
    var fixture = new Fixture();
    var config = new TerminationConfig().withScoreCalculationCountLimit(20L);
    // Also retain work performed before the first resumable phase creates its budget view.
    fixture.state.recordProblemChangeScoreCalculations(2);
    var solver = fixture.solverBudget(config, 900);
    var phase = fixture.budget(config);
    assertThat(solver.consumption().scoreCalculations()).isEqualTo(13);
    fixture.state.modelChanged(1);
    fixture.state.recordProblemChangeScoreCalculations(3);
    assertThat(solver.consumption().scoreCalculations()).isEqualTo(16);
    assertThat(phase.consumption()).isEqualTo(Work.ZERO);
    assertThat(solver.report().limits().getFirst().consumed()).isEqualTo(16);
    fixture.solverBudget(config, 1_000);
    assertThat(solver.consumption().scoreCalculations()).isEqualTo(27);
    assertThat(solver.isTerminated()).isTrue();
    assertThat(phase.isTerminated()).isFalse();
  }

  @Test
  void ordinaryWorkBeforeFirstResumablePhaseSurvivesModelRevision() {
    var fixture = new Fixture();
    fixture.state.recordProblemChangeScoreCalculations(2);
    fixture.state.captureOrdinaryWork(fixture.owner);
    fixture.state.captureOrdinaryWork(fixture.owner);
    assertThat(fixture.state.solverMoveEvaluationCount(fixture.owner)).isEqualTo(7);
    assertThat(fixture.state.solverScoreCalculationCount(fixture.owner)).isEqualTo(13);
    fixture.state.modelChanged(1);
    fixture.state.recordProblemChangeScoreCalculations(3);
    when(fixture.owner.getMoveEvaluationCount()).thenReturn(1L);
    when(fixture.owner.getScoreCalculationCount()).thenReturn(2L);
    when(fixture.owner.getBestSolutionTimeMillis()).thenReturn(1_050L);
    fixture.clock.now = 1_100;
    var budget =
        fixture.solverBudget(
            and(
                steps(5),
                new TerminationConfig().withSpentLimit(Duration.ofMillis(100)),
                new TerminationConfig().withUnimprovedSpentLimit(Duration.ofMillis(100))),
            1_050);
    assertThat(budget.consumption()).isEqualTo(new Work(0, 8, 18));
    assertThat(budget.remainingStepCount()).isEqualTo(5);
    assertThat(budget.report().limits())
        .extracting(ResumableSearchBudget.Limit::consumed)
        .containsExactly(0L, 200L, 150L);
    assertThat(fixture.state.solverScoreCalculationCount(fixture.owner)).isEqualTo(18);
  }

  @Test
  void capturingAnExistingSolverBudgetDeduplicatesItsOrdinarySource() {
    var fixture = new Fixture();
    var budget = fixture.solverBudget(steps(10), 900);
    budget.recordSource("child-0", 2, 3, 5);
    fixture.state.captureOrdinaryWork(fixture.owner);
    assertThat(budget.consumption()).isEqualTo(new Work(2, 10, 16));
    when(fixture.owner.getMoveEvaluationCount()).thenReturn(9L);
    when(fixture.owner.getScoreCalculationCount()).thenReturn(15L);
    fixture.state.captureOrdinaryWork(fixture.owner);
    fixture.state.captureOrdinaryWork(fixture.owner);
    assertThat(budget.refreshOrdinaryWork(fixture.owner)).isEqualTo(Work.ZERO);
    assertThat(budget.consumption()).isEqualTo(new Work(2, 12, 20));
    assertThat(budget.remainingStepCount()).isEqualTo(8);
  }

  @Test
  void solverPredicateViewsIncludeOrdinaryWorkAfterTheLastCoordinatorCommitExactlyOnce() {
    var fixture = new Fixture();
    fixture.state.recordProblemChangeScoreCalculations(2);
    assertThat(fixture.state.solverScoreCalculationCount(fixture.owner)).isEqualTo(13);
    assertThat(fixture.state.solverMoveEvaluationCount(fixture.owner)).isEqualTo(7);
    var config = new TerminationConfig().withMoveCountLimit(100L);
    var budget = fixture.solverBudget(config, 900);
    budget.recordSource("island-0/model-0", 5, 20, 30);
    assertThat(fixture.state.solverScoreCalculationCount(fixture.owner)).isEqualTo(43);
    assertThat(fixture.state.solverMoveEvaluationCount(fixture.owner)).isEqualTo(27);
    when(fixture.owner.getScoreCalculationCount()).thenReturn(16L);
    when(fixture.owner.getMoveEvaluationCount()).thenReturn(10L);
    assertThat(fixture.state.solverScoreCalculationCount(fixture.owner)).isEqualTo(48);
    assertThat(fixture.state.solverMoveEvaluationCount(fixture.owner)).isEqualTo(30);
    fixture.solverBudget(config, 900);
    assertThat(fixture.state.solverScoreCalculationCount(fixture.owner)).isEqualTo(48);
    assertThat(fixture.state.solverMoveEvaluationCount(fixture.owner)).isEqualTo(30);
    fixture.state.modelChanged(1);
    when(fixture.owner.getScoreCalculationCount()).thenReturn(1L);
    when(fixture.owner.getMoveEvaluationCount()).thenReturn(0L);
    assertThat(fixture.state.solverScoreCalculationCount(fixture.owner)).isEqualTo(49);
    assertThat(fixture.state.solverMoveEvaluationCount(fixture.owner)).isEqualTo(30);
  }

  @Test
  void refreshingOrdinaryCoordinatorWorkDoesNotDuplicateItsRawCounterDelta() {
    var fixture = new Fixture();
    var budget = fixture.solverBudget(steps(100), 900);
    budget.recordSource("child-0", 1, 2, 3);
    when(fixture.owner.getMoveEvaluationCount()).thenReturn(9L);
    when(fixture.owner.getScoreCalculationCount()).thenReturn(16L);
    assertThat(budget.refreshOrdinaryWork(fixture.owner)).isEqualTo(new Work(0, 2, 5));
    assertThat(budget.refreshOrdinaryWork(fixture.owner)).isEqualTo(Work.ZERO);
    assertThat(budget.consumption()).isEqualTo(new Work(1, 11, 19));
    assertThat(fixture.state.solverMoveEvaluationCount(fixture.owner)).isEqualTo(11);
    assertThat(fixture.state.solverScoreCalculationCount(fixture.owner)).isEqualTo(19);
  }

  @Test
  void retiringNestedScopeReleasesItsStateWithoutChangingAncestorConsumption() {
    var fixture = new Fixture();
    var ancestor = fixture.budget(steps(100));
    var nested =
        new ResumableSearchBudget<>(
            "phase-0/island-0/episode-0/phase-0",
            steps(3),
            fixture.policy,
            fixture.state,
            fixture.clock,
            1_000);
    var finalWork = nested.recordSource("child", 3, 7, 11);
    ancestor.recordDelta(finalWork);
    nested.retireScope();
    var reopened =
        new ResumableSearchBudget<>(
            "phase-0/island-0/episode-0/phase-0",
            steps(3),
            fixture.policy,
            fixture.state,
            fixture.clock,
            1_000);
    assertThat(reopened.consumption()).isEqualTo(Work.ZERO);
    assertThat(ancestor.consumption()).isEqualTo(finalWork);
    assertThatThrownBy(() -> fixture.solverBudget(steps(3), 900).retireScope())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("solve-lifetime");
  }

  @Test
  void elapsedAndIdleTimeUseOriginalOriginsAndOnlyStrictImprovementsResetIdle() {
    var fixture = new Fixture();
    var budget =
        fixture.budget(new TerminationConfig().withUnimprovedSpentLimit(Duration.ofMillis(50)));
    budget.rebaseArchive(InnerScore.fullyAssigned(SimpleScore.of(1)));
    fixture.clock.now = 1_040;
    budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(2)), fixture.clock.now);
    fixture.clock.now = 1_060;
    assertThat(budget.isTerminated()).isFalse();
    budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.of(2)), fixture.clock.now);
    fixture.clock.now = 1_090;
    assertThat(budget.isTerminated()).isTrue();
    var zero = new Fixture().budget(new TerminationConfig().withSpentLimit(Duration.ZERO));
    assertThat(zero.isTerminated()).isTrue();
  }

  @Test
  void inheritedTreeIncludesPlumbingWithoutReplacingTheWorkExpression() {
    var fixture = new Fixture();
    var solver =
        fixture.solverBudget(and(steps(5), new TerminationConfig().withMoveCountLimit(10L)), 900);
    assertThat(solver.remainingStepCount()).isEqualTo(ResumableSearchBudget.UNBOUNDED);
    solver.recordDelta(2, 3, 0);
    assertThat(solver.remainingStepCount()).isEqualTo(3);
    fixture.plumbing.terminateEarly();
    assertThat(solver.isTerminated()).isTrue();
  }

  @Test
  void unsupportedHistoryLeavesFailWithTheirScopeAndExpressionPath() {
    for (var unsupported :
        List.of(
            new TerminationConfig().withDiminishedReturns(),
            new TerminationConfig()
                .withUnimprovedSpentLimit(Duration.ofMillis(10))
                .withUnimprovedScoreDifferenceThreshold("1"))) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new Fixture().budget(and(steps(3), unsupported)))
          .withMessageContaining("Unsupported resumable aggregate termination")
          .withMessageContaining("phase-0.termination[1]");
    }
  }

  private static TerminationConfig steps(int count) {
    return new TerminationConfig().withStepCountLimit(count);
  }

  private static TerminationConfig and(TerminationConfig... children) {
    return new TerminationConfig()
        .withTerminationCompositionStyle(TerminationCompositionStyle.AND)
        .withTerminationConfigList(List.of(children));
  }

  private static TerminationConfig or(TerminationConfig... children) {
    return new TerminationConfig()
        .withTerminationCompositionStyle(TerminationCompositionStyle.OR)
        .withTerminationConfigList(List.of(children));
  }

  private static final class Fixture {
    private final PersistentState state = new PersistentState();
    private final MutableClock clock = new MutableClock();
    private final HeuristicConfigPolicy<TestdataSolution> policy =
        mock(HeuristicConfigPolicy.class);
    private final SolverScope<TestdataSolution> owner = mock(SolverScope.class);
    private final BasicPlumbingTermination<TestdataSolution> plumbing =
        new BasicPlumbingTermination<>(false);

    private Fixture() {
      when(policy.getScoreDefinition()).thenReturn(new SimpleScoreDefinition());
      when(owner.getMoveEvaluationCount()).thenReturn(7L);
      when(owner.getScoreCalculationCount()).thenReturn(11L);
      when(owner.<SimpleScore>getBestScore())
          .thenReturn(InnerScore.fullyAssigned(SimpleScore.ZERO));
      when(owner.getBestSolutionTimeMillis()).thenReturn(950L);
      when(owner.getStartingSystemTimeMillis()).thenReturn(900L);
    }

    private ResumableSearchBudget<TestdataSolution> budget(TerminationConfig config) {
      return new ResumableSearchBudget<>("phase-0", config, policy, state, clock, clock.millis());
    }

    private ResumableSearchBudget<TestdataSolution> solverBudget(
        TerminationConfig config, long start) {
      return solverBudget(config, start, "phase-0");
    }

    private ResumableSearchBudget<TestdataSolution> solverBudget(
        TerminationConfig config, long start, String phaseKey) {
      SolverTermination<TestdataSolution> definition =
          TerminationFactory.<TestdataSolution>create(config).buildTermination(policy, plumbing);
      return ResumableSearchBudget.forSolver(
          "solver", phaseKey, definition, owner, state, clock, start);
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
