package ai.greycos.solver.core.impl.heuristic.selector.common;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

public final class SelectionCacheLifecycleBridge<Solution_>
    implements PhaseLifecycleListener<Solution_> {

  private final SelectionCacheType cacheType;
  private final SelectionCacheLifecycleListener<Solution_> selectionCacheLifecycleListener;
  private boolean isConstructed = false;
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
      updateWorkingEntityListRevision(solverScope);
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
      updateWorkingEntityListRevision(phaseScope.getSolverScope());
    }
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> stepScope) {
    if (cacheType == SelectionCacheType.STEP) {
      assertNotConstructed();
      selectionCacheLifecycleListener.constructCache(stepScope.getPhaseScope().getSolverScope());
      isConstructed = true;
      updateWorkingEntityListRevision(stepScope.getPhaseScope().getSolverScope());
    } else if (cacheType == SelectionCacheType.PHASE || cacheType == SelectionCacheType.SOLVER) {
      resetCacheIfWorkingSolutionChanged(stepScope);
    }
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    if (cacheType == SelectionCacheType.STEP) {
      assertConstructed();
      selectionCacheLifecycleListener.disposeCache(stepScope.getPhaseScope().getSolverScope());
      isConstructed = false;
      workingEntityListRevision = null;
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
      isConstructed = false;
      workingEntityListRevision = null;
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    if (cacheType == SelectionCacheType.SOLVER) {
      assertConstructed();
      selectionCacheLifecycleListener.disposeCache(solverScope);
      isConstructed = false;
      workingEntityListRevision = null;
    } else {
      assertNotConstructed(); // Fail fast if we have a disposal problem, which is effectively a
      // memory leak.
    }
  }

  private void updateWorkingEntityListRevision(SolverScope<Solution_> solverScope) {
    workingEntityListRevision = solverScope.getScoreDirector().getWorkingEntityListRevision();
  }

  private void resetCacheIfWorkingSolutionChanged(AbstractStepScope<Solution_> stepScope) {
    if (!isConstructed || workingEntityListRevision == null) {
      return;
    }
    var scoreDirector = stepScope.getScoreDirector();
    if (scoreDirector.isWorkingEntityListDirty(workingEntityListRevision)) {
      var solverScope = stepScope.getPhaseScope().getSolverScope();
      selectionCacheLifecycleListener.disposeCache(solverScope);
      selectionCacheLifecycleListener.constructCache(solverScope);
      workingEntityListRevision = scoreDirector.getWorkingEntityListRevision();
    }
  }

  @Override
  public String toString() {
    return "Bridge(" + selectionCacheLifecycleListener + ")";
  }
}
