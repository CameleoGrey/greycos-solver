package greycos.solver.core.impl.neighborhood.stream.enumerating.collector;

import java.util.function.Function;
import java.util.function.Supplier;

import greycos.solver.core.api.score.stream.bi.BiConstraintCollector;
import greycos.solver.core.api.score.stream.bi.BiConstraintCollectorAccumulator;
import greycos.solver.core.api.score.stream.bi.BiConstraintCollectorValueHandle;
import greycos.solver.core.api.score.stream.uni.UniConstraintCollector;
import greycos.solver.core.api.score.stream.uni.UniConstraintCollectorAccumulator;
import greycos.solver.core.api.score.stream.uni.UniConstraintCollectorValueHandle;
import greycos.solver.core.preview.api.move.SolutionView;
import greycos.solver.core.preview.api.neighborhood.stream.enumerating.collector.BiNeighborhoodsCollector;
import greycos.solver.core.preview.api.neighborhood.stream.enumerating.collector.BiNeighborhoodsCollectorValueHandle;
import greycos.solver.core.preview.api.neighborhood.stream.enumerating.collector.UniNeighborhoodsCollector;
import greycos.solver.core.preview.api.neighborhood.stream.enumerating.collector.UniNeighborhoodsCollectorValueHandle;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Adapts neighborhoods collectors to Bavet's constraint collector API. Called at {@code
 * buildNode()} time once the {@link SolutionView} is available.
 */
@NullMarked
public final class NeighborhoodsCollectorUtils {

  public static <Solution_, A, ResultContainer_, Result_>
      UniConstraintCollector<A, ResultContainer_, Result_> toConstraintCollector(
          UniNeighborhoodsCollector<Solution_, A, ResultContainer_, Result_> collector,
          SolutionView<Solution_> view) {
    var acc = collector.accumulator();
    return new UniConstraintCollector<>() {
      @Override
      public Supplier<ResultContainer_> supplier() {
        return collector.supplier();
      }

      @Override
      public UniConstraintCollectorAccumulator<ResultContainer_, A> accumulator() {
        return container -> {
          var handle = acc.intoGroup(view, container);
          return wrapUni(handle);
        };
      }

      @Override
      public Function<ResultContainer_, @Nullable Result_> finisher() {
        return collector.finisher();
      }
    };
  }

  public static <Solution_, A, B, ResultContainer_, Result_>
      BiConstraintCollector<A, B, ResultContainer_, Result_> toConstraintCollector(
          BiNeighborhoodsCollector<Solution_, A, B, ResultContainer_, Result_> collector,
          SolutionView<Solution_> view) {
    var acc = collector.accumulator();
    return new BiConstraintCollector<>() {
      @Override
      public Supplier<ResultContainer_> supplier() {
        return collector.supplier();
      }

      @Override
      public BiConstraintCollectorAccumulator<ResultContainer_, A, B> accumulator() {
        return container -> {
          var handle = acc.intoGroup(view, container);
          return wrapBi(handle);
        };
      }

      @Override
      public Function<ResultContainer_, @Nullable Result_> finisher() {
        return collector.finisher();
      }
    };
  }

  private static <A> UniConstraintCollectorValueHandle<A> wrapUni(
      UniNeighborhoodsCollectorValueHandle<A> handle) {
    return new UniConstraintCollectorValueHandle<>() {
      @Override
      public void add(@Nullable A a) {
        handle.add(a);
      }

      @Override
      public void replaceWith(@Nullable A a) {
        handle.replaceWith(a);
      }

      @Override
      public void remove() {
        handle.remove();
      }
    };
  }

  private static <A, B> BiConstraintCollectorValueHandle<A, B> wrapBi(
      BiNeighborhoodsCollectorValueHandle<A, B> handle) {
    return new BiConstraintCollectorValueHandle<>() {
      @Override
      public void add(@Nullable A a, @Nullable B b) {
        handle.add(a, b);
      }

      @Override
      public void replaceWith(@Nullable A a, @Nullable B b) {
        handle.replaceWith(a, b);
      }

      @Override
      public void remove() {
        handle.remove();
      }
    };
  }

  private NeighborhoodsCollectorUtils() {}
}
