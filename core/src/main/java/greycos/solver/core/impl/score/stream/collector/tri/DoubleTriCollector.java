package greycos.solver.core.impl.score.stream.collector.tri;

import java.util.function.Function;

import greycos.solver.core.api.function.ToDoubleTriFunction;
import greycos.solver.core.api.score.stream.tri.TriConstraintCollector;
import greycos.solver.core.api.score.stream.tri.TriConstraintCollectorAccumulator;
import greycos.solver.core.api.score.stream.tri.TriConstraintCollectorValueHandle;
import greycos.solver.core.impl.score.stream.collector.AbstractFloatingPointCollector;
import greycos.solver.core.impl.score.stream.collector.AbstractFloatingPointSlot;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

final class DoubleTriCollector<A, B, C>
    extends AbstractFloatingPointCollector<ToDoubleTriFunction<? super A, ? super B, ? super C>>
    implements TriConstraintCollector<A, B, C, AbstractFloatingPointSlot.State, Double> {

  DoubleTriCollector(ToDoubleTriFunction<? super A, ? super B, ? super C> mapper, boolean average) {
    super(mapper, average);
  }

  @Override
  public @NonNull TriConstraintCollectorAccumulator<AbstractFloatingPointSlot.State, A, B, C>
      accumulator() {
    return Slot::new;
  }

  @Override
  public @NonNull Function<AbstractFloatingPointSlot.State, @Nullable Double> finisher() {
    return average
        ? AbstractFloatingPointSlot.State::averageDouble
        : AbstractFloatingPointSlot.State::sumDouble;
  }

  private final class Slot extends AbstractFloatingPointSlot
      implements TriConstraintCollectorValueHandle<A, B, C> {

    private Slot(State state) {
      super(state);
    }

    @Override
    public void add(A a, B b, C c) {
      addMapped(mapper.applyAsDouble(a, b, c));
    }

    @Override
    public void replaceWith(A a, B b, C c) {
      replaceWithMapped(mapper.applyAsDouble(a, b, c));
    }

    @Override
    public void remove() {
      removeMapped();
    }
  }
}
