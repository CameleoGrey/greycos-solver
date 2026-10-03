package greycos.solver.core.impl.solver.termination;

import static greycos.solver.core.testutil.PlannerTestUtils.mockSolverScope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AcceptorCoolingTerminationTest {

  @Test
  void factoryDiminishedReturnsAndPlumbingDoNotSupplyCooling() {
    SolverScope<TestdataSolution> scope = mockSolverScope();
    var phase = new LocalSearchPhaseScope<>(scope, 0);
    var config = new TerminationConfig().withDiminishedReturns();
    var phaseTermination = buildPhase(config);
    phaseTermination.phaseStarted(phase);
    assertThat(phaseTermination.calculatePhaseTimeGradient(phase)).isEqualTo(-1.0);
    var solverTermination = buildSolver(config);
    assertThat(solverTermination.calculateSolverTimeGradient(scope)).isEqualTo(-1.0);
    assertThat(PhaseTermination.bridge(solverTermination).calculatePhaseTimeGradient(phase))
        .isEqualTo(-1.0);
    assertThat(
            new PhaseToSolverTerminationBridge<>(solverTermination)
                .calculatePhaseTimeGradient(phase))
        .isEqualTo(-1.0);
  }

  @Test
  void phaseOnlyLeavesDoNotInventSolverCoolingThroughLegacyBridge() {
    SolverScope<TestdataSolution> scope = mockSolverScope();
    var phase = new LocalSearchPhaseScope<>(scope, 0);
    List<Termination<TestdataSolution>> leaves =
        List.of(
            new DiminishedReturnsTermination<>(100, 0.1),
            new StepCountTermination<>(10),
            new UnimprovedStepCountTermination<>(10));
    for (var leaf : leaves) {
      assertThat(new PhaseToSolverTerminationBridge<>(leaf).calculatePhaseTimeGradient(phase))
          .as(leaf.toString())
          .isEqualTo(-1.0);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void factorySupportedCoolingSurvivesDiminishedReturns(boolean and) {
    SolverScope<TestdataSolution> scope = mockSolverScope();
    when(scope.calculateTimeMillisSpentUpToNow()).thenReturn(60L);
    var phase = startedPhase(scope);
    when(scope.getMoveEvaluationCount()).thenReturn(4L);
    when(scope.getClock()).thenReturn(Clock.fixed(Instant.ofEpochMilli(20L), ZoneOffset.UTC));
    var config =
        new TerminationConfig()
            .withDiminishedReturns()
            .withSpentLimit(Duration.ofMillis(100))
            .withMoveCountLimit(10L)
            .withTerminationCompositionStyle(
                and ? TerminationCompositionStyle.AND : TerminationCompositionStyle.OR);
    assertThat(buildPhase(config).calculatePhaseTimeGradient(phase)).isEqualTo(and ? 0.2 : 0.4);
    var solverTermination = buildSolver(config);
    assertThat(solverTermination.calculateSolverTimeGradient(scope)).isEqualTo(and ? 0.4 : 0.6);
    assertThat(PhaseTermination.bridge(solverTermination).calculatePhaseTimeGradient(phase))
        .isEqualTo(and ? 0.4 : 0.6);
  }

  @Test
  void nestedUnsupportedOrDoesNotFreezeAndCooling() {
    var unsupported =
        new TerminationConfig()
            .withTerminationConfigList(
                List.of(
                    new TerminationConfig().withDiminishedReturns(),
                    new TerminationConfig().withDiminishedReturns()));
    var config =
        new TerminationConfig()
            .withTerminationCompositionStyle(TerminationCompositionStyle.AND)
            .withTerminationConfigList(
                List.of(unsupported, new TerminationConfig().withMoveCountLimit(10L)));
    SolverScope<TestdataSolution> scope = mockSolverScope();
    var phase = startedPhase(scope);
    when(scope.getMoveEvaluationCount()).thenReturn(3L);
    assertThat(buildPhase(config).calculatePhaseTimeGradient(phase)).isEqualTo(0.3);
    assertThat(buildSolver(config).calculateSolverTimeGradient(scope)).isEqualTo(0.3);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void emptyOrInapplicableCompositesHaveUnsupportedCooling(boolean and) {
    SolverScope<TestdataSolution> scope = mockSolverScope();
    var phase = new ConstructionHeuristicPhaseScope<>(scope, 0);
    var empty = composite(and, List.of());
    assertThat(empty.calculateSolverTimeGradient(scope)).isEqualTo(-1.0);
    assertThat(empty.calculatePhaseTimeGradient(phase)).isEqualTo(-1.0);
    var inapplicable = composite(and, List.of(new DiminishedReturnsTermination<>(100, 0.1)));
    assertThat(inapplicable.calculateSolverTimeGradient(scope)).isEqualTo(-1.0);
    assertThat(inapplicable.calculatePhaseTimeGradient(phase)).isEqualTo(-1.0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void invalidGradientsRemainVisibleInEitherChildOrder(boolean and) {
    SolverScope<TestdataSolution> scope = mockSolverScope();
    var phase = startedPhase(scope);
    MockableSolverTermination<TestdataSolution> solverLeaf = mock(MockableSolverTermination.class);
    MockablePhaseTermination<TestdataSolution> phaseLeaf = mock(MockablePhaseTermination.class);
    when(phaseLeaf.isApplicableTo(phase.getClass())).thenReturn(true);
    for (double invalid :
        new double[] {
          -2.0, -0.5, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1.1
        }) {
      when(solverLeaf.calculateSolverTimeGradient(scope)).thenReturn(invalid);
      when(phaseLeaf.calculatePhaseTimeGradient(phase)).thenReturn(invalid);
      for (boolean invalidFirst : new boolean[] {false, true}) {
        var time = new TimeMillisSpentTermination<TestdataSolution>(100);
        List<Termination<TestdataSolution>> solverLeaves =
            invalidFirst ? List.of(solverLeaf, time) : List.of(time, solverLeaf);
        List<Termination<TestdataSolution>> phaseLeaves =
            invalidFirst ? List.of(phaseLeaf, time) : List.of(time, phaseLeaf);
        assertGradient(composite(and, solverLeaves).calculateSolverTimeGradient(scope), invalid);
        assertGradient(composite(and, phaseLeaves).calculatePhaseTimeGradient(phase), invalid);
      }
    }
  }

  @Test
  void partitionDiminishedReturnsHasNoCoolingBeforeDuringOrAfterSearch() {
    SolverScope<TestdataSolution> parent = mockSolverScope();
    var budget =
        new PartitionTerminationBudget<>(
            buildPhase(new TerminationConfig().withDiminishedReturns()),
            new LocalSearchPhaseScope<>(parent, 0));
    SolverScope<TestdataSolution> childScope = mockSolverScope();
    var child = budget.createChildTermination(childScope);
    var phase = new LocalSearchPhaseScope<>(childScope, 0);
    assertThat(child.calculateSolverTimeGradient(childScope)).isEqualTo(-1.0);
    child.phaseStarted(phase);
    assertThat(child.calculatePhaseTimeGradient(phase)).isEqualTo(-1.0);
    assertThat(PhaseTermination.bridge(child).calculatePhaseTimeGradient(phase)).isEqualTo(-1.0);
    child.phaseEnded(phase);
    assertThat(child.calculateSolverTimeGradient(childScope)).isEqualTo(-1.0);
  }

  @Test
  void partitionIgnoresUnsupportedNestedBranchAndPreservesSharedClockOrigin() {
    SolverScope<TestdataSolution> parent = mockSolverScope();
    when(parent.calculateTimeMillisSpentUpToNow()).thenReturn(70L);
    var unsupported =
        new OrCompositeTermination<TestdataSolution>(new DiminishedReturnsTermination<>(100, 0.1));
    var definition =
        new AndCompositeTermination<>(
            unsupported,
            PhaseTermination.bridge(new TimeMillisSpentTermination<TestdataSolution>(100)));
    var budget =
        new PartitionTerminationBudget<>(definition, new LocalSearchPhaseScope<>(parent, 0));
    SolverScope<TestdataSolution> childScope = mockSolverScope();
    var child = budget.createChildTermination(childScope);
    var phase = new LocalSearchPhaseScope<>(childScope, 0);
    child.phaseStarted(phase);
    assertThat(PhaseTermination.bridge(child).calculatePhaseTimeGradient(phase)).isEqualTo(0.7);
    var nestedBudget = new PartitionTerminationBudget<>(PhaseTermination.bridge(child), phase);
    SolverScope<TestdataSolution> grandchildScope = mockSolverScope();
    var grandchild = nestedBudget.createChildTermination(grandchildScope);
    var grandchildPhase = new LocalSearchPhaseScope<>(grandchildScope, 0);
    grandchild.phaseStarted(grandchildPhase);
    assertThat(grandchild.calculatePhaseTimeGradient(grandchildPhase)).isEqualTo(0.7);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void partitionRetainsInvalidLocalGradients(boolean and) {
    SolverScope<TestdataSolution> parent = mockSolverScope();
    SolverScope<TestdataSolution> childScope = mockSolverScope();
    var phase = new LocalSearchPhaseScope<>(childScope, 0);
    var leaf =
        (MockablePhaseTermination<TestdataSolution>)
            mock(
                MockablePhaseTermination.class,
                withSettings().extraInterfaces(ChildThreadSupportingTermination.class));
    when(leaf.isApplicableTo(any())).thenReturn(true);
    doReturn(leaf)
        .when(
            (ChildThreadSupportingTermination<TestdataSolution, SolverScope<TestdataSolution>>)
                leaf)
        .createChildThreadTermination(childScope, ChildThreadType.PART_THREAD);
    var definition = composite(and, List.of(leaf, new TimeMillisSpentTermination<>(100)));
    var child =
        new PartitionTerminationBudget<>(definition, startedPhase(parent))
            .createChildTermination(childScope);
    for (double invalid : new double[] {-2.0, Double.NaN, Double.POSITIVE_INFINITY, 1.1}) {
      when(leaf.calculatePhaseTimeGradient(phase)).thenReturn(invalid);
      assertGradient(child.calculatePhaseTimeGradient(phase), invalid);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void partitionSolverOriginBypassesPhaseApplicability() {
    SolverScope<TestdataSolution> parent = mockSolverScope();
    SolverScope<TestdataSolution> childScope = mockSolverScope();
    var phase = new LocalSearchPhaseScope<>(childScope, 0);
    var leaf =
        (MockablePhaseTermination<TestdataSolution>)
            mock(
                MockablePhaseTermination.class,
                withSettings()
                    .extraInterfaces(
                        MockableSolverTermination.class, ChildThreadSupportingTermination.class));
    doReturn(leaf)
        .when(
            (ChildThreadSupportingTermination<TestdataSolution, SolverScope<TestdataSolution>>)
                leaf)
        .createChildThreadTermination(childScope, ChildThreadType.PART_THREAD);
    when(leaf.isApplicableTo(any())).thenReturn(false);
    when(leaf.calculateSolverTimeGradient(childScope)).thenReturn(0.7);
    var definition = PhaseTermination.bridge((SolverTermination<TestdataSolution>) leaf);
    var child =
        new PartitionTerminationBudget<>(definition, new LocalSearchPhaseScope<>(parent, 0))
            .createChildTermination(childScope);
    child.phaseStarted(phase);
    assertThat(child.calculatePhaseTimeGradient(phase)).isEqualTo(0.7);
    verify(leaf, never()).calculatePhaseTimeGradient(phase);
  }

  @Test
  void islandSearchQuotaDoesNotReplaceInvalidCoolingWithCompletion() {
    SolverScope<TestdataSolution> scope = mockSolverScope();
    var quota = new IslandWorkQuota<>(scope, 0);
    MockablePhaseTermination<TestdataSolution> leaf = mock(MockablePhaseTermination.class);
    when(leaf.calculatePhaseTimeGradient(any())).thenReturn(Double.NaN);
    assertThat(quota.bindSearchTermination(leaf).gradient()).isNaN();
  }

  private static void assertGradient(double actual, double expected) {
    if (Double.isNaN(expected)) {
      assertThat(actual).isNaN();
    } else {
      assertThat(actual).isEqualTo(expected);
    }
  }

  private static UniversalTermination<TestdataSolution> composite(
      boolean and, List<Termination<TestdataSolution>> leaves) {
    return and ? new AndCompositeTermination<>(leaves) : new OrCompositeTermination<>(leaves);
  }

  private static LocalSearchPhaseScope<TestdataSolution> startedPhase(
      SolverScope<TestdataSolution> scope) {
    when(scope.getClock()).thenReturn(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    doReturn(mock(InnerScoreDirector.class)).when(scope).getScoreDirector();
    var phase = new LocalSearchPhaseScope<>(scope, 0);
    phase.startingNow();
    return phase;
  }

  @SuppressWarnings("unchecked")
  private static PhaseTermination<TestdataSolution> buildPhase(TerminationConfig config) {
    return TerminationFactory.<TestdataSolution>create(config)
        .buildTermination(
            mock(HeuristicConfigPolicy.class),
            PhaseTermination.bridge(new BasicPlumbingTermination<>(false)));
  }

  @SuppressWarnings("unchecked")
  private static SolverTermination<TestdataSolution> buildSolver(TerminationConfig config) {
    return TerminationFactory.<TestdataSolution>create(config)
        .buildTermination(mock(HeuristicConfigPolicy.class), new BasicPlumbingTermination<>(false));
  }
}
