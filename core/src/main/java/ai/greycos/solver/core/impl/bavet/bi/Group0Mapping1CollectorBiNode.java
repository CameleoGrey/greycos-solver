package ai.greycos.solver.core.impl.bavet.bi;

import ai.greycos.solver.core.api.score.stream.bi.BiConstraintCollector;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import ai.greycos.solver.core.impl.bavet.common.tuple.UniTuple;

public final class Group0Mapping1CollectorBiNode<OldA, OldB, A, ResultContainer_>
    extends AbstractGroupBiNode<OldA, OldB, UniTuple<A>, Void, ResultContainer_, A> {

  private final int outputStoreSize;

  public Group0Mapping1CollectorBiNode(
      int groupStoreIndex,
      int undoStoreIndex,
      BiConstraintCollector<OldA, OldB, ResultContainer_, A> collector,
      TupleLifecycle<UniTuple<A>> nextNodesTupleLifecycle,
      int outputStoreSize,
      EnvironmentMode environmentMode) {
    super(
        groupStoreIndex, undoStoreIndex, null, collector, nextNodesTupleLifecycle, environmentMode);
    this.outputStoreSize = outputStoreSize;
  }

  @Override
  protected UniTuple<A> createOutTuple(Void groupKey) {
    return UniTuple.of(outputStoreSize);
  }

  @Override
  protected void updateOutTupleToResult(UniTuple<A> outTuple, A a) {
    outTuple.setA(a);
  }
}
