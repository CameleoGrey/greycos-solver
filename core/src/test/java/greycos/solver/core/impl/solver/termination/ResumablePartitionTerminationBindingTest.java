package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ResumablePartitionTerminationBindingTest {

  @ParameterizedTest
  @CsvSource({"step,false", "step,true", "unimproved,false", "unimproved,true"})
  void phaseOnlyLimitsResetAcrossPhasesAndSurviveNestedRebinding(String kind, boolean nested) {
    var clock = new MutableClock();
    var parent = scope(clock);
    var parentPhase = partition(parent);
    PhaseTermination<TestdataSolution> definition =
        kind.equals("step")
            ? new StepCountTermination<>(2)
            : new UnimprovedStepCountTermination<>(2);
    var budget = PartitionTerminationBudget.forResumableSearch(definition, parentPhase);
    var childScope = scope(clock);
    var child = budget.createChildTermination(childScope);
    if (nested) {
      child = rebind(child, childScope);
      var innerBudget =
          PartitionTerminationBudget.forResumableSearch(
              PhaseTermination.bridge(UniversalTermination.and(child)), parentPhase);
      child = innerBudget.createChildTermination(childScope);
    }
    var bridge = PhaseTermination.bridge(child);
    child.solvingStarted(childScope);
    for (int index = 0; index < 2; index++) {
      var phase = search(childScope, index);
      child.phaseStarted(phase);
      assertThat(bridge.isPhaseTerminated(phase)).isFalse();
      for (int stepIndex = 0; stepIndex < 2; stepIndex++) {
        var step = new LocalSearchStepScope<>(phase, stepIndex);
        child.stepStarted(step);
        child.stepEnded(step);
        phase.setLastCompletedStepScope(step);
      }
      assertThat(bridge.isPhaseTerminated(phase)).isTrue();
      assertThat(child.isSolverTerminated(childScope)).isFalse();
      assertThat(bridge.calculatePhaseTimeGradient(phase)).isEqualTo(-1.0);
      child.phaseEnded(phase);
      assertThat(child.isSolverTerminated(childScope)).isFalse();
    }
    assertThat(budget.isDefinitelyTerminated()).isFalse();
    child.solvingEnded(childScope);
  }

  @ParameterizedTest
  @CsvSource({"moves,false", "moves,true", "scores,false", "scores,true"})
  void workPredicatesKeepPhaseAndSolverOriginsAndIndependentSiblingCounts(
      String kind, boolean solverOrigin) {
    var clock = new MutableClock();
    var parentPhase = partition(scope(clock));
    PhaseTermination<TestdataSolution> definition =
        kind.equals("moves")
            ? new MoveCountTermination<>(10)
            : new ScoreCalculationCountTermination<>(10);
    var budget =
        PartitionTerminationBudget.forResumableSearch(
            solverOrigin
                ? PhaseTermination.bridge(UniversalTermination.and(definition))
                : definition,
            parentPhase);
    var childScope = scope(clock);
    var scores = new AtomicLong(7);
    when(childScope.getScoreDirector().getCalculationCount()).thenAnswer(ignored -> scores.get());
    childScope.addMoveEvaluationCount(7);
    var child = rebind(budget.createChildTermination(childScope), childScope);
    var siblingScope = scope(clock);
    var sibling = budget.createChildTermination(siblingScope);
    child.solvingStarted(childScope);
    var first = search(childScope, 0);
    child.phaseStarted(first);
    childScope.addMoveEvaluationCount(3);
    scores.addAndGet(3);
    assertThat(child.isPhaseTerminated(first)).isEqualTo(solverOrigin);
    assertThat(child.isSolverTerminated(childScope)).isTrue();
    assertThat(sibling.isSolverTerminated(siblingScope)).isFalse();
    child.phaseEnded(first);
    var second = search(childScope, 1);
    child.phaseStarted(second);
    assertThat(child.isPhaseTerminated(second)).isEqualTo(solverOrigin);
    assertThat(child.calculatePhaseTimeGradient(second)).isEqualTo(-1.0);
    assertThat(budget.isDefinitelyTerminated()).isFalse();
    child.phaseEnded(second);
    child.solvingEnded(childScope);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void sharedDeadlineDoesNotRemoveUnavailableOperandsFromAnd(boolean and) {
    var clock = new MutableClock();
    var parentPhase = partition(scope(clock));
    var time = new TimeMillisSpentTermination<TestdataSolution>(0);
    var idle = new UnimprovedStepCountTermination<TestdataSolution>(2);
    var definition =
        and ? UniversalTermination.and(time, idle) : UniversalTermination.or(time, idle);
    var budget = PartitionTerminationBudget.forResumableSearch(definition, parentPhase);
    var childScope = scope(clock);
    var child = budget.createChildTermination(childScope);
    child.solvingStarted(childScope);
    var construction = new ConstructionHeuristicPhaseScope<>(childScope, 0);
    construction.startingNow();
    construction.reset();
    child.phaseStarted(construction);
    assertThat(budget.isDefinitelyTerminated()).isEqualTo(!and);
    assertThat(child.isSolverTerminated(childScope)).isEqualTo(!and);
    assertThat(PhaseTermination.bridge(child).isPhaseTerminated(construction)).isEqualTo(!and);
    child.phaseEnded(construction);
    child.solvingEnded(childScope);
  }

  @Test
  void sharedScoreChangesOnlyAtRefreshAndNeverUsesAPartialChildScore() {
    var clock = new MutableClock();
    var parent = spy(scope(clock));
    var parentPhase = spy(partition(parent));
    var budget =
        PartitionTerminationBudget.forResumableSearch(
            new BestScoreTermination<TestdataSolution>(
                new SimpleScoreDefinition(), SimpleScore.of(10), new double[0]),
            parentPhase);
    var childScope = scope(clock);
    childScope.setInitializedBestScore(SimpleScore.of(1000));
    var child = budget.createChildTermination(childScope);
    parent.setInitializedBestScore(SimpleScore.of(10));
    clearInvocations(parent, parentPhase);
    assertThat(child.isSolverTerminated(childScope)).isFalse();
    assertThat(budget.isDefinitelyTerminated()).isFalse();
    verifyNoInteractions(parent, parentPhase);
    budget.refresh();
    clearInvocations(parent, parentPhase);
    assertThat(child.isSolverTerminated(childScope)).isTrue();
    assertThat(budget.isDefinitelyTerminated()).isTrue();
    verifyNoInteractions(parent, parentPhase);
  }

  @Test
  void deadlinesAdvanceWithoutRefreshingOrReadingTheParkedParent() {
    var clock = new MutableClock();
    var parent = spy(scope(clock));
    clock.now.addAndGet(80);
    var parentPhase = spy(partition(parent));
    var phaseBudget =
        PartitionTerminationBudget.forResumableSearch(
            new TimeMillisSpentTermination<>(100), parentPhase);
    var solverBudget =
        PartitionTerminationBudget.forResumableSearch(
            PhaseTermination.bridge(new TimeMillisSpentTermination<>(100)), parentPhase);
    var childScope = scope(clock);
    var phaseChild = rebind(phaseBudget.createChildTermination(childScope), childScope);
    var solverChild = solverBudget.createChildTermination(childScope);
    clearInvocations(parent, parentPhase);
    clock.now.addAndGet(20);
    assertThat(solverBudget.isDefinitelyTerminated()).isTrue();
    assertThat(solverChild.isSolverTerminated(childScope)).isTrue();
    assertThat(phaseBudget.isDefinitelyTerminated()).isFalse();
    assertThat(phaseChild.isSolverTerminated(childScope)).isFalse();
    clock.now.addAndGet(80);
    assertThat(phaseBudget.isDefinitelyTerminated()).isTrue();
    assertThat(phaseChild.isSolverTerminated(childScope)).isTrue();
    assertThat(phaseChild.calculateSolverTimeGradient(childScope)).isEqualTo(-1.0);
    verifyNoInteractions(parent, parentPhase);
  }

  @Test
  void inheritedPredicatesDoNotContributeToEpisodeCooling() {
    var clock = new MutableClock();
    var budget =
        PartitionTerminationBudget.forResumableSearch(
            new StepCountTermination<>(5), partition(scope(clock)));
    var childScope = scope(clock);
    var child = rebind(budget.createChildTermination(childScope), childScope);
    var phase = search(childScope, 0);
    child.solvingStarted(childScope);
    child.phaseStarted(phase);
    phase.setLastCompletedStepScope(new LocalSearchStepScope<>(phase, 3));
    var episode =
        UniversalTermination.or(
            PhaseTermination.bridge(child), new StepCountTermination<TestdataSolution>(10));
    assertThat(episode.calculatePhaseTimeGradient(phase)).isEqualTo(0.4);
    assertThat(episode.isPhaseTerminated(phase)).isFalse();
    phase.setLastCompletedStepScope(new LocalSearchStepScope<>(phase, 4));
    assertThat(episode.isPhaseTerminated(phase)).isTrue();
    assertThat(episode.calculatePhaseTimeGradient(phase)).isEqualTo(0.5);
    child.phaseEnded(phase);
    child.solvingEnded(childScope);
  }

  @SuppressWarnings("unchecked")
  private static UniversalTermination<TestdataSolution> rebind(
      UniversalTermination<TestdataSolution> termination, SolverScope<TestdataSolution> scope) {
    return (UniversalTermination<TestdataSolution>)
        ChildThreadSupportingTermination
            .<TestdataSolution, SolverScope<TestdataSolution>>assertChildThreadSupport(termination)
            .createChildThreadTermination(scope, ChildThreadType.PART_THREAD);
  }

  private static PartitionedSearchPhaseScope<TestdataSolution> partition(
      SolverScope<TestdataSolution> scope) {
    var phase = new PartitionedSearchPhaseScope<>(scope, 0);
    phase.startingNow();
    phase.reset();
    return phase;
  }

  private static LocalSearchPhaseScope<TestdataSolution> search(
      SolverScope<TestdataSolution> scope, int index) {
    var phase = new LocalSearchPhaseScope<>(scope, index);
    phase.startingNow();
    phase.reset();
    return phase;
  }

  @SuppressWarnings("unchecked")
  private static SolverScope<TestdataSolution> scope(Clock clock) {
    var scope = new SolverScope<TestdataSolution>(clock);
    scope.setScoreDirector(mock(InnerScoreDirector.class));
    scope.setInitializedBestScore(SimpleScore.ZERO);
    scope.setStartingInitializedScore(SimpleScore.ZERO);
    scope.startingNow();
    return scope;
  }

  private static final class MutableClock extends Clock {
    private final AtomicLong now = new AtomicLong(1000);

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
      return Instant.ofEpochMilli(millis());
    }

    @Override
    public long millis() {
      return now.get();
    }
  }
}
