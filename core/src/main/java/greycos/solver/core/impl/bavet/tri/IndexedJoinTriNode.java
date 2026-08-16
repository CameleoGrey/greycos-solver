package greycos.solver.core.impl.bavet.tri;

import greycos.solver.core.api.function.TriPredicate;
import greycos.solver.core.impl.bavet.common.AbstractIndexedJoinNode;
import greycos.solver.core.impl.bavet.common.index.IndexerFactory;
import greycos.solver.core.impl.bavet.common.tuple.BiTuple;
import greycos.solver.core.impl.bavet.common.tuple.InOutTupleStorePositionTracker;
import greycos.solver.core.impl.bavet.common.tuple.TriTuple;
import greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.UniTuple;

public final class IndexedJoinTriNode<A, B, C>
    extends AbstractIndexedJoinNode<BiTuple<A, B>, C, TriTuple<A, B, C>> {

  private final TriPredicate<A, B, C> filtering;

  public IndexedJoinTriNode(
      IndexerFactory<C> indexerFactory,
      TupleLifecycle<TriTuple<A, B, C>> nextNodesTupleLifecycle,
      TriPredicate<A, B, C> filtering,
      InOutTupleStorePositionTracker tupleStorePositionTracker) {
    super(
        indexerFactory.buildBiLeftKeysExtractor(),
        indexerFactory,
        nextNodesTupleLifecycle,
        filtering != null,
        tupleStorePositionTracker);
    this.filtering = filtering;
  }

  @Override
  protected TriTuple<A, B, C> createOutTuple(BiTuple<A, B> leftTuple, UniTuple<C> rightTuple) {
    return TriTuple.of(
        leftTuple.getA(),
        leftTuple.getB(),
        rightTuple.getA(),
        outputStoreSizeTracker.computeStoreSize());
  }

  @Override
  protected void setOutTupleLeftFacts(TriTuple<A, B, C> outTuple, BiTuple<A, B> leftTuple) {
    outTuple.setA(leftTuple.getA());
    outTuple.setB(leftTuple.getB());
  }

  @Override
  protected void setOutTupleRightFact(TriTuple<A, B, C> outTuple, UniTuple<C> rightTuple) {
    outTuple.setC(rightTuple.getA());
  }

  @Override
  protected boolean testFiltering(BiTuple<A, B> leftTuple, UniTuple<C> rightTuple) {
    return filtering.test(leftTuple.getA(), leftTuple.getB(), rightTuple.getA());
  }
}
