package greycos.solver.core.impl.solver.recaller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.event.SolverEventSupport;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BestSolutionRecallerTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void completedStepPublishesExactlyOnceWithTheSelectedProducer(boolean explicitProducer) {
    SolverScope<TestdataSolution> scope = createSolverScope();
    var original = mock(TestdataSolution.class);
    var improved = mock(TestdataSolution.class);
    scope.setBestSolution(original);
    scope.setBestScore(InnerScore.withUnassignedCount(SimpleScore.ZERO, 1));
    var step = setupConstructionHeuristics(scope);
    when(step.getStepIndex()).thenReturn(7);
    doReturn(InnerScore.fullyAssigned(SimpleScore.ONE)).when(step).getScore();
    when(step.cloneWorkingSolution()).thenReturn(improved);
    when(scope.getScoreDirector().getSolutionDescriptor().getScore(improved))
        .thenReturn(SimpleScore.ONE);
    var ordinaryId = mock(EventProducerId.class);
    var suppliedId = mock(EventProducerId.class);
    when(step.getPhaseScope().getPhaseId()).thenReturn(ordinaryId);
    SolverEventSupport<TestdataSolution> events = mock(SolverEventSupport.class);
    var recaller = new BestSolutionRecaller<TestdataSolution>();
    recaller.setSolverEventSupport(events);

    if (explicitProducer) recaller.processWorkingSolutionDuringStep(step, suppliedId);
    else recaller.processWorkingSolutionDuringStep(step);

    assertThat(scope.getBestSolution()).isSameAs(improved);
    assertThat(scope.getBestScore()).isEqualTo(InnerScore.fullyAssigned(SimpleScore.ONE));
    assertThat(scope.<SimpleScore>getStartingInitializedScore()).isEqualTo(SimpleScore.ONE);
    assertThat(scope.getBestSolutionTimeMillis()).isNotNull();
    verify(step).setBestScoreImproved(true);
    verify(step.getPhaseScope()).setBestSolutionStepIndex(7);
    verify(events)
        .fireBestSolutionChanged(scope, explicitProducer ? suppliedId : ordinaryId, improved);
    verifyNoMoreInteractions(events);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0})
  void explicitProducerRetainsTheUnimprovedBestAssertion(int stepScore) {
    SolverScope<TestdataSolution> scope = createSolverScope();
    var original = mock(TestdataSolution.class);
    scope.setBestSolution(original);
    scope.setBestScore(InnerScore.fullyAssigned(SimpleScore.ZERO));
    var step = setupConstructionHeuristics(scope);
    doReturn(InnerScore.fullyAssigned(SimpleScore.of(stepScore))).when(step).getScore();
    SolverEventSupport<TestdataSolution> events = mock(SolverEventSupport.class);
    var recaller = new BestSolutionRecaller<TestdataSolution>();
    recaller.setSolverEventSupport(events);
    recaller.assertBestScoreIsUnmodified = true;

    recaller.processWorkingSolutionDuringStep(step, mock(EventProducerId.class));

    assertThat(scope.getBestSolution()).isSameAs(original);
    verify(scope.getScoreDirector()).assertScoreFromScratch(original);
    verify(step).setBestScoreImproved(false);
    verifyNoInteractions(events);
  }

  @Test
  void explicitProducerCannotPublishAStructurallyFlawedImprovement() {
    SolverScope<TestdataSolution> scope = createSolverScope();
    var original = mock(TestdataSolution.class);
    var flawed = mock(TestdataSolution.class);
    scope.setBestSolution(original);
    scope.setBestScore(InnerScore.fullyAssigned(new SimpleScore(-2, 0)));
    var step = setupConstructionHeuristics(scope);
    var flawedScore = new SimpleScore(-1, 0);
    doReturn(InnerScore.fullyAssigned(flawedScore)).when(step).getScore();
    when(step.cloneWorkingSolution()).thenReturn(flawed);
    when(scope.getScoreDirector().getSolutionDescriptor().getScore(flawed)).thenReturn(flawedScore);
    SolverEventSupport<TestdataSolution> events = mock(SolverEventSupport.class);
    var recaller = new BestSolutionRecaller<TestdataSolution>();
    recaller.setSolverEventSupport(events);

    assertThatThrownBy(
            () -> recaller.processWorkingSolutionDuringStep(step, mock(EventProducerId.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("structurally flawed");

    assertThat(scope.getBestSolution()).isSameAs(original);
    verifyNoInteractions(events);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void solvingStartedReplacesBestWithRefreshedCloneEvenWhenScoreIsEqual(int newScoreValue) {
    SolverScope<TestdataSolution> solverScope = createSolverScope();
    var publishedBest = TestdataSolution.generateSolution();
    publishedBest.setScore(SimpleScore.ZERO);
    solverScope.setBestSolution(publishedBest);
    var refreshedBest = TestdataSolution.generateSolution();
    var score = SimpleScore.of(newScoreValue);
    refreshedBest.setScore(score);
    var director = solverScope.getScoreDirector();
    doReturn(InnerScore.fullyAssigned(score)).when(director).calculateScore();
    when(director.cloneWorkingSolution()).thenReturn(refreshedBest);

    BestSolutionRecaller<TestdataSolution> recaller = createBestSolutionRecaller();
    recaller.solvingStarted(solverScope);

    assertThat(solverScope.getBestSolution()).isSameAs(refreshedBest);
    assertThat(solverScope.getBestScore()).isEqualTo(InnerScore.fullyAssigned(score));
    assertThat(publishedBest.getScore()).isEqualTo(SimpleScore.ZERO);
  }

  private static <Solution_> SolverScope<Solution_> createSolverScope() {
    var solverScope = new SolverScope<Solution_>();
    InnerScoreDirector<Solution_, ?> scoreDirector = mock(InnerScoreDirector.class);
    SolutionDescriptor<Solution_> solutionDescriptor = mock(SolutionDescriptor.class);
    when(scoreDirector.getSolutionDescriptor()).thenReturn(solutionDescriptor);
    solverScope.setScoreDirector(scoreDirector);
    return solverScope;
  }

  private static <Solution_> ConstructionHeuristicStepScope<Solution_> setupConstructionHeuristics(
      SolverScope<Solution_> solverScope) {
    ConstructionHeuristicPhaseScope<Solution_> phaseScope =
        mock(ConstructionHeuristicPhaseScope.class);
    when(phaseScope.getSolverScope()).thenReturn(solverScope);
    ConstructionHeuristicStepScope<Solution_> stepScope =
        mock(ConstructionHeuristicStepScope.class);
    when(stepScope.getPhaseScope()).thenReturn(phaseScope);
    when(stepScope.getScoreDirector()).thenReturn(solverScope.getScoreDirector());
    return stepScope;
  }

  private static <Solution_> BestSolutionRecaller<Solution_> createBestSolutionRecaller() {
    var recaller = new BestSolutionRecaller<Solution_>();
    recaller.setSolverEventSupport(mock(SolverEventSupport.class));
    return recaller;
  }

  @Test
  void unimprovedUninitializedProcessWorkingSolutionDuringStep() {
    var originalBestScore =
        new InnerScoreWithSolution<>(
            mock(TestdataSolution.class), InnerScore.withUnassignedCount(SimpleScore.of(-300), 1));
    var stepScore =
        new InnerScoreWithSolution<>(
            mock(TestdataSolution.class), InnerScore.withUnassignedCount(SimpleScore.ZERO, 2));
    doProcessWorkingSolutionDuringStep(originalBestScore, stepScore, false);
  }

  @Test
  void unimprovedInitializedProcessWorkingSolutionDuringStep() {
    var originalBestScore = SimpleScore.of(0);
    var stepScore = SimpleScore.of(-1);
    doProcessWorkingSolutionDuringStep(originalBestScore, stepScore, false);
  }

  @Test
  void improvedUninitializedProcessWorkingSolutionDuringStep() {
    var originalBestScore =
        new InnerScoreWithSolution<>(
            mock(TestdataSolution.class), InnerScore.withUnassignedCount(SimpleScore.ZERO, 2));
    var stepScore =
        new InnerScoreWithSolution<>(
            mock(TestdataSolution.class), InnerScore.withUnassignedCount(SimpleScore.ZERO, 1));
    doProcessWorkingSolutionDuringStep(originalBestScore, stepScore, true);
  }

  @Test
  void improvedInitializedProcessWorkingSolutionDuringStep() {
    var originalBestScore = SimpleScore.of(-1);
    var stepScore = SimpleScore.of(0);
    doProcessWorkingSolutionDuringStep(originalBestScore, stepScore, true);
  }

  protected void doProcessWorkingSolutionDuringStep(
      InnerScoreWithSolution<TestdataSolution, SimpleScore> originalBestScore,
      InnerScoreWithSolution<TestdataSolution, SimpleScore> stepScore,
      boolean stepImprovesBestSolution) {
    SolverScope<TestdataSolution> solverScope = createSolverScope();
    var originalBestSolution = originalBestScore.solution;
    var scoreDirector = solverScope.getScoreDirector();
    when(scoreDirector.getSolutionDescriptor().getScore(originalBestSolution))
        .thenReturn(originalBestScore.innerScore.raw());
    when(scoreDirector.getWorkingInitScore())
        .thenReturn(-originalBestScore.innerScore.unassignedCount());
    solverScope.setBestSolution(originalBestSolution);
    solverScope.setBestScore(originalBestScore.innerScore);

    var stepScope = setupConstructionHeuristics(solverScope);
    var stepSolution = stepScore.solution;
    when(scoreDirector.getSolutionDescriptor().getScore(stepSolution))
        .thenReturn(stepScore.innerScore.raw());
    when(scoreDirector.getWorkingInitScore()).thenReturn(-stepScore.innerScore.unassignedCount());
    doReturn(stepScore.innerScore).when(stepScope).getScore();
    when(stepScope.cloneWorkingSolution()).thenReturn(stepSolution);

    BestSolutionRecaller<TestdataSolution> recaller = createBestSolutionRecaller();
    recaller.processWorkingSolutionDuringStep(stepScope);
    if (stepImprovesBestSolution) {
      assertThat(solverScope.getBestSolution()).isEqualTo(stepSolution);
      assertThat(solverScope.getBestScore()).isEqualTo(stepScore.innerScore);
    } else {
      assertThat(solverScope.getBestSolution()).isEqualTo(originalBestSolution);
      assertThat(solverScope.getBestScore()).isEqualTo(originalBestScore.innerScore);
    }
  }

  record InnerScoreWithSolution<Solution_, Score_ extends Score<Score_>>(
      Solution_ solution, InnerScore<Score_> innerScore) {}

  protected void doProcessWorkingSolutionDuringStep(
      SimpleScore originalBestScore, SimpleScore stepScore, boolean stepImprovesBestSolution) {
    var originalBestInnerScore =
        new InnerScoreWithSolution<TestdataSolution, SimpleScore>(
            mock(TestdataSolution.class), InnerScore.fullyAssigned(originalBestScore));
    var originalStepInnerScore =
        new InnerScoreWithSolution<TestdataSolution, SimpleScore>(
            mock(TestdataSolution.class), InnerScore.fullyAssigned(stepScore));
    doProcessWorkingSolutionDuringStep(
        originalBestInnerScore, originalStepInnerScore, stepImprovesBestSolution);
  }

  @Test
  void unimprovedUninitializedProcessWorkingSolutionDuringMove() {
    var bestScore =
        new InnerScoreWithSolution<>(
            mock(TestdataSolution.class), InnerScore.fullyAssigned(SimpleScore.of(-10)));
    var moveScore =
        new InnerScoreWithSolution<>(
            mock(TestdataSolution.class), InnerScore.withUnassignedCount(SimpleScore.of(-1), 1));
    doProcessWorkingSolutionDuringMove(bestScore, moveScore, false);
  }

  @Test
  void unimprovedInitializedProcessWorkingSolutionDuringMove() {
    var bestScore = SimpleScore.of(0);
    var moveScore = SimpleScore.of(-1);
    doProcessWorkingSolutionDuringMove(bestScore, moveScore, false);
  }

  @Test
  void improvedUninitializedProcessWorkingSolutionDuringMove() {
    var bestScore =
        new InnerScoreWithSolution<>(
            mock(TestdataSolution.class), InnerScore.withUnassignedCount(SimpleScore.ZERO, 1));
    var moveScore =
        new InnerScoreWithSolution<>(
            mock(TestdataSolution.class), InnerScore.fullyAssigned(SimpleScore.of(-2)));
    doProcessWorkingSolutionDuringMove(bestScore, moveScore, true);
  }

  @Test
  void improvedInitializedProcessWorkingSolutionDuringMove() {
    var bestScore = SimpleScore.of(-2);
    var moveScore = SimpleScore.of(-1);
    doProcessWorkingSolutionDuringMove(bestScore, moveScore, true);
  }

  protected void doProcessWorkingSolutionDuringMove(
      SimpleScore originalBestScore, SimpleScore moveScore, boolean moveImprovesBestSolution) {
    var originalBestInnerScore =
        new InnerScoreWithSolution<TestdataSolution, SimpleScore>(
            mock(TestdataSolution.class), InnerScore.fullyAssigned(originalBestScore));
    var originalMoveInnerScore =
        new InnerScoreWithSolution<TestdataSolution, SimpleScore>(
            mock(TestdataSolution.class), InnerScore.fullyAssigned(moveScore));
    doProcessWorkingSolutionDuringMove(
        originalBestInnerScore, originalMoveInnerScore, moveImprovesBestSolution);
  }

  protected void doProcessWorkingSolutionDuringMove(
      InnerScoreWithSolution<TestdataSolution, SimpleScore> originalBestScore,
      InnerScoreWithSolution<TestdataSolution, SimpleScore> moveScore,
      boolean moveImprovesBestSolution) {
    SolverScope<TestdataSolution> solverScope = createSolverScope();
    var originalBestSolution = originalBestScore.solution;
    when(solverScope.getScoreDirector().getSolutionDescriptor().getScore(originalBestSolution))
        .thenReturn(originalBestScore.innerScore.raw());
    solverScope.setBestSolution(originalBestSolution);
    solverScope.setBestScore(originalBestScore.innerScore);

    var stepScope = setupConstructionHeuristics(solverScope);

    var moveSolution = moveScore.solution;
    when(solverScope.getScoreDirector().getSolutionDescriptor().getScore(moveSolution))
        .thenReturn(moveScore.innerScore.raw());
    when(solverScope.getScoreDirector().cloneWorkingSolution()).thenReturn(moveSolution);

    BestSolutionRecaller<TestdataSolution> recaller = createBestSolutionRecaller();
    recaller.processWorkingSolutionDuringMove(moveScore.innerScore, stepScope);
    if (moveImprovesBestSolution) {
      assertThat(solverScope.getBestSolution()).isEqualTo(moveSolution);
      assertThat(solverScope.getBestScore()).isEqualTo(moveScore.innerScore);
    } else {
      assertThat(solverScope.getBestSolution()).isEqualTo(originalBestSolution);
      assertThat(solverScope.getBestScore()).isEqualTo(originalBestScore.innerScore);
    }
  }
}
