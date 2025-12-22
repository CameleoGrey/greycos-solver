package ai.greycos.solver.core.impl.score.stream.collector.bi;

import java.util.function.Function;
import java.util.function.Supplier;

import ai.greycos.solver.core.api.function.TriFunction;
import ai.greycos.solver.core.api.score.stream.bi.BiConstraintCollector;
import ai.greycos.solver.core.impl.score.stream.collector.LongCounter;

import org.jspecify.annotations.NonNull;

final class CountLongBiCollector<A, B> implements BiConstraintCollector<A, B, LongCounter, Long> {
  private static final CountLongBiCollector<?, ?> INSTANCE = new CountLongBiCollector<>();

  private CountLongBiCollector() {}

  @SuppressWarnings("unchecked")
  static <A, B> CountLongBiCollector<A, B> getInstance() {
    return (CountLongBiCollector<A, B>) INSTANCE;
  }

  @Override
  public @NonNull Supplier<LongCounter> supplier() {
    return LongCounter::new;
  }

  @Override
  public @NonNull TriFunction<LongCounter, A, B, Runnable> accumulator() {
    return (counter, a, b) -> {
      counter.increment();
      return counter::decrement;
    };
  }

  @Override
  public @NonNull Function<LongCounter, Long> finisher() {
    return LongCounter::result;
  }
}
