package greycos.solver.core.impl.heuristic.selector.common;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;

public final class SelectionCacheLifecycleBridge<Solution_>
    implements PhaseLifecycleListener<Solution_> {

  private final SelectionCacheType cacheType;
  private final SelectionCacheLifecycleListener<Solution_> selectionCacheLifecycleListener;
  private boolean isConstructed = false;
  private InnerScoreDirector<Solution_, ?> cachedScoreDirector = null;
  private Long workingEntityListRevision = null;

  public SelectionCacheLifecycleBridge(
      SelectionCacheType cacheType,
      SelectionCacheLifecycleListener<Solution_> selectionCacheLifecycleListener) {
    this.cacheType = cacheType;
    this.selectionCacheLifecycleListener = selectionCacheLifecycleListener;
    if (cacheType == null) {
      throw new IllegalArgumentException(
          "The cacheType ("
              + cacheType
              + ") for selectionCacheLifecycleListener ("
              + selectionCacheLifecycleListener
              + ") should have already been resolved.");
    }
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    assertNotConstructed();
    if (cacheType == SelectionCacheType.SOLVER) {
      selectionCacheLifecycleListener.constructCache(solverScope);
      isConstructed = true;
      updateCacheContext(solverScope);
    }
  }

  private void assertNotConstructed() {
    if (isConstructed) {
      throw new IllegalStateException(
          "Impossible state: selection cache of type ("
              + cacheType
              + ") for listener ("
              + selectionCacheLifecycleListener
              + ") already constructed.");
    }
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    if (cacheType == SelectionCacheType.PHASE) {
      assertNotConstructed();
      selectionCacheLifecycleListener.constructCache(phaseScope.getSolverScope());
      isConstructed = true;
      updateCacheContext(phaseScope.getSolverScope());
    } else if (cacheType == SelectionCacheType.SOLVER) {
      // Other selector caches may read this cache during phaseStarted(), before the first step.
      resetCacheIfWorkingSolutionChanged(phaseScope.getSolverScope());
    }
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> stepScope) {
    if (cacheType == SelectionCacheType.STEP) {
      assertNotConstructed();
      selectionCacheLifecycleListener.constructCache(stepScope.getPhaseScope().getSolverScope());
      isConstructed = true;
      updateCacheContext(stepScope.getPhaseScope().getSolverScope());
    } else if (cacheType == SelectionCacheType.PHASE || cacheType == SelectionCacheType.SOLVER) {
      resetCacheIfWorkingSolutionChanged(stepScope.getPhaseScope().getSolverScope());
    }
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    if (cacheType == SelectionCacheType.STEP) {
      assertConstructed();
      selectionCacheLifecycleListener.disposeCache(stepScope.getPhaseScope().getSolverScope());
      clearCacheContext();
    }
  }

  private void assertConstructed() {
    if (!isConstructed) {
      throw new IllegalStateException(
          "Impossible state: selection cache of type ("
              + cacheType
              + ") for listener ("
              + selectionCacheLifecycleListener
              + ") already disposed of.");
    }
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    if (cacheType != SelectionCacheType.SOLVER) {
      // Dispose of step cache as well, since we aren't guaranteed that stepEnded() was called.
      if (cacheType != SelectionCacheType.STEP) {
        assertConstructed(); // The step cache may have already been disposed of during stepEnded().
      }
      selectionCacheLifecycleListener.disposeCache(phaseScope.getSolverScope());
      clearCacheContext();
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    if (cacheType == SelectionCacheType.SOLVER) {
      assertConstructed();
      selectionCacheLifecycleListener.disposeCache(solverScope);
      clearCacheContext();
    } else {
      assertNotConstructed(); // Fail fast if we have a disposal problem, which is effectively a
      // memory leak.
    }
  }

  private void updateCacheContext(SolverScope<Solution_> solverScope) {
    cachedScoreDirector = solverScope.getScoreDirector();
    workingEntityListRevision = cachedScoreDirector.getWorkingEntityListRevision();
  }

  private void clearCacheContext() {
    isConstructed = false;
    cachedScoreDirector = null;
    workingEntityListRevision = null;
  }

  private void resetCacheIfWorkingSolutionChanged(SolverScope<Solution_> solverScope) {
    if (!isConstructed || workingEntityListRevision == null) {
      return;
    }
    var scoreDirector = solverScope.getScoreDirector();
    // Entity revisions are local to each director and can coincide after an environment-mode swap.
    if (scoreDirector != cachedScoreDirector
        || scoreDirector.isWorkingEntityListDirty(workingEntityListRevision)) {
      selectionCacheLifecycleListener.disposeCache(solverScope);
      clearCacheContext();
      selectionCacheLifecycleListener.constructCache(solverScope);
      isConstructed = true;
      updateCacheContext(solverScope);
    }
  }

  @Override
  public String toString() {
    return "Bridge(" + selectionCacheLifecycleListener + ")";
  }
}
