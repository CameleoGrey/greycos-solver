package greycos.solver.core.impl.heuristic.selector;

import java.util.random.RandomGenerator;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleSupport;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract superclass for {@link Selector}.
 *
 * @see AbstractDemandEnabledSelector
 */
public abstract class AbstractSelector<Solution_> implements Selector<Solution_> {

  protected final transient Logger logger = LoggerFactory.getLogger(getClass());

  protected PhaseLifecycleSupport<Solution_> phaseLifecycleSupport = new PhaseLifecycleSupport<>();

  protected RandomGenerator workingRandom = null;

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    workingRandom = solverScope.getWorkingRandom().moveIteratorUsage();
    phaseLifecycleSupport.fireSolvingStarted(solverScope);
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    // Only ILS scopes own a separate stream. Keep ordinary selectors bound at solvingStarted.
    if (phaseScope instanceof IteratedLocalSearchPhaseScope<?>
        || (phaseScope instanceof LocalSearchPhaseScope<?> localSearch
            && localSearch.isEpisode())) {
      workingRandom = phaseScope.getWorkingRandom().moveIteratorUsage();
    }
    phaseLifecycleSupport.firePhaseStarted(phaseScope);
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> stepScope) {
    phaseLifecycleSupport.fireStepStarted(stepScope);
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    phaseLifecycleSupport.fireStepEnded(stepScope);
  }

  @Override
  public void stepAborted(AbstractStepScope<Solution_> stepScope) {
    phaseLifecycleSupport.fireStepAborted(stepScope);
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    phaseLifecycleSupport.firePhaseEnded(phaseScope);
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    try {
      phaseLifecycleSupport.fireSolvingEnded(solverScope);
    } finally {
      workingRandom = null;
    }
  }

  @Override
  public SelectionCacheType getCacheType() {
    return SelectionCacheType.JUST_IN_TIME;
  }
}
