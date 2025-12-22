package ai.greycos.solver.core.impl.score.stream.collector.quad;

import java.util.function.Function;
import java.util.function.Supplier;

import ai.greycos.solver.core.api.function.PentaFunction;
import ai.greycos.solver.core.api.score.stream.quad.QuadConstraintCollector;
import ai.greycos.solver.core.impl.score.stream.collector.IntCounter;

import org.jspecify.annotations.NonNull;

final class CountIntQuadCollector<A, B, C, D>
    implements QuadConstraintCollector<A, B, C, D, IntCounter, Integer> {
  private static final CountIntQuadCollector<?, ?, ?, ?> INSTANCE = new CountIntQuadCollector<>();

  private CountIntQuadCollector() {}

  @SuppressWarnings("unchecked")
  static <A, B, C, D> CountIntQuadCollector<A, B, C, D> getInstance() {
    return (CountIntQuadCollector<A, B, C, D>) INSTANCE;
  }

  @Override
  public @NonNull Supplier<IntCounter> supplier() {
    return IntCounter::new;
  }

  @Override
  public @NonNull PentaFunction<IntCounter, A, B, C, D, Runnable> accumulator() {
    return (counter, a, b, c, d) -> {
      counter.increment();
      return counter::decrement;
    };
  }

  @Override
  public @NonNull Function<IntCounter, Integer> finisher() {
    return IntCounter::result;
  }
}
