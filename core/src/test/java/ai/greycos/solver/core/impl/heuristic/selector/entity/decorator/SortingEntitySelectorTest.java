package ai.greycos.solver.core.impl.heuristic.selector.entity.decorator;

import static ai.greycos.solver.core.testutil.PlannerAssert.assertAllCodesOfEntitySelector;
import static ai.greycos.solver.core.testutil.PlannerAssert.verifyPhaseLifecycle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.common.TestdataObjectSorter;
import ai.greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionSorter;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataSolution;

import org.junit.jupiter.api.Test;

class SortingEntitySelectorTest {

  @Test
  void cacheTypeSolver() {
    runCacheType(SelectionCacheType.SOLVER, 1);
  }

  @Test
  void cacheTypePhase() {
    runCacheType(SelectionCacheType.PHASE, 2);
  }

  @Test
  void cacheTypeStep() {
    runCacheType(SelectionCacheType.STEP, 5);
  }

  @Test
  void cacheTypeJustInTime() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> runCacheType(SelectionCacheType.JUST_IN_TIME, 5));
  }

  public void runCacheType(SelectionCacheType cacheType, int timesCalled) {
    EntitySelector childEntitySelector =
        SelectorTestUtils.mockEntitySelector(
            TestdataEntity.class,
            new TestdataEntity("jan"),
            new TestdataEntity("feb"),
            new TestdataEntity("mar"),
            new TestdataEntity("apr"),
            new TestdataEntity("may"),
            new TestdataEntity("jun"));

    EntitySelector entitySelector =
        new SortingEntitySelector(
            childEntitySelector,
            cacheType,
            new TestdataObjectSorter<TestdataSolution, TestdataEntity>());

    InnerScoreDirector<?, ?> scoreDirector = mock(InnerScoreDirector.class);
    doReturn(new TestdataSolution()).when(scoreDirector).getWorkingSolution();
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
    assertAllCodesOfEntitySelector(entitySelector, "apr", "feb", "jan", "jun", "mar", "may");
    entitySelector.stepEnded(stepScopeA1);

    AbstractStepScope stepScopeA2 = mock(AbstractStepScope.class);
    when(stepScopeA2.getPhaseScope()).thenReturn(phaseScopeA);
    when(stepScopeA2.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.stepStarted(stepScopeA2);
    assertAllCodesOfEntitySelector(entitySelector, "apr", "feb", "jan", "jun", "mar", "may");
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
    assertAllCodesOfEntitySelector(entitySelector, "apr", "feb", "jan", "jun", "mar", "may");
    entitySelector.stepEnded(stepScopeB1);

    AbstractStepScope stepScopeB2 = mock(AbstractStepScope.class);
    when(stepScopeB2.getPhaseScope()).thenReturn(phaseScopeB);
    when(stepScopeB2.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.stepStarted(stepScopeB2);
    assertAllCodesOfEntitySelector(entitySelector, "apr", "feb", "jan", "jun", "mar", "may");
    entitySelector.stepEnded(stepScopeB2);

    AbstractStepScope stepScopeB3 = mock(AbstractStepScope.class);
    when(stepScopeB3.getPhaseScope()).thenReturn(phaseScopeB);
    when(stepScopeB3.getScoreDirector()).thenReturn(scoreDirector);
    entitySelector.stepStarted(stepScopeB3);
    assertAllCodesOfEntitySelector(entitySelector, "apr", "feb", "jan", "jun", "mar", "may");
    entitySelector.stepEnded(stepScopeB3);

    entitySelector.phaseEnded(phaseScopeB);

    entitySelector.solvingEnded(solverScope);

    verifyPhaseLifecycle(childEntitySelector, 1, 2, 5);
    verify(childEntitySelector, times(timesCalled)).iterator();
    verify(childEntitySelector, times(timesCalled)).getSize();
  }

  @Test
  void isNeverEnding() {
    EntitySelector entitySelector =
        new SortingEntitySelector(
            mock(EntitySelector.class), SelectionCacheType.PHASE, mock(SelectionSorter.class));
    assertThat(entitySelector.isNeverEnding()).isFalse();
  }

  @Test
  void isCountable() {
    EntitySelector entitySelector =
        new SortingEntitySelector(
            mock(EntitySelector.class), SelectionCacheType.PHASE, mock(SelectionSorter.class));
    assertThat(entitySelector.isCountable()).isTrue();
  }
}
