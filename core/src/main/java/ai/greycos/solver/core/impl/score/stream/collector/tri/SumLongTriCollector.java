package ai.greycos.solver.core.impl.score.stream.collector.tri;

import java.util.function.Supplier;

import ai.greycos.solver.core.api.function.ToLongTriFunction;
import ai.greycos.solver.core.impl.score.stream.collector.LongSumCalculator;

import org.jspecify.annotations.NonNull;

final class SumLongTriCollector<A, B, C>
    extends LongCalculatorTriCollector<A, B, C, Long, LongSumCalculator> {
  SumLongTriCollector(ToLongTriFunction<? super A, ? super B, ? super C> mapper) {
    super(mapper);
  }

  @Override
  public @NonNull Supplier<LongSumCalculator> supplier() {
    return LongSumCalculator::new;
  }
}
