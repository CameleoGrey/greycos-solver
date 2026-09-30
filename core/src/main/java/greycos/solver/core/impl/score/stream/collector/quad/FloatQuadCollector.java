package greycos.solver.core.impl.score.stream.collector.quad;

import java.util.function.Function;

import greycos.solver.core.api.function.ToFloatQuadFunction;
import greycos.solver.core.api.score.stream.quad.QuadConstraintCollector;
import greycos.solver.core.api.score.stream.quad.QuadConstraintCollectorAccumulator;
import greycos.solver.core.api.score.stream.quad.QuadConstraintCollectorValueHandle;
import greycos.solver.core.impl.score.stream.collector.AbstractFloatingPointCollector;
import greycos.solver.core.impl.score.stream.collector.AbstractFloatingPointSlot;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

final class FloatQuadCollector<A, B, C, D>
    extends AbstractFloatingPointCollector<
        ToFloatQuadFunction<? super A, ? super B, ? super C, ? super D>>
    implements QuadConstraintCollector<A, B, C, D, AbstractFloatingPointSlot.State, Float> {

  FloatQuadCollector(
      ToFloatQuadFunction<? super A, ? super B, ? super C, ? super D> mapper, boolean average) {
    super(mapper, average);
  }

  @Override
  public @NonNull QuadConstraintCollectorAccumulator<AbstractFloatingPointSlot.State, A, B, C, D>
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
      implements QuadConstraintCollectorValueHandle<A, B, C, D> {

    private Slot(State state) {
      super(state);
    }

    @Override
    public void add(A a, B b, C c, D d) {
      addMapped(mapper.applyAsFloat(a, b, c, d));
    }

    @Override
    public void replaceWith(A a, B b, C c, D d) {
      replaceWithMapped(mapper.applyAsFloat(a, b, c, d));
    }

    @Override
    public void remove() {
      removeMapped();
    }
  }
}
