package ai.greycos.solver.core.impl.score.stream.collector.tri;

import java.util.function.Supplier;

import ai.greycos.solver.core.api.function.ToIntTriFunction;
import ai.greycos.solver.core.impl.score.stream.collector.IntSumCalculator;

import org.jspecify.annotations.NonNull;

final class SumIntTriCollector<A, B, C>
    extends IntCalculatorTriCollector<A, B, C, Integer, IntSumCalculator> {
  SumIntTriCollector(ToIntTriFunction<? super A, ? super B, ? super C> mapper) {
    super(mapper);
  }

  @Override
  public @NonNull Supplier<IntSumCalculator> supplier() {
    return IntSumCalculator::new;
  }
}
