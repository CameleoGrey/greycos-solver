package greycos.solver.core.impl.bavet.common.tuple;

import java.util.Objects;

import greycos.solver.core.api.score.profile.ConstraintProfile.Operation;
import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler;
import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler.Unit;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ProfilingTupleLifecycle<Tuple_ extends Tuple> implements TupleLifecycle<Tuple_> {
  private final DefaultConstraintProfiler profiler;
  private final Unit unit;
  private final TupleLifecycle<Tuple_> delegate;
  private boolean active;

  public ProfilingTupleLifecycle(
      DefaultConstraintProfiler profiler, Unit unit, TupleLifecycle<Tuple_> delegate) {
    this.profiler = profiler;
    this.unit = unit;
    this.delegate = delegate;
  }

  @Override
  public void afterAllFactsInserted(boolean upstreamCanProduceTuples) {
    delegate.afterAllFactsInserted(upstreamCanProduceTuples);
  }

  @Override
  public boolean isActive() {
    active = delegate.isActive();
    return active;
  }

  /** The result observed during normal network activation; does not rerun activation. */
  public boolean wasActive() {
    return active;
  }

  @Override
  public void insert(Tuple_ tuple) {
    profiler.enterUnit(unit, Operation.INSERT);
    Throwable failure = null;
    try {
      delegate.insert(tuple);
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitUnit(failure);
    }
  }

  @Override
  public void update(Tuple_ tuple) {
    profiler.enterUnit(unit, Operation.UPDATE);
    Throwable failure = null;
    try {
      delegate.update(tuple);
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitUnit(failure);
    }
  }

  @Override
  public void retract(Tuple_ tuple) {
    profiler.enterUnit(unit, Operation.RETRACT);
    Throwable failure = null;
    try {
      delegate.retract(tuple);
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitUnit(failure);
    }
  }

  public Unit unit() {
    return unit;
  }

  public TupleLifecycle<Tuple_> delegate() {
    return delegate;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    return obj instanceof ProfilingTupleLifecycle<?> other
        && Objects.equals(profiler, other.profiler)
        && Objects.equals(unit, other.unit)
        && Objects.equals(delegate, other.delegate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(profiler, unit, delegate);
  }

  @Override
  public String toString() {
    return "ProfilingTupleLifecycle[%s, %s]".formatted(unit.unitId(), delegate);
  }
}
