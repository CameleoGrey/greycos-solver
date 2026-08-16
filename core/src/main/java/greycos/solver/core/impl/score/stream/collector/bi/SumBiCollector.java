package greycos.solver.core.impl.score.stream.collector.bi;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongBiFunction;

import greycos.solver.core.api.score.stream.bi.BiConstraintCollectorValueHandle;
import greycos.solver.core.impl.score.stream.collector.AbstractLongSumSlot;
import greycos.solver.core.impl.util.MutableLong;

import org.jspecify.annotations.NonNull;

final class SumBiCollector<A, B>
    extends AbstractPrimitiveBasedBiCollector<A, B, Long, MutableLong> {
  SumBiCollector(ToLongBiFunction<? super A, ? super B> mapper) {
    super(mapper);
  }

  @Override
  public @NonNull Supplier<MutableLong> supplier() {
    return MutableLong::new;
  }

  @Override
  public @NonNull Function<MutableLong, Long> finisher() {
    return MutableLong::longValue;
  }

  @Override
  protected BiConstraintCollectorValueHandle<A, B> newAccumulatedValue(MutableLong state) {
    return new Slot(state);
  }

  private final class Slot extends AbstractLongSumSlot
      implements BiConstraintCollectorValueHandle<A, B> {
    Slot(MutableLong state) {
      super(state);
    }

    @Override
    public void add(A a, B b) {
      addMapped(mapper.applyAsLong(a, b));
    }

    @Override
    public void replaceWith(A a, B b) {
      replaceWithMapped(mapper.applyAsLong(a, b));
    }

    @Override
    public void remove() {
      removeMapped();
    }
  }
}
