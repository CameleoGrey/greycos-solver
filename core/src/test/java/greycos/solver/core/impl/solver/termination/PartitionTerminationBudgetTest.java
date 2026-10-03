package greycos.solver.core.impl.solver.termination;

import static greycos.solver.core.testutil.PlannerTestUtils.mockSolverScope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.definition.HardSoftScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class PartitionTerminationBudgetTest {

  private final SolverScope<TestdataSolution> parent = mockSolverScope();
  private final AbstractPhaseScope<TestdataSolution> parentPhase = phase(parent);

  @Test
  void phaseClockAndSolverClockRetainTheirOrigins() {
    when(parent.calculateTimeMillisSpentUpToNow()).thenReturn(900L);
    when(parentPhase.calculatePhaseTimeMillisSpentUpToNow()).thenReturn(20L);
    var phaseBudget =
        new PartitionTerminationBudget<>(new TimeMillisSpentTermination<>(100), parentPhase);
    var solverBudget =
        new PartitionTerminationBudget<>(
            PhaseTermination.bridge(new TimeMillisSpentTermination<>(1000)), parentPhase);
    SolverScope<TestdataSolution> child = mockSolverScope();
    var phaseTermination = phaseBudget.createChildTermination(parent);
    var solverTermination = solverBudget.createChildTermination(parent);
    assertThat(phaseTermination.calculateSolverTimeGradient(child)).isEqualTo(0.2);
    assertThat(solverTermination.calculateSolverTimeGradient(child)).isEqualTo(0.9);
    assertThat(phaseTermination.isSolverTerminated(child)).isFalse();
    when(parentPhase.calculatePhaseTimeMillisSpentUpToNow()).thenReturn(100L);
    phaseBudget.refresh();
    assertThat(phaseTermination.isSolverTerminated(child)).isTrue();
    assertThat(solverTermination.isSolverTerminated(child)).isFalse();
  }

  @Test
  void sharedSnapshotRemainsImmutableUntilParentRefresh() {
    when(parentPhase.calculatePhaseTimeMillisSpentUpToNow()).thenReturn(10L);
    var budget =
        new PartitionTerminationBudget<>(new TimeMillisSpentTermination<>(100), parentPhase);
    var child = budget.createChildTermination(parent);
    when(parentPhase.calculatePhaseTimeMillisSpentUpToNow()).thenReturn(200L);
    clearInvocations(parent, parentPhase);
    assertThat(child.isSolverTerminated(mockSolverScope())).isFalse();
    assertThat(child.calculateSolverTimeGradient(mockSolverScope())).isEqualTo(0.1);
    verifyNoInteractions(parent, parentPhase);
    budget.refresh();
    assertThat(child.isSolverTerminated(mockSolverScope())).isTrue();
  }

  @Test
  void orSharedTimeStopsEveryChildButAndStillRequiresEachChildsWork() {
    when(parentPhase.calculatePhaseTimeMillisSpentUpToNow()).thenReturn(100L);
    var andBudget =
        new PartitionTerminationBudget<>(
            UniversalTermination.and(
                new TimeMillisSpentTermination<>(100), new MoveCountTermination<>(5)),
            parentPhase);
    var orBudget =
        new PartitionTerminationBudget<>(
            UniversalTermination.or(
                new TimeMillisSpentTermination<>(100), new MoveCountTermination<>(5)),
            parentPhase);
    SolverScope<TestdataSolution> fast = mockSolverScope();
    SolverScope<TestdataSolution> slow = mockSolverScope();
    when(fast.getMoveEvaluationCount()).thenReturn(5L);
    when(slow.getMoveEvaluationCount()).thenReturn(4L);
    when(parent.getMoveEvaluationCount()).thenReturn(10000L);
    assertThat(andBudget.isDefinitelyTerminated()).isFalse();
    assertThat(orBudget.isDefinitelyTerminated()).isTrue();
    assertThat(andBudget.createChildTermination(parent).isSolverTerminated(fast)).isTrue();
    assertThat(andBudget.createChildTermination(parent).isSolverTerminated(slow)).isFalse();
    assertThat(orBudget.createChildTermination(parent).isSolverTerminated(fast)).isTrue();
    assertThat(orBudget.createChildTermination(parent).isSolverTerminated(slow)).isTrue();
    assertThat(andBudget.createChildTermination(parent).calculateSolverTimeGradient(slow))
        .isEqualTo(0.8);
  }

  @Test
  void nestedExpressionPreservesGroupingAndGradientComposition() {
    when(parentPhase.calculatePhaseTimeMillisSpentUpToNow()).thenReturn(50L);
    var budget =
        new PartitionTerminationBudget<>(
            UniversalTermination.or(
                UniversalTermination.and(
                    new TimeMillisSpentTermination<>(100), new StepCountTermination<>(10)),
                new MoveCountTermination<>(20)),
            parentPhase);
    SolverScope<TestdataSolution> childScope = mockSolverScope();
    var childPhase = phase(childScope);
    when(childScope.getMoveEvaluationCount()).thenReturn(5L);
    when(childPhase.getNextStepIndex()).thenReturn(9);
    var child = budget.createChildTermination(parent);
    child.phaseStarted(childPhase);
    assertThat(child.calculatePhaseTimeGradient(childPhase)).isEqualTo(0.5);
    assertThat(PhaseTermination.bridge(child).calculatePhaseTimeGradient(childPhase))
        .isEqualTo(0.5);
    when(childPhase.getNextStepIndex()).thenReturn(2);
    assertThat(PhaseTermination.bridge(child).calculatePhaseTimeGradient(childPhase))
        .isEqualTo(0.25);
    when(childPhase.getNextStepIndex()).thenReturn(9);
    assertThat(child.isPhaseTerminated(childPhase)).isFalse();
    when(parentPhase.calculatePhaseTimeMillisSpentUpToNow()).thenReturn(100L);
    budget.refresh();
    assertThat(budget.isDefinitelyTerminated()).isFalse();
    assertThat(child.isSolverTerminated(childScope)).isFalse();
    assertThat(child.isPhaseTerminated(childPhase)).isFalse();
    when(childPhase.getNextStepIndex()).thenReturn(10);
    assertThat(child.isPhaseTerminated(childPhase)).isTrue();
    when(childPhase.getNextStepIndex()).thenReturn(0);
    when(childScope.getMoveEvaluationCount()).thenReturn(20L);
    assertThat(child.isPhaseTerminated(childPhase)).isTrue();
  }

  @Test
  void scoreCalculationQuotaIsNeitherSummedAcrossChildrenNorEvaluatedOnParent() {
    var budget =
        new PartitionTerminationBudget<>(new ScoreCalculationCountTermination<>(10), parentPhase);
    SolverScope<TestdataSolution> first = mockSolverScope();
    SolverScope<TestdataSolution> second = mockSolverScope();
    InnerScoreDirector<TestdataSolution, SimpleScore> firstDirector =
        mock(InnerScoreDirector.class);
    InnerScoreDirector<TestdataSolution, SimpleScore> secondDirector =
        mock(InnerScoreDirector.class);
    doReturn(firstDirector).when(first).getScoreDirector();
    doReturn(secondDirector).when(second).getScoreDirector();
    when(firstDirector.getCalculationCount()).thenReturn(7L);
    when(secondDirector.getCalculationCount()).thenReturn(7L);
    var firstTermination = budget.createChildTermination(parent);
    var secondTermination = budget.createChildTermination(parent);
    assertThat(firstTermination.isSolverTerminated(first)).isFalse();
    assertThat(secondTermination.isSolverTerminated(second)).isFalse();
    when(firstDirector.getCalculationCount()).thenReturn(10L);
    assertThat(firstTermination.isSolverTerminated(first)).isTrue();
    assertThat(secondTermination.isSolverTerminated(second)).isFalse();
    assertThat(budget.isDefinitelyTerminated()).isFalse();
  }

  @Test
  void absoluteScoreRequiresInitializedMergedParentScore() {
    var scoreTermination =
        new BestScoreTermination<TestdataSolution>(
            new SimpleScoreDefinition(), SimpleScore.of(10), new double[0]);
    doReturn(InnerScore.withUnassignedCount(SimpleScore.of(100), 1)).when(parent).getBestScore();
    when(parent.isBestSolutionInitialized()).thenReturn(false);
    var budget =
        new PartitionTerminationBudget<>(PhaseTermination.bridge(scoreTermination), parentPhase);
    var child = budget.createChildTermination(parent);
    SolverScope<TestdataSolution> childScope = mockSolverScope();
    doReturn(InnerScore.fullyAssigned(SimpleScore.of(1000))).when(childScope).getBestScore();
    assertThat(child.isSolverTerminated(childScope)).isFalse();
    assertThat(child.calculateSolverTimeGradient(childScope)).isZero();
    doReturn(SimpleScore.ZERO).when(parent).getStartingInitializedScore();
    doReturn(InnerScore.fullyAssigned(SimpleScore.of(5))).when(parent).getBestScore();
    when(parent.isBestSolutionInitialized()).thenReturn(true);
    budget.refresh();
    assertThat(child.isSolverTerminated(childScope)).isFalse();
    assertThat(child.calculateSolverTimeGradient(childScope)).isEqualTo(0.5);
    doReturn(InnerScore.fullyAssigned(SimpleScore.of(10))).when(parent).getBestScore();
    assertThat(child.isSolverTerminated(childScope)).isFalse();
    budget.refresh();
    assertThat(child.isSolverTerminated(childScope)).isTrue();
    assertThat(budget.isDefinitelyTerminated()).isTrue();
  }

  @Test
  void feasibleScoreRequiresInitializedMergedParentAndSafeStartingGradient() {
    var termination =
        new BestScoreFeasibleTermination<TestdataSolution>(
            new HardSoftScoreDefinition(), new double[0]);
    doReturn(InnerScore.withUnassignedCount(HardSoftScore.ZERO, 1))
        .when(parentPhase)
        .getBestScore();
    doReturn(InnerScore.withUnassignedCount(HardSoftScore.ZERO, 1))
        .when(parentPhase)
        .getStartingScore();
    var budget = new PartitionTerminationBudget<>(termination, parentPhase);
    var child = budget.createChildTermination(parent);
    assertThat(child.isSolverTerminated(mockSolverScope())).isFalse();
    assertThat(child.calculateSolverTimeGradient(mockSolverScope())).isZero();
    doReturn(InnerScore.fullyAssigned(HardSoftScore.ZERO)).when(parentPhase).getBestScore();
    budget.refresh();
    assertThat(child.isSolverTerminated(mockSolverScope())).isTrue();
    assertThat(child.calculateSolverTimeGradient(mockSolverScope())).isZero();
  }

  @Test
  void phaseScoreGradientStartsWithTheFirstInitializedParentIncumbent() {
    var parentScope = new SolverScope<TestdataSolution>();
    parentScope.setBestScore(InnerScore.withUnassignedCount(SimpleScore.ZERO, 2));
    var enclosingPhase = new PartitionedSearchPhaseScope<>(parentScope, 0);
    enclosingPhase.reset();
    var termination =
        new BestScoreTermination<TestdataSolution>(
            new SimpleScoreDefinition(), SimpleScore.of(10), new double[0]);
    var budget = new PartitionTerminationBudget<>(termination, enclosingPhase);
    var child = budget.createChildTermination(parentScope);
    var childScope = new SolverScope<TestdataSolution>();
    assertThat(child.calculateSolverTimeGradient(childScope)).isZero();
    assertThat(child.isSolverTerminated(childScope)).isFalse();

    parentScope.setStartingInitializedScore(SimpleScore.of(2));
    parentScope.setInitializedBestScore(SimpleScore.of(2));
    budget.refresh();
    assertThat(child.calculateSolverTimeGradient(childScope)).isZero();
    assertThat(child.isSolverTerminated(childScope)).isFalse();

    parentScope.setInitializedBestScore(SimpleScore.of(6));
    budget.refresh();
    assertThat(enclosingPhase.getStartingScore().isFullyAssigned()).isFalse();
    assertThat(child.calculateSolverTimeGradient(childScope)).isEqualTo(0.5);
    assertThat(child.isSolverTerminated(childScope)).isFalse();

    parentScope.setInitializedBestScore(SimpleScore.of(10));
    budget.refresh();
    assertThat(child.calculateSolverTimeGradient(childScope)).isEqualTo(1.0);
    assertThat(child.isSolverTerminated(childScope)).isTrue();
  }

  @Test
  void phaseFeasibilityGradientStartsWithTheFirstInitializedParentIncumbent() {
    var parentScope = new SolverScope<TestdataSolution>();
    parentScope.setBestScore(InnerScore.withUnassignedCount(HardSoftScore.ZERO, 2));
    var enclosingPhase = new PartitionedSearchPhaseScope<>(parentScope, 0);
    enclosingPhase.reset();
    var termination =
        new BestScoreFeasibleTermination<TestdataSolution>(
            new HardSoftScoreDefinition(), new double[0]);
    var budget = new PartitionTerminationBudget<>(termination, enclosingPhase);
    var child = budget.createChildTermination(parentScope);
    var childScope = new SolverScope<TestdataSolution>();
    assertThat(child.calculateSolverTimeGradient(childScope)).isZero();
    assertThat(child.isSolverTerminated(childScope)).isFalse();

    parentScope.setStartingInitializedScore(HardSoftScore.of(-8, 0));
    parentScope.setInitializedBestScore(HardSoftScore.of(-8, 0));
    budget.refresh();
    assertThat(child.calculateSolverTimeGradient(childScope)).isZero();
    assertThat(child.isSolverTerminated(childScope)).isFalse();

    parentScope.setInitializedBestScore(HardSoftScore.of(-4, 0));
    budget.refresh();
    assertThat(child.calculateSolverTimeGradient(childScope)).isEqualTo(0.5);
    assertThat(child.isSolverTerminated(childScope)).isFalse();

    parentScope.setInitializedBestScore(HardSoftScore.ZERO);
    budget.refresh();
    assertThat(child.calculateSolverTimeGradient(childScope)).isEqualTo(1.0);
    assertThat(child.isSolverTerminated(childScope)).isTrue();
  }

  @Test
  void initializedPhaseScoreBaselineTakesPrecedenceOverTheEarlierSolverBaseline() {
    var parentScope = new SolverScope<TestdataSolution>();
    parentScope.setStartingInitializedScore(SimpleScore.ZERO);
    parentScope.setInitializedBestScore(SimpleScore.of(4));
    var enclosingPhase = new PartitionedSearchPhaseScope<>(parentScope, 1);
    enclosingPhase.reset();
    parentScope.setInitializedBestScore(SimpleScore.of(7));
    var termination =
        new BestScoreTermination<TestdataSolution>(
            new SimpleScoreDefinition(), SimpleScore.of(10), new double[0]);
    var budget = new PartitionTerminationBudget<>(termination, enclosingPhase);
    var child = budget.createChildTermination(parentScope);
    var childScope = new SolverScope<TestdataSolution>();
    assertThat(child.calculateSolverTimeGradient(childScope)).isEqualTo(0.5);
    assertThat(child.isSolverTerminated(childScope)).isFalse();
  }

  @Test
  void cancellationIsPublishedWithoutExposingParentPlumbingToChildren() {
    var plumbing = new BasicPlumbingTermination<TestdataSolution>(false);
    var budget = new PartitionTerminationBudget<>(PhaseTermination.bridge(plumbing), parentPhase);
    var child = budget.createChildTermination(parent);
    plumbing.terminateEarly();
    assertThat(child.isSolverTerminated(mockSolverScope())).isFalse();
    budget.refresh();
    assertThat(child.isSolverTerminated(mockSolverScope())).isTrue();
    assertThat(budget.isDefinitelyTerminated()).isTrue();
    assertThat(child.calculateSolverTimeGradient(mockSolverScope())).isEqualTo(-1.0);
  }

  @Test
  void zeroElapsedLimitHasCompleteGradient() {
    var budget = new PartitionTerminationBudget<>(new TimeMillisSpentTermination<>(0), parentPhase);
    assertThat(budget.createChildTermination(parent).calculateSolverTimeGradient(mockSolverScope()))
        .isEqualTo(1.0);
    assertThat(budget.isDefinitelyTerminated()).isTrue();
  }

  @Test
  void invalidSharedScoreGradientRemainsVisibleToCoolingClients() {
    BestScoreTermination<TestdataSolution> termination = mock(BestScoreTermination.class);
    doReturn(InnerScore.fullyAssigned(SimpleScore.ZERO)).when(parentPhase).getStartingScore();
    when(termination.calculatePhaseTimeGradient(parentPhase)).thenReturn(Double.NaN);
    var budget = new PartitionTerminationBudget<>(termination, parentPhase);
    assertThat(budget.createChildTermination(parent).calculateSolverTimeGradient(mockSolverScope()))
        .isNaN();
  }

  @Test
  void supportedStatefulCustomLeavesAreClonedAndReceiveLifecycleIndependently() {
    var original = supportedCustom();
    var first = mock(MockablePhaseTermination.class);
    var second = mock(MockablePhaseTermination.class);
    when(((ChildThreadSupportingTermination<TestdataSolution, SolverScope<TestdataSolution>>)
                original)
            .createChildThreadTermination(parent, ChildThreadType.PART_THREAD))
        .thenReturn(first, second);
    var budget = new PartitionTerminationBudget<>(original, parentPhase);
    var firstChild = budget.createChildTermination(parent);
    var secondChild = budget.createChildTermination(parent);
    var childPhase = phase(mockSolverScope());
    AbstractStepScope<TestdataSolution> step = mock(AbstractStepScope.class);
    firstChild.phaseStarted(childPhase);
    firstChild.stepStarted(step);
    firstChild.stepEnded(step);
    firstChild.bestScoreImproved(step);
    firstChild.phaseEnded(childPhase);
    verify(first).phaseStarted(childPhase);
    verify(first).stepStarted(step);
    verify(first).stepEnded(step);
    verify(first).bestScoreImproved(step);
    verify(first).phaseEnded(childPhase);
    verifyNoInteractions(second);
    when(second.isApplicableTo(childPhase.getClass())).thenReturn(true);
    when(second.calculatePhaseTimeGradient(childPhase)).thenReturn(-1.0);
    assertThat(secondChild.calculatePhaseTimeGradient(childPhase)).isEqualTo(-1.0);
    verify(second, never()).isPhaseTerminated(childPhase);
  }

  @Test
  void builtInIdleAndDiminishedReturnsStateBelongsToEachChild() {
    List<PhaseTermination<TestdataSolution>> definitions =
        List.of(
            new UnimprovedTimeMillisSpentTermination<>(0),
            new UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<>(0, SimpleScore.ONE),
            new DiminishedReturnsTermination<>(0, 0.1));
    for (var definition : definitions) {
      var budget = new PartitionTerminationBudget<>(definition, parentPhase);
      var first = budget.createChildTermination(parent);
      var second = budget.createChildTermination(parent);
      SolverScope<TestdataSolution> childScope = mockSolverScope();
      doReturn(InnerScore.fullyAssigned(SimpleScore.ZERO)).when(childScope).getBestScore();
      when(childScope.getBestSolutionTimeMillis()).thenReturn(0L);
      var searchPhase = spy(new LocalSearchPhaseScope<>(childScope, 0));
      var constructionPhase = spy(new ConstructionHeuristicPhaseScope<>(childScope, 0));
      doReturn(0L).when(searchPhase).getStartingSystemTimeMillis();
      doReturn(0L).when(searchPhase).getPhaseBestSolutionTimeMillis();
      doReturn(0L).when(constructionPhase).getStartingSystemTimeMillis();
      first.solvingStarted(childScope);
      second.solvingStarted(childScope);
      first.phaseStarted(searchPhase);
      second.phaseStarted(constructionPhase);
      AbstractStepScope<TestdataSolution> step = mock(AbstractStepScope.class);
      when(step.getPhaseScope()).thenReturn(searchPhase);
      first.stepStarted(step);
      assertThat(first.isPhaseTerminated(searchPhase)).as(definition.toString()).isTrue();
      assertThat(second.isPhaseTerminated(constructionPhase)).as(definition.toString()).isFalse();
      assertThat(budget.isDefinitelyTerminated()).isFalse();
    }
  }

  @Test
  void solverStepLimitRetainsChildPhaseApplicabilityAndCannotBypassAndBetweenPhases() {
    var definition =
        PhaseTermination.<TestdataSolution>bridge(
            UniversalTermination.and(
                new TimeMillisSpentTermination<>(0), new UnimprovedStepCountTermination<>(5)));
    var budget = new PartitionTerminationBudget<>(definition, parentPhase);
    SolverScope<TestdataSolution> childScope = mockSolverScope();
    var child = budget.createChildTermination(parent);
    assertThat(child.isSolverTerminated(childScope)).isFalse();
    assertThat(budget.isDefinitelyTerminated()).isFalse();
    var searchPhase = phase(childScope);
    AbstractStepScope<TestdataSolution> lastStep = mock(AbstractStepScope.class);
    when(searchPhase.getLastCompletedStepScope()).thenReturn(lastStep);
    when(searchPhase.getBestSolutionStepIndex()).thenReturn(1);
    when(lastStep.getStepIndex()).thenReturn(5);
    assertThat(child.isPhaseTerminated(searchPhase)).isFalse();
    when(lastStep.getStepIndex()).thenReturn(6);
    assertThat(child.isPhaseTerminated(searchPhase)).isTrue();
  }

  @Test
  void unsupportedLeafValidationIncludesClassPathAndRemedy() {
    var unsupported = mock(MockablePhaseTermination.class);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                PartitionTerminationBudget.validate(
                    UniversalTermination.or(new TimeMillisSpentTermination<>(100), unsupported),
                    "phase[2].partitionedSearch.termination"))
        .withMessageContaining(unsupported.getClass().getName())
        .withMessageContaining("phase[2].partitionedSearch.termination.termination[1]")
        .withMessageContaining("ChildThreadSupportingTermination")
        .withMessageContaining("child phase");
  }

  @Test
  void alnsRecognizesSupportedLeavesInsideTheAdapterWithoutUnwrappingParentScopes() {
    var budget =
        new PartitionTerminationBudget<>(
            UniversalTermination.and(
                new TimeMillisSpentTermination<>(100), new MoveCountTermination<>(5)),
            parentPhase);
    var child = budget.createChildTermination(parent);
    var childPhase = phase(mockSolverScope());
    var polling = new AlnsTerminationPolling<>(childPhase, child);
    assertThat(polling.supportedForRepairAttempts()).isTrue();
    assertThat(polling.checkNow()).isFalse();
    var statefulBudget =
        new PartitionTerminationBudget<>(new DiminishedReturnsTermination<>(100, 0.1), parentPhase);
    assertThat(
            new AlnsTerminationPolling<>(childPhase, statefulBudget.createChildTermination(parent))
                .supportedForRepairAttempts())
        .isFalse();
  }

  @SuppressWarnings("unchecked")
  private static AbstractPhaseScope<TestdataSolution> phase(SolverScope<TestdataSolution> solver) {
    AbstractPhaseScope<TestdataSolution> phase = mock(AbstractPhaseScope.class);
    when(phase.getSolverScope()).thenReturn(solver);
    return phase;
  }

  @SuppressWarnings("unchecked")
  private static MockablePhaseTermination<TestdataSolution> supportedCustom() {
    return mock(
        MockablePhaseTermination.class,
        withSettings().extraInterfaces(ChildThreadSupportingTermination.class));
  }
}
