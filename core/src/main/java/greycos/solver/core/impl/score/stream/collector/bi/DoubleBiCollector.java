package greycos.solver.core.impl.score.stream.collector.bi;

import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;

import greycos.solver.core.api.score.stream.bi.BiConstraintCollector;
import greycos.solver.core.api.score.stream.bi.BiConstraintCollectorAccumulator;
import greycos.solver.core.api.score.stream.bi.BiConstraintCollectorValueHandle;
import greycos.solver.core.impl.score.stream.collector.AbstractFloatingPointCollector;
import greycos.solver.core.impl.score.stream.collector.AbstractFloatingPointSlot;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

final class DoubleBiCollector<A, B>
    extends AbstractFloatingPointCollector<ToDoubleBiFunction<? super A, ? super B>>
    implements BiConstraintCollector<A, B, AbstractFloatingPointSlot.State, Double> {

  DoubleBiCollector(ToDoubleBiFunction<? super A, ? super B> mapper, boolean average) {
    super(mapper, average);
  }

  @Override
  public @NonNull BiConstraintCollectorAccumulator<AbstractFloatingPointSlot.State, A, B>
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
      implements BiConstraintCollectorValueHandle<A, B> {

    private Slot(State state) {
      super(state);
    }

    @Override
    public void add(A a, B b) {
      addMapped(mapper.applyAsDouble(a, b));
    }

    @Override
    public void replaceWith(A a, B b) {
      replaceWithMapped(mapper.applyAsDouble(a, b));
    }

    @Override
    public void remove() {
      removeMapped();
    }
  }
}
