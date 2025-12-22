package ai.greycos.solver.core.impl.bavet.bi;

import static ai.greycos.solver.core.impl.bavet.bi.Group1Mapping0CollectorBiNode.createGroupKey;

import java.util.function.BiFunction;

import ai.greycos.solver.core.api.score.stream.bi.BiConstraintCollector;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.bavet.common.tuple.BiTuple;
import ai.greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;

public final class Group1Mapping1CollectorBiNode<OldA, OldB, A, B, ResultContainer_>
    extends AbstractGroupBiNode<OldA, OldB, BiTuple<A, B>, A, ResultContainer_, B> {

  private final int outputStoreSize;

  public Group1Mapping1CollectorBiNode(
      BiFunction<OldA, OldB, A> groupKeyMapping,
      int groupStoreIndex,
      int undoStoreIndex,
      BiConstraintCollector<OldA, OldB, ResultContainer_, B> collector,
      TupleLifecycle<BiTuple<A, B>> nextNodesTupleLifecycle,
      int outputStoreSize,
      EnvironmentMode environmentMode) {
    super(
        groupStoreIndex,
        undoStoreIndex,
        tuple -> createGroupKey(groupKeyMapping, tuple),
        collector,
        nextNodesTupleLifecycle,
        environmentMode);
    this.outputStoreSize = outputStoreSize;
  }

  @Override
  protected BiTuple<A, B> createOutTuple(A a) {
    return new BiTuple<>(a, null, outputStoreSize);
  }

  @Override
  protected void updateOutTupleToResult(BiTuple<A, B> outTuple, B b) {
    outTuple.factB = b;
  }
}
