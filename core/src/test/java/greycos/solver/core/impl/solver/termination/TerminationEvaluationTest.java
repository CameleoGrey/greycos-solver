package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedList;
import java.util.List;
import java.util.RandomAccess;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TerminationEvaluationTest {

  @Test
  void solverAndKeepsPhaseOnlyConditionsAtEveryNestingLevel() {
    SolverScope<TestdataSolution> solver = mock(SolverScope.class);
    LocalSearchPhaseScope<TestdataSolution> phase = mock(LocalSearchPhaseScope.class);
    when(phase.getSolverScope()).thenReturn(solver);
    when(solver.calculateTimeMillisSpentUpToNow()).thenReturn(100L);
    when(phase.getNextStepIndex()).thenReturn(2);
    var time = new TimeMillisSpentTermination<TestdataSolution>(100);
    var steps = new StepCountTermination<TestdataSolution>(3);
    var nested = UniversalTermination.and(time, UniversalTermination.or(steps));
    assertThat(nested.isSolverTerminated(solver)).isFalse();
    assertThat(PhaseTermination.bridge(nested).isPhaseTerminated(phase)).isFalse();
    when(phase.getNextStepIndex()).thenReturn(3);
    assertThat(nested.isSolverTerminated(solver)).isFalse();
    assertThat(PhaseTermination.bridge(nested).isPhaseTerminated(phase)).isTrue();
    assertThat(
            UniversalTermination.or(time, UniversalTermination.and(steps))
                .isSolverTerminated(solver))
        .isTrue();
  }

  @Test
  void unavailableNestedBranchesCannotSatisfyConstructionConjunction() {
    SolverScope<TestdataSolution> solver = mock(SolverScope.class);
    var construction = new ConstructionHeuristicPhaseScope<>(solver, 0);
    var unavailable =
        UniversalTermination.<TestdataSolution>and(
            new UnimprovedStepCountTermination<>(1), new DiminishedReturnsTermination<>(100, 0.1));
    var reached = new TimeMillisSpentTermination<TestdataSolution>(0);
    assertThat(unavailable.isSolverTerminated(solver)).isFalse();
    assertThat(unavailable.isPhaseTerminated(construction)).isFalse();
    assertThat(
            PhaseTermination.bridge(UniversalTermination.and(reached, unavailable))
                .isPhaseTerminated(construction))
        .isFalse();
    assertThat(
            PhaseTermination.bridge(UniversalTermination.or(reached, unavailable))
                .isPhaseTerminated(construction))
        .isTrue();
    assertThat(new AndCompositeTermination<TestdataSolution>(List.of()).isSolverTerminated(solver))
        .isFalse();
    assertThat(
            new AndCompositeTermination<TestdataSolution>(List.of())
                .isPhaseTerminated(construction))
        .isFalse();
  }

  @Test
  void bridgeUsesGlobalClockButPhaseOnlyStepProgress() {
    SolverScope<TestdataSolution> solver = mock(SolverScope.class);
    LocalSearchPhaseScope<TestdataSolution> phase = mock(LocalSearchPhaseScope.class);
    when(phase.getSolverScope()).thenReturn(solver);
    when(solver.calculateTimeMillisSpentUpToNow()).thenReturn(80L);
    when(phase.calculatePhaseTimeMillisSpentUpToNow()).thenReturn(5L);
    when(phase.getNextStepIndex()).thenReturn(3);
    var tree =
        UniversalTermination.<TestdataSolution>and(
            new TimeMillisSpentTermination<>(100), new StepCountTermination<>(4));
    assertThat(tree.calculatePhaseTimeGradient(phase)).isEqualTo(0.05);
    assertThat(tree.calculateSolverTimeGradient(solver)).isEqualTo(0.8);
    assertThat(PhaseTermination.bridge(tree).calculatePhaseTimeGradient(phase)).isEqualTo(0.75);
    when(solver.calculateTimeMillisSpentUpToNow()).thenReturn(100L);
    when(phase.getNextStepIndex()).thenReturn(4);
    assertThat(PhaseTermination.bridge(tree).isPhaseTerminated(phase)).isTrue();
    assertThat(tree.isPhaseTerminated(phase)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void globalEndpointPrecedesTheUniversalLeafsPhaseApplicability() {
    SolverScope<TestdataSolution> solver = mock(SolverScope.class);
    var phase = new LocalSearchPhaseScope<>(solver, 0);
    var leaf =
        (MockableSolverTermination<TestdataSolution>)
            mock(
                MockableSolverTermination.class,
                withSettings().extraInterfaces(MockablePhaseTermination.class));
    var phaseLeaf = (MockablePhaseTermination<TestdataSolution>) leaf;
    when(phaseLeaf.isApplicableTo(any())).thenReturn(false);
    when(leaf.isSolverTerminated(solver)).thenReturn(true);
    when(leaf.calculateSolverTimeGradient(solver)).thenReturn(0.6);
    var bridge = PhaseTermination.bridge(UniversalTermination.and(leaf));
    assertThat(bridge.isPhaseTerminated(phase)).isTrue();
    assertThat(bridge.calculatePhaseTimeGradient(phase)).isEqualTo(0.6);
    verify(phaseLeaf, never()).isPhaseTerminated(phase);
    verify(phaseLeaf, never()).calculatePhaseTimeGradient(phase);
  }

  @Test
  void zeroBudgetsProvideCompletedGradientsEvenBeforeAnyWork() {
    SolverScope<TestdataSolution> solver = mock(SolverScope.class);
    InnerScoreDirector<TestdataSolution, ?> director = mock(InnerScoreDirector.class);
    doReturn(director).when(solver).getScoreDirector();
    LocalSearchPhaseScope<TestdataSolution> phase = mock(LocalSearchPhaseScope.class);
    when(phase.getSolverScope()).thenReturn(solver);
    doReturn(director).when(phase).getScoreDirector();
    var lastStep = new greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope<>(phase, -1);
    when(phase.getLastCompletedStepScope()).thenReturn(lastStep);
    when(phase.getBestSolutionStepIndex()).thenReturn(-1);
    List<PhaseTermination<TestdataSolution>> leaves =
        List.of(
            new StepCountTermination<>(0),
            new UnimprovedStepCountTermination<>(0),
            new MoveCountTermination<>(0),
            new ScoreCalculationCountTermination<>(0),
            new TimeMillisSpentTermination<>(0));
    for (var leaf : leaves) {
      assertThat(leaf.calculatePhaseTimeGradient(phase)).as(leaf.toString()).isEqualTo(1.0);
    }
    var clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    var idle = new UnimprovedTimeMillisSpentTermination<TestdataSolution>(0, clock);
    var thresholdIdle =
        new UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<TestdataSolution>(
            0, SimpleScore.ONE, clock);
    var realPhase = new LocalSearchPhaseScope<>(solver, 0);
    when(solver.getClock()).thenReturn(clock);
    realPhase.startingNow();
    for (var leaf : List.<UniversalTermination<TestdataSolution>>of(idle, thresholdIdle)) {
      leaf.solvingStarted(solver);
      leaf.phaseStarted(realPhase);
      assertThat(leaf.calculateSolverTimeGradient(solver)).isEqualTo(1.0);
      assertThat(leaf.calculatePhaseTimeGradient(realPhase)).isEqualTo(1.0);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void immutableOperandsKeepShortCircuitAndGradientOrder(boolean and) {
    SolverScope<TestdataSolution> solver = mock(SolverScope.class);
    var phase = new LocalSearchPhaseScope<>(solver, 0);
    var first =
        (MockableSolverTermination<TestdataSolution>)
            mock(
                MockableSolverTermination.class,
                withSettings().extraInterfaces(MockablePhaseTermination.class));
    var second =
        (MockableSolverTermination<TestdataSolution>)
            mock(
                MockableSolverTermination.class,
                withSettings().extraInterfaces(MockablePhaseTermination.class));
    var firstPhase = (MockablePhaseTermination<TestdataSolution>) first;
    var secondPhase = (MockablePhaseTermination<TestdataSolution>) second;
    when(firstPhase.isApplicableTo(any())).thenReturn(true);
    when(secondPhase.isApplicableTo(any())).thenReturn(true);
    when(first.isSolverTerminated(solver)).thenReturn(!and);
    when(firstPhase.isPhaseTerminated(phase)).thenReturn(!and);
    when(first.calculateSolverTimeGradient(solver)).thenReturn(0.2);
    when(second.calculateSolverTimeGradient(solver)).thenReturn(0.8);
    when(firstPhase.calculatePhaseTimeGradient(phase)).thenReturn(0.3);
    when(secondPhase.calculatePhaseTimeGradient(phase)).thenReturn(0.7);
    var supplied = new LinkedList<Termination<TestdataSolution>>(List.of(first, second));
    var composite =
        and ? new AndCompositeTermination<>(supplied) : new OrCompositeTermination<>(supplied);
    supplied.clear();
    assertThat(composite.terminationList)
        .isInstanceOf(RandomAccess.class)
        .containsExactly(first, second);
    assertThatThrownBy(() -> composite.terminationList.clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(composite.isSolverTerminated(solver)).isEqualTo(!and);
    assertThat(composite.isPhaseTerminated(phase)).isEqualTo(!and);
    verify(second, never()).isSolverTerminated(solver);
    verify(secondPhase, never()).isPhaseTerminated(phase);
    assertThat(composite.calculateSolverTimeGradient(solver)).isEqualTo(and ? 0.2 : 0.8);
    assertThat(composite.calculatePhaseTimeGradient(phase)).isEqualTo(and ? 0.3 : 0.7);
    var order = inOrder(first, second);
    order.verify(first).calculateSolverTimeGradient(solver);
    order.verify(second).calculateSolverTimeGradient(solver);
    order.verify(firstPhase).calculatePhaseTimeGradient(phase);
    order.verify(secondPhase).calculatePhaseTimeGradient(phase);
  }

  @Test
  void varargsOperandsAreCopied() {
    Termination<TestdataSolution>[] operands =
        new Termination[] {
          new TimeMillisSpentTermination<TestdataSolution>(0),
          new StepCountTermination<TestdataSolution>(2)
        };
    var first = operands[0];
    var composite = new OrCompositeTermination<>(operands);
    operands[0] = new TimeMillisSpentTermination<>(100);
    assertThat(composite.terminationList.getFirst()).isSameAs(first);
  }
}
