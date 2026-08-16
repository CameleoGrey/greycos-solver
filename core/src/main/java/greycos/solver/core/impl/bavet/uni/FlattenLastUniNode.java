package greycos.solver.core.impl.bavet.uni;

import java.util.function.Function;

import greycos.solver.core.impl.bavet.common.AbstractFlattenNode;
import greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.UniTuple;

public final class FlattenLastUniNode<A, NewA>
    extends AbstractFlattenNode<UniTuple<A>, UniTuple<NewA>, NewA> {

  private final Function<A, Iterable<NewA>> mappingFunction;

  private final int outputStoreSize;

  public FlattenLastUniNode(
      int flattenLastStoreIndex,
      Function<A, Iterable<NewA>> mappingFunction,
      TupleLifecycle<UniTuple<NewA>> nextNodesTupleLifecycle,
      int outputStoreSize) {
    super(flattenLastStoreIndex, nextNodesTupleLifecycle);
    this.mappingFunction = mappingFunction;
    this.outputStoreSize = outputStoreSize;
  }

  @Override
  protected UniTuple<NewA> createTuple(UniTuple<A> originalTuple, NewA item) {
    return UniTuple.of(item, outputStoreSize);
  }

  @Override
  protected Iterable<NewA> extractIterable(UniTuple<A> tuple) {
    return mappingFunction.apply(tuple.getA());
  }
}
