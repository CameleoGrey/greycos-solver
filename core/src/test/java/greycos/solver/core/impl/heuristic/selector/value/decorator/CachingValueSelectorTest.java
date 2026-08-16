package greycos.solver.core.impl.heuristic.selector.value.decorator;

import static greycos.solver.core.testutil.PlannerAssert.assertAllCodesOfValueSelector;
import static greycos.solver.core.testutil.PlannerAssert.verifyPhaseLifecycle;
import static greycos.solver.core.testutil.PlannerTestUtils.mockSolverScope;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;

class CachingValueSelectorTest {

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
    IterableValueSelector childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(
            TestdataEntity.class,
            "value",
            new TestdataValue("v1"),
            new TestdataValue("v2"),
            new TestdataValue("v3"));

    IterableValueSelector valueSelector =
        new CachingValueSelector(childValueSelector, cacheType, false);
    verify(childValueSelector, times(1)).isNeverEnding();

    SolverScope solverScope = mockSolverScope();
    InnerScoreDirector scoreDirector = mock(InnerScoreDirector.class);
    when(scoreDirector.getWorkingEntityListRevision()).thenReturn(0L);
    when(scoreDirector.isWorkingEntityListDirty(anyLong())).thenReturn(false);
    when(solverScope.getScoreDirector()).thenReturn(scoreDirector);
    valueSelector.solvingStarted(solverScope);

    AbstractPhaseScope phaseScopeA = mock(AbstractPhaseScope.class);
    when(phaseScopeA.getSolverScope()).thenReturn(solverScope);
    valueSelector.phaseStarted(phaseScopeA);

    AbstractStepScope stepScopeA1 = mock(AbstractStepScope.class);
    when(stepScopeA1.getPhaseScope()).thenReturn(phaseScopeA);
    when(stepScopeA1.getScoreDirector()).thenReturn(scoreDirector);
    valueSelector.stepStarted(stepScopeA1);
    assertAllCodesOfValueSelector(valueSelector, "v1", "v2", "v3");
    valueSelector.stepEnded(stepScopeA1);

    AbstractStepScope stepScopeA2 = mock(AbstractStepScope.class);
    when(stepScopeA2.getPhaseScope()).thenReturn(phaseScopeA);
    when(stepScopeA2.getScoreDirector()).thenReturn(scoreDirector);
    valueSelector.stepStarted(stepScopeA2);
    assertAllCodesOfValueSelector(valueSelector, "v1", "v2", "v3");
    valueSelector.stepEnded(stepScopeA2);

    valueSelector.phaseEnded(phaseScopeA);

    AbstractPhaseScope phaseScopeB = mock(AbstractPhaseScope.class);
    when(phaseScopeB.getSolverScope()).thenReturn(solverScope);
    valueSelector.phaseStarted(phaseScopeB);

    AbstractStepScope stepScopeB1 = mock(AbstractStepScope.class);
    when(stepScopeB1.getPhaseScope()).thenReturn(phaseScopeB);
    when(stepScopeB1.getScoreDirector()).thenReturn(scoreDirector);
    valueSelector.stepStarted(stepScopeB1);
    assertAllCodesOfValueSelector(valueSelector, "v1", "v2", "v3");
    valueSelector.stepEnded(stepScopeB1);

    AbstractStepScope stepScopeB2 = mock(AbstractStepScope.class);
    when(stepScopeB2.getPhaseScope()).thenReturn(phaseScopeB);
    when(stepScopeB2.getScoreDirector()).thenReturn(scoreDirector);
    valueSelector.stepStarted(stepScopeB2);
    assertAllCodesOfValueSelector(valueSelector, "v1", "v2", "v3");
    valueSelector.stepEnded(stepScopeB2);

    AbstractStepScope stepScopeB3 = mock(AbstractStepScope.class);
    when(stepScopeB3.getPhaseScope()).thenReturn(phaseScopeB);
    when(stepScopeB3.getScoreDirector()).thenReturn(scoreDirector);
    valueSelector.stepStarted(stepScopeB3);
    assertAllCodesOfValueSelector(valueSelector, "v1", "v2", "v3");
    valueSelector.stepEnded(stepScopeB3);

    valueSelector.phaseEnded(phaseScopeB);

    valueSelector.solvingEnded(solverScope);

    verifyPhaseLifecycle(childValueSelector, 1, 2, 5);
    verify(childValueSelector, times(timesCalled)).iterator();
    verify(childValueSelector, times(timesCalled)).getSize();
  }
}
