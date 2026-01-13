package ai.greycos.solver.core.impl.heuristic.selector.entity.decorator;

import static ai.greycos.solver.core.testutil.PlannerAssert.assertAllCodesOfEntitySelector;
import static ai.greycos.solver.core.testutil.PlannerAssert.assertAllCodesOfOrderedEntitySelector;
import static ai.greycos.solver.core.testutil.PlannerAssert.verifyPhaseLifecycle;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class FilteringEntitySelectorTest {

  @Test
  void filterCacheTypeSolver() {
    filter(SelectionCacheType.SOLVER, 1, SelectionOrder.RANDOM);
  }

  @Test
  void filterCacheTypePhase() {
    filter(SelectionCacheType.PHASE, 2, SelectionOrder.RANDOM);
  }

  @Test
  void filterCacheTypeStep() {
    filter(SelectionCacheType.STEP, 5, SelectionOrder.RANDOM);
  }

  @Test
  void filterCacheTypeJustInTime() {
    filter(SelectionCacheType.JUST_IN_TIME, 5, SelectionOrder.RANDOM);
  }

  @Test
  void filterOrderedCacheTypeSolver() {
    filter(SelectionCacheType.JUST_IN_TIME, 5, SelectionOrder.ORIGINAL);
  }

  private void verifyStep(
      EntitySelector entitySelector,
      SelectionCacheType cacheType,
      AbstractPhaseScope phaseScope,
      AbstractStepScope stepScope,
      SelectionOrder selectionOrder,
      InnerScoreDirector scoreDirector) {
    when(stepScope.getPhaseScope()).thenReturn(phaseScope);
    when(stepScope.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.stepStarted(stepScope);
    if (selectionOrder == SelectionOrder.RANDOM) {
      assertAllCodesOfEntitySelector(
          entitySelector, (cacheType.isNotCached() ? 4L : 3L), "e1", "e2", "e4");
    }
    if (selectionOrder == SelectionOrder.ORIGINAL) {
      assertAllCodesOfOrderedEntitySelector(
          entitySelector, (cacheType.isNotCached() ? 4L : 3L), "e1", "e2", "e4");
    }
    entitySelector.stepEnded(stepScope);
  }

  private void filter(
      SelectionCacheType cacheType, int timesCalled, SelectionOrder selectionOrder) {
    EntitySelector childEntitySelector =
        SelectorTestUtils.mockEntitySelector(
            TestdataEntity.class,
            new TestdataEntity("e1"),
            new TestdataEntity("e2"),
            new TestdataEntity("e3"),
            new TestdataEntity("e4"));

    SelectionFilter<TestdataSolution, TestdataEntity> filter =
        (scoreDirector, entity) -> !entity.getCode().equals("e3");
    EntitySelector entitySelector =
        FilteringEntitySelector.of(childEntitySelector, (SelectionFilter) filter);
    if (cacheType.isCached()) {
      entitySelector = new CachingEntitySelector(entitySelector, cacheType, false);
    }

    InnerScoreDirector scoreDirector = mock(InnerScoreDirector.class);
    when(scoreDirector.getWorkingEntityListRevision()).thenReturn(0L);
    when(scoreDirector.isWorkingEntityListDirty(anyLong())).thenReturn(false);
    SolverScope solverScope = SelectorTestUtils.solvingStarted(entitySelector, scoreDirector);

    AbstractPhaseScope phaseScopeA = mock(AbstractPhaseScope.class);
    when(phaseScopeA.getSolverScope()).thenReturn(solverScope);
    when(phaseScopeA.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.phaseStarted(phaseScopeA);

    AbstractStepScope stepScopeA1 = mock(AbstractStepScope.class);
    verifyStep(entitySelector, cacheType, phaseScopeA, stepScopeA1, selectionOrder, scoreDirector);

    AbstractStepScope stepScopeA2 = mock(AbstractStepScope.class);
    verifyStep(entitySelector, cacheType, phaseScopeA, stepScopeA2, selectionOrder, scoreDirector);

    entitySelector.phaseEnded(phaseScopeA);

    AbstractPhaseScope phaseScopeB = mock(AbstractPhaseScope.class);
    when(phaseScopeB.getSolverScope()).thenReturn(solverScope);
    when(phaseScopeB.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.phaseStarted(phaseScopeB);

    AbstractStepScope stepScopeB1 = mock(AbstractStepScope.class);
    verifyStep(entitySelector, cacheType, phaseScopeB, stepScopeB1, selectionOrder, scoreDirector);

    AbstractStepScope stepScopeB2 = mock(AbstractStepScope.class);
    verifyStep(entitySelector, cacheType, phaseScopeB, stepScopeB2, selectionOrder, scoreDirector);

    AbstractStepScope stepScopeB3 = mock(AbstractStepScope.class);
    verifyStep(entitySelector, cacheType, phaseScopeB, stepScopeB3, selectionOrder, scoreDirector);

    entitySelector.phaseEnded(phaseScopeB);

    entitySelector.solvingEnded(solverScope);

    verifyPhaseLifecycle(childEntitySelector, 1, 2, 5);
    if (selectionOrder == SelectionOrder.RANDOM) {
      verify(childEntitySelector, times(timesCalled)).iterator();
      verify(childEntitySelector, times(timesCalled)).getSize();
    }
    if (selectionOrder == SelectionOrder.ORIGINAL) {
      verify(childEntitySelector, times(timesCalled)).listIterator();
      verify(childEntitySelector, times(timesCalled)).getSize();
    }
  }
}
