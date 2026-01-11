package ai.greycos.solver.core.impl.heuristic.selector.move.decorator;

import static ai.greycos.solver.core.testutil.PlannerAssert.assertAllCodesOfMoveSelector;
import static ai.greycos.solver.core.testutil.PlannerAssert.assertCodesOfNeverEndingMoveSelector;
import static ai.greycos.solver.core.testutil.PlannerAssert.verifyPhaseLifecycle;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.impl.heuristic.move.DummyMove;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.testutil.PlannerTestUtils;
import ai.greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;

class CachingMoveSelectorTest {

  @Test
  void originalSelectionCacheTypeSolver() {
    runOriginalSelection(SelectionCacheType.SOLVER, 1);
  }

  @Test
  void originalSelectionCacheTypePhase() {
    runOriginalSelection(SelectionCacheType.PHASE, 2);
  }

  @Test
  void originalSelectionCacheTypeStep() {
    runOriginalSelection(SelectionCacheType.STEP, 5);
  }

  public void runOriginalSelection(SelectionCacheType cacheType, int timesCalled) {
    MoveSelector childMoveSelector =
        SelectorTestUtils.mockMoveSelector(
            new DummyMove("a1"), new DummyMove("a2"), new DummyMove("a3"));

    CachingMoveSelector moveSelector = new CachingMoveSelector(childMoveSelector, cacheType, false);
    verify(childMoveSelector, times(1)).isNeverEnding();

    InnerScoreDirector scoreDirector = mock(InnerScoreDirector.class);
    when(scoreDirector.getWorkingEntityListRevision()).thenReturn(0L);
    when(scoreDirector.isWorkingEntityListDirty(anyLong())).thenReturn(false);
    SolverScope solverScope = SelectorTestUtils.solvingStarted(moveSelector, scoreDirector);

    AbstractPhaseScope phaseScopeA = mock(AbstractPhaseScope.class);
    when(phaseScopeA.getSolverScope()).thenReturn(solverScope);
    when(phaseScopeA.getScoreDirector()).thenReturn(scoreDirector);
    moveSelector.phaseStarted(phaseScopeA);

    AbstractStepScope stepScopeA1 = mock(AbstractStepScope.class);
    when(stepScopeA1.getPhaseScope()).thenReturn(phaseScopeA);
    when(stepScopeA1.getScoreDirector()).thenReturn(scoreDirector);
    moveSelector.stepStarted(stepScopeA1);
    assertAllCodesOfMoveSelector(moveSelector, "a1", "a2", "a3");
    moveSelector.stepEnded(stepScopeA1);

    AbstractStepScope stepScopeA2 = mock(AbstractStepScope.class);
    when(stepScopeA2.getPhaseScope()).thenReturn(phaseScopeA);
    when(stepScopeA2.getScoreDirector()).thenReturn(scoreDirector);
    moveSelector.stepStarted(stepScopeA2);
    assertAllCodesOfMoveSelector(moveSelector, "a1", "a2", "a3");
    moveSelector.stepEnded(stepScopeA2);

    moveSelector.phaseEnded(phaseScopeA);

    AbstractPhaseScope phaseScopeB = mock(AbstractPhaseScope.class);
    when(phaseScopeB.getSolverScope()).thenReturn(solverScope);
    when(phaseScopeB.getScoreDirector()).thenReturn(scoreDirector);
    moveSelector.phaseStarted(phaseScopeB);

    AbstractStepScope stepScopeB1 = mock(AbstractStepScope.class);
    when(stepScopeB1.getPhaseScope()).thenReturn(phaseScopeB);
    when(stepScopeB1.getScoreDirector()).thenReturn(scoreDirector);
    moveSelector.stepStarted(stepScopeB1);
    assertAllCodesOfMoveSelector(moveSelector, "a1", "a2", "a3");
    moveSelector.stepEnded(stepScopeB1);

    AbstractStepScope stepScopeB2 = mock(AbstractStepScope.class);
    when(stepScopeB2.getPhaseScope()).thenReturn(phaseScopeB);
    when(stepScopeB2.getScoreDirector()).thenReturn(scoreDirector);
    moveSelector.stepStarted(stepScopeB2);
    assertAllCodesOfMoveSelector(moveSelector, "a1", "a2", "a3");
    moveSelector.stepEnded(stepScopeB2);

    AbstractStepScope stepScopeB3 = mock(AbstractStepScope.class);
    when(stepScopeB3.getPhaseScope()).thenReturn(phaseScopeB);
    when(stepScopeB3.getScoreDirector()).thenReturn(scoreDirector);
    moveSelector.stepStarted(stepScopeB3);
    assertAllCodesOfMoveSelector(moveSelector, "a1", "a2", "a3");
    moveSelector.stepEnded(stepScopeB3);

    moveSelector.phaseEnded(phaseScopeB);

    moveSelector.solvingEnded(solverScope);

    verifyPhaseLifecycle(childMoveSelector, 1, 2, 5);
    verify(childMoveSelector, times(timesCalled)).iterator();
    verify(childMoveSelector, times(timesCalled)).getSize();
  }

  @Test
  void randomSelectionCacheTypeSolver() {
    runRandomSelection(SelectionCacheType.SOLVER, 1);
  }

  @Test
  void randomSelectionCacheTypePhase() {
    runRandomSelection(SelectionCacheType.PHASE, 2);
  }

  @Test
  void randomSelectionCacheTypeStep() {
    runRandomSelection(SelectionCacheType.STEP, 3);
  }

  public void runRandomSelection(SelectionCacheType cacheType, int timesCalled) {
    MoveSelector childMoveSelector =
        SelectorTestUtils.mockMoveSelector(
            new DummyMove("a1"), new DummyMove("a2"), new DummyMove("a3"));

    CachingMoveSelector moveSelector = new CachingMoveSelector(childMoveSelector, cacheType, true);
    verify(childMoveSelector, times(1)).isNeverEnding();

    TestRandom workingRandom = new TestRandom(1, 0, 2);

    InnerScoreDirector scoreDirector = mock(InnerScoreDirector.class);
    when(scoreDirector.getWorkingEntityListRevision()).thenReturn(0L);
    when(scoreDirector.isWorkingEntityListDirty(anyLong())).thenReturn(false);
    SolverScope solverScope =
        SelectorTestUtils.solvingStarted(moveSelector, scoreDirector, workingRandom);

    AbstractPhaseScope phaseScopeA = PlannerTestUtils.delegatingPhaseScope(solverScope);
    moveSelector.phaseStarted(phaseScopeA);

    AbstractStepScope stepScopeA1 = PlannerTestUtils.delegatingStepScope(phaseScopeA);
    moveSelector.stepStarted(stepScopeA1);
    assertCodesOfNeverEndingMoveSelector(moveSelector, 3L, "a2", "a1", "a3");
    moveSelector.stepEnded(stepScopeA1);

    AbstractStepScope stepScopeA2 = PlannerTestUtils.delegatingStepScope(phaseScopeA);
    moveSelector.stepStarted(stepScopeA2);
    workingRandom.reset(2, 0, 1);
    assertCodesOfNeverEndingMoveSelector(moveSelector, 3L, "a3", "a1", "a2");
    moveSelector.stepEnded(stepScopeA2);

    moveSelector.phaseEnded(phaseScopeA);

    AbstractPhaseScope phaseScopeB = PlannerTestUtils.delegatingPhaseScope(solverScope);
    moveSelector.phaseStarted(phaseScopeB);

    AbstractStepScope stepScopeB1 = PlannerTestUtils.delegatingStepScope(phaseScopeB);
    moveSelector.stepStarted(stepScopeB1);
    workingRandom.reset(1, 2, 0);
    assertCodesOfNeverEndingMoveSelector(moveSelector, 3L, "a2", "a3", "a1");
    moveSelector.stepEnded(stepScopeB1);

    moveSelector.phaseEnded(phaseScopeB);

    moveSelector.solvingEnded(solverScope);

    verifyPhaseLifecycle(childMoveSelector, 1, 2, 3);
    verify(childMoveSelector, times(timesCalled)).iterator();
    verify(childMoveSelector, times(timesCalled)).getSize();
  }
}
