package greycos.solver.core.impl.score.stream.collector.quad;

import java.util.function.Function;

import greycos.solver.core.api.function.ToDoubleQuadFunction;
import greycos.solver.core.api.score.stream.quad.QuadConstraintCollector;
import greycos.solver.core.api.score.stream.quad.QuadConstraintCollectorAccumulator;
import greycos.solver.core.api.score.stream.quad.QuadConstraintCollectorValueHandle;
import greycos.solver.core.impl.score.stream.collector.AbstractFloatingPointCollector;
import greycos.solver.core.impl.score.stream.collector.AbstractFloatingPointSlot;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

final class DoubleQuadCollector<A, B, C, D>
    extends AbstractFloatingPointCollector<
        ToDoubleQuadFunction<? super A, ? super B, ? super C, ? super D>>
    implements QuadConstraintCollector<A, B, C, D, AbstractFloatingPointSlot.State, Double> {

  DoubleQuadCollector(
      ToDoubleQuadFunction<? super A, ? super B, ? super C, ? super D> mapper, boolean average) {
    super(mapper, average);
  }

  @Override
  public @NonNull QuadConstraintCollectorAccumulator<AbstractFloatingPointSlot.State, A, B, C, D>
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
      implements QuadConstraintCollectorValueHandle<A, B, C, D> {

    private Slot(State state) {
      super(state);
    }

    @Override
    public void add(A a, B b, C c, D d) {
      addMapped(mapper.applyAsDouble(a, b, c, d));
    }

    @Override
    public void replaceWith(A a, B b, C c, D d) {
      replaceWithMapped(mapper.applyAsDouble(a, b, c, d));
    }

    @Override
    public void remove() {
      removeMapped();
    }
  }
}
