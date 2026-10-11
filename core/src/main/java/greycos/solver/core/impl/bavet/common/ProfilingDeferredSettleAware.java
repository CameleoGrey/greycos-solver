package greycos.solver.core.impl.bavet.common;

import greycos.solver.core.api.score.profile.ConstraintProfile.Operation;
import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler.Unit;

/** Instruments preparation without changing the node identity or its preload protocol. */
public record ProfilingDeferredSettleAware(
    DefaultConstraintProfiler profiler, Unit unit, DeferredSettleAware delegate)
    implements DeferredSettleAware {
  @Override
  public void prepareForSettle() {
    profiler.enterUnit(unit, Operation.PREPARE);
    Throwable failure = null;
    try {
      delegate.prepareForSettle();
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitUnit(failure);
    }
  }

  @Override
  public boolean canDeferWork() {
    return delegate.canDeferWork();
  }

  @Override
  public void setSettleDistance(long settleDistance) {
    delegate.setSettleDistance(settleDistance);
  }

  @Override
  public void preloadStarted() {
    delegate.preloadStarted();
  }

  @Override
  public void preloadEnded() {
    delegate.preloadEnded();
  }
}
