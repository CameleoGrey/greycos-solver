package ai.greycos.solver.core.impl.heuristic.selector.entity.decorator;

import static ai.greycos.solver.core.testutil.PlannerAssert.assertAllCodesOfEntitySelector;
import static ai.greycos.solver.core.testutil.PlannerAssert.verifyPhaseLifecycle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.common.iterator.CachedListRandomIterator;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.testdomain.TestdataEntity;

import org.junit.jupiter.api.Test;

class CachingEntitySelectorTest {

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
    EntitySelector childEntitySelector =
        SelectorTestUtils.mockEntitySelector(
            TestdataEntity.class,
            new TestdataEntity("e1"),
            new TestdataEntity("e2"),
            new TestdataEntity("e3"));

    CachingEntitySelector entitySelector =
        new CachingEntitySelector(childEntitySelector, cacheType, false);
    verify(childEntitySelector, times(1)).isNeverEnding();

    InnerScoreDirector scoreDirector = mock(InnerScoreDirector.class);
    when(scoreDirector.getWorkingEntityListRevision()).thenReturn(0L);
    when(scoreDirector.isWorkingEntityListDirty(anyLong())).thenReturn(false);
    SolverScope solverScope = SelectorTestUtils.solvingStarted(entitySelector, scoreDirector);

    AbstractPhaseScope phaseScopeA = mock(AbstractPhaseScope.class);
    when(phaseScopeA.getSolverScope()).thenReturn(solverScope);
    when(phaseScopeA.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.phaseStarted(phaseScopeA);

    AbstractStepScope stepScopeA1 = mock(AbstractStepScope.class);
    when(stepScopeA1.getPhaseScope()).thenReturn(phaseScopeA);
    when(stepScopeA1.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.stepStarted(stepScopeA1);
    assertAllCodesOfEntitySelector(entitySelector, "e1", "e2", "e3");
    entitySelector.stepEnded(stepScopeA1);

    AbstractStepScope stepScopeA2 = mock(AbstractStepScope.class);
    when(stepScopeA2.getPhaseScope()).thenReturn(phaseScopeA);
    when(stepScopeA2.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.stepStarted(stepScopeA2);
    assertAllCodesOfEntitySelector(entitySelector, "e1", "e2", "e3");
    entitySelector.stepEnded(stepScopeA2);

    entitySelector.phaseEnded(phaseScopeA);

    AbstractPhaseScope phaseScopeB = mock(AbstractPhaseScope.class);
    when(phaseScopeB.getSolverScope()).thenReturn(solverScope);
    when(phaseScopeB.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.phaseStarted(phaseScopeB);

    AbstractStepScope stepScopeB1 = mock(AbstractStepScope.class);
    when(stepScopeB1.getPhaseScope()).thenReturn(phaseScopeB);
    when(stepScopeB1.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.stepStarted(stepScopeB1);
    assertAllCodesOfEntitySelector(entitySelector, "e1", "e2", "e3");
    entitySelector.stepEnded(stepScopeB1);

    AbstractStepScope stepScopeB2 = mock(AbstractStepScope.class);
    when(stepScopeB2.getPhaseScope()).thenReturn(phaseScopeB);
    when(stepScopeB2.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.stepStarted(stepScopeB2);
    assertAllCodesOfEntitySelector(entitySelector, "e1", "e2", "e3");
    entitySelector.stepEnded(stepScopeB2);

    AbstractStepScope stepScopeB3 = mock(AbstractStepScope.class);
    when(stepScopeB3.getPhaseScope()).thenReturn(phaseScopeB);
    when(stepScopeB3.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.stepStarted(stepScopeB3);
    assertAllCodesOfEntitySelector(entitySelector, "e1", "e2", "e3");
    entitySelector.stepEnded(stepScopeB3);

    entitySelector.phaseEnded(phaseScopeB);

    entitySelector.solvingEnded(solverScope);

    verifyPhaseLifecycle(childEntitySelector, 1, 2, 5);
    verify(childEntitySelector, times(timesCalled)).iterator();
    verify(childEntitySelector, times(timesCalled)).getSize();
  }

  @Test
  void listIteratorWithRandomSelection() {
    EntitySelector childEntitySelector = SelectorTestUtils.mockEntitySelector(TestdataEntity.class);
    CachingEntitySelector cachingEntitySelector =
        new CachingEntitySelector(childEntitySelector, SelectionCacheType.PHASE, true);
    assertThatIllegalStateException().isThrownBy(cachingEntitySelector::listIterator);
  }

  @Test
  void indexedListIteratorWithRandomSelection() {
    EntitySelector childEntitySelector = SelectorTestUtils.mockEntitySelector(TestdataEntity.class);
    CachingEntitySelector cachingEntitySelector =
        new CachingEntitySelector(childEntitySelector, SelectionCacheType.PHASE, true);
    assertThatIllegalStateException().isThrownBy(() -> cachingEntitySelector.listIterator(0));
  }

  @Test
  void isNeverEnding() {
    EntitySelector childEntitySelector = SelectorTestUtils.mockEntitySelector(TestdataEntity.class);
    CachingEntitySelector cachingEntitySelector =
        new CachingEntitySelector(childEntitySelector, SelectionCacheType.PHASE, true);
    assertThat(cachingEntitySelector.isNeverEnding()).isTrue();
    cachingEntitySelector =
        new CachingEntitySelector(childEntitySelector, SelectionCacheType.PHASE, false);
    assertThat(cachingEntitySelector.isNeverEnding()).isFalse();
  }

  @Test
  void iterator() {
    EntitySelector childEntitySelector = SelectorTestUtils.mockEntitySelector(TestdataEntity.class);
    when(childEntitySelector.getSize()).thenReturn(1L);

    CachingEntitySelector cachingEntitySelector =
        new CachingEntitySelector(childEntitySelector, SelectionCacheType.PHASE, true);
    cachingEntitySelector.constructCache(null);
    assertThat(cachingEntitySelector.iterator()).isInstanceOf(CachedListRandomIterator.class);

    cachingEntitySelector =
        new CachingEntitySelector(childEntitySelector, SelectionCacheType.PHASE, false);
    cachingEntitySelector.constructCache(null);
    assertThat(cachingEntitySelector.iterator()).isNotInstanceOf(CachedListRandomIterator.class);
  }
}
