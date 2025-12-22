package ai.greycos.solver.core.impl.score.stream.collector.quad;

import java.util.function.Supplier;

import ai.greycos.solver.core.api.function.ToIntQuadFunction;
import ai.greycos.solver.core.impl.score.stream.collector.IntAverageCalculator;

import org.jspecify.annotations.NonNull;

final class AverageIntQuadCollector<A, B, C, D>
    extends IntCalculatorQuadCollector<A, B, C, D, Double, IntAverageCalculator> {
  AverageIntQuadCollector(ToIntQuadFunction<? super A, ? super B, ? super C, ? super D> mapper) {
    super(mapper);
  }

  @Override
  public @NonNull Supplier<IntAverageCalculator> supplier() {
    return IntAverageCalculator::new;
  }
}
