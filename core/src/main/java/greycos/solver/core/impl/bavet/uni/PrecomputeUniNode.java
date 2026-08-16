package greycos.solver.core.impl.bavet.uni;

import java.util.function.Supplier;

import greycos.solver.core.impl.bavet.common.AbstractPrecomputeNode;
import greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.UniTuple;
import greycos.solver.core.impl.score.stream.bavet.common.BavetPrecomputeBuildHelper;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PrecomputeUniNode<A> extends AbstractPrecomputeNode<UniTuple<A>> {
  private final int outputStoreSize;

  public PrecomputeUniNode(
      Supplier<BavetPrecomputeBuildHelper<UniTuple<A>>> precomputeBuildHelperSupplier,
      int outputStoreSize,
      TupleLifecycle<UniTuple<A>> nextNodesTupleLifecycle,
      Class<?>[] sourceClasses) {
    super(precomputeBuildHelperSupplier, nextNodesTupleLifecycle, sourceClasses);
    this.outputStoreSize = outputStoreSize;
  }

  @Override
  protected UniTuple<A> remapTuple(UniTuple<A> tuple) {
    return UniTuple.of(tuple.getA(), outputStoreSize);
  }
}
