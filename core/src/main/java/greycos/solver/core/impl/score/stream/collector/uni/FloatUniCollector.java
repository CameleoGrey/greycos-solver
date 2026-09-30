package greycos.solver.core.impl.score.stream.collector.uni;

import java.util.function.Function;

import greycos.solver.core.api.function.ToFloatFunction;
import greycos.solver.core.api.score.stream.uni.UniConstraintCollector;
import greycos.solver.core.api.score.stream.uni.UniConstraintCollectorAccumulator;
import greycos.solver.core.api.score.stream.uni.UniConstraintCollectorValueHandle;
import greycos.solver.core.impl.score.stream.collector.AbstractFloatingPointCollector;
import greycos.solver.core.impl.score.stream.collector.AbstractFloatingPointSlot;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

final class FloatUniCollector<A> extends AbstractFloatingPointCollector<ToFloatFunction<? super A>>
    implements UniConstraintCollector<A, AbstractFloatingPointSlot.State, Float> {

  FloatUniCollector(ToFloatFunction<? super A> mapper, boolean average) {
    super(mapper, average);
  }

  @Override
  public @NonNull UniConstraintCollectorAccumulator<AbstractFloatingPointSlot.State, A>
      accumulator() {
    return Slot::new;
  }

  @Override
  public @NonNull Function<AbstractFloatingPointSlot.State, @Nullable Float> finisher() {
    return average
        ? AbstractFloatingPointSlot.State::averageFloat
        : AbstractFloatingPointSlot.State::sumFloat;
  }

  private final class Slot extends AbstractFloatingPointSlot
      implements UniConstraintCollectorValueHandle<A> {

    private Slot(State state) {
      super(state);
    }

    @Override
    public void add(A a) {
      addMapped(mapper.applyAsFloat(a));
    }

    @Override
    public void replaceWith(A a) {
      replaceWithMapped(mapper.applyAsFloat(a));
    }

    @Override
    public void remove() {
      removeMapped();
    }
  }
}
