package greycos.solver.core.impl.bavet.common;

import greycos.solver.core.api.score.profile.ConstraintProfile.Operation;
import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler.Unit;

public record ProfilingPropagator(
    DefaultConstraintProfiler profiler, Unit unit, Propagator delegate) implements Propagator {
  @Override
  public void propagateRetracts() {
    profiler.enterUnit(unit, Operation.RETRACT);
    Throwable failure = null;
    try {
      delegate.propagateRetracts();
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitUnit(failure);
    }
  }

  @Override
  public void propagateUpdates() {
    profiler.enterUnit(unit, Operation.UPDATE);
    Throwable failure = null;
    try {
      delegate.propagateUpdates();
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitUnit(failure);
    }
  }

  @Override
  public void propagateInserts() {
    profiler.enterUnit(unit, Operation.INSERT);
    Throwable failure = null;
    try {
      delegate.propagateInserts();
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitUnit(failure);
    }
  }
}
