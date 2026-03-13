package ai.greycos.solver.core.impl.bavet.quad;

import java.util.function.Supplier;

import ai.greycos.solver.core.impl.bavet.common.AbstractPrecomputeNode;
import ai.greycos.solver.core.impl.bavet.common.tuple.QuadTuple;
import ai.greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import ai.greycos.solver.core.impl.score.stream.bavet.common.BavetPrecomputeBuildHelper;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PrecomputeQuadNode<A, B, C, D>
    extends AbstractPrecomputeNode<QuadTuple<A, B, C, D>> {
  private final int outputStoreSize;

  public PrecomputeQuadNode(
      Supplier<BavetPrecomputeBuildHelper<QuadTuple<A, B, C, D>>> precomputeBuildHelperSupplier,
      int outputStoreSize,
      TupleLifecycle<QuadTuple<A, B, C, D>> nextNodesTupleLifecycle,
      Class<?>[] sourceClasses) {
    super(precomputeBuildHelperSupplier, nextNodesTupleLifecycle, sourceClasses);
    this.outputStoreSize = outputStoreSize;
  }

  @Override
  protected QuadTuple<A, B, C, D> remapTuple(QuadTuple<A, B, C, D> tuple) {
    return QuadTuple.of(tuple.getA(), tuple.getB(), tuple.getC(), tuple.getD(), outputStoreSize);
  }
}
