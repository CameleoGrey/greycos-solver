package greycos.solver.core.impl.solver.monitoring;

import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.event.SolverEventListener;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

/** Shared registration helpers for root and island solvers. */
public final class SolverMetricSupport {
  private SolverMetricSupport() {}

  @SuppressWarnings("unchecked")
  public static <Solution_> SolverScope<Solution_> scope(Solver<Solution_> solver) {
    if (solver instanceof SolverMetricScopeProvider<?> provider) {
      return (SolverScope<Solution_>) provider.getSolverScope();
    }
    throw new IllegalArgumentException("Solver (" + solver + ") does not expose a metric scope.");
  }

  public static void publish(SolverScope<?> scope, Runnable publication) {
    scope.getMetricRun().publish(publication);
  }

  public static <Solution_> SolverEventListener<Solution_> guardedEventListener(
      SolverScope<Solution_> scope, SolverEventListener<Solution_> delegate) {
    var run = scope.getMetricRun();
    return event -> run.publish(() -> delegate.bestSolutionChanged(event));
  }

  public static <Solution_> GuardedPhaseListener<Solution_> guardedPhaseListener(
      SolverScope<Solution_> scope, PhaseLifecycleListenerAdapter<Solution_> delegate) {
    return new GuardedPhaseListener<>(scope.getMetricRun(), delegate);
  }

  public static final class GuardedPhaseListener<Solution_>
      extends PhaseLifecycleListenerAdapter<Solution_> {
    private final SolverMetricRun run;
    private final PhaseLifecycleListenerAdapter<Solution_> delegate;

    private GuardedPhaseListener(
        SolverMetricRun run, PhaseLifecycleListenerAdapter<Solution_> delegate) {
      this.run = run;
      this.delegate = delegate;
    }

    public PhaseLifecycleListenerAdapter<Solution_> delegate() {
      return delegate;
    }

    @Override
    public void phaseStarted(AbstractPhaseScope<Solution_> scope) {
      run.publish(() -> delegate.phaseStarted(scope));
    }

    @Override
    public void phaseEnded(AbstractPhaseScope<Solution_> scope) {
      run.publish(() -> delegate.phaseEnded(scope));
    }

    @Override
    public void stepStarted(AbstractStepScope<Solution_> scope) {
      run.publish(() -> delegate.stepStarted(scope));
    }

    @Override
    public void stepEnded(AbstractStepScope<Solution_> scope) {
      run.publish(() -> delegate.stepEnded(scope));
    }
  }
}
