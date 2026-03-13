package ai.greycos.solver.core.impl.bavet.bi;

import ai.greycos.solver.core.api.score.stream.ConstraintCollectors;
import ai.greycos.solver.core.api.score.stream.bi.BiConstraintCollector;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.bavet.common.tuple.BiTuple;
import ai.greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import ai.greycos.solver.core.impl.util.Pair;

public final class Group0Mapping2CollectorBiNode<
        OldA, OldB, A, B, ResultContainerA_, ResultContainerB_>
    extends AbstractGroupBiNode<OldA, OldB, BiTuple<A, B>, Void, Object, Pair<A, B>> {

  private final int outputStoreSize;

  public Group0Mapping2CollectorBiNode(
      int groupStoreIndex,
      int undoStoreIndex,
      BiConstraintCollector<OldA, OldB, ResultContainerA_, A> collectorA,
      BiConstraintCollector<OldA, OldB, ResultContainerB_, B> collectorB,
      TupleLifecycle<BiTuple<A, B>> nextNodesTupleLifecycle,
      int outputStoreSize,
      EnvironmentMode environmentMode) {
    super(
        groupStoreIndex,
        undoStoreIndex,
        null,
        mergeCollectors(collectorA, collectorB),
        nextNodesTupleLifecycle,
        environmentMode);
    this.outputStoreSize = outputStoreSize;
  }

  static <OldA, OldB, A, B, ResultContainerA_, ResultContainerB_>
      BiConstraintCollector<OldA, OldB, Object, Pair<A, B>> mergeCollectors(
          BiConstraintCollector<OldA, OldB, ResultContainerA_, A> collectorA,
          BiConstraintCollector<OldA, OldB, ResultContainerB_, B> collectorB) {
    return (BiConstraintCollector<OldA, OldB, Object, Pair<A, B>>)
        ConstraintCollectors.compose(collectorA, collectorB, Pair::new);
  }

  @Override
  protected BiTuple<A, B> createOutTuple(Void groupKey) {
    return BiTuple.of(outputStoreSize);
  }

  @Override
  protected void updateOutTupleToResult(BiTuple<A, B> outTuple, Pair<A, B> result) {
    outTuple.setA(result.key());
    outTuple.setB(result.value());
  }
}
