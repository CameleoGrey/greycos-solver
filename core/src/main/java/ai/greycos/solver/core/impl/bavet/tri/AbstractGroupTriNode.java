package ai.greycos.solver.core.impl.bavet.tri;

import java.util.function.Function;

import ai.greycos.solver.core.api.score.stream.tri.TriConstraintCollector;
import ai.greycos.solver.core.api.score.stream.tri.TriConstraintCollectorAccumulator;
import ai.greycos.solver.core.api.score.stream.tri.TriConstraintCollectorValueHandle;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.bavet.common.AbstractGroupNode;
import ai.greycos.solver.core.impl.bavet.common.tuple.TriTuple;
import ai.greycos.solver.core.impl.bavet.common.tuple.Tuple;
import ai.greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

abstract class AbstractGroupTriNode<
        OldA, OldB, OldC, OutTuple_ extends Tuple, GroupKey_, ResultContainer_, Result_>
    extends AbstractGroupNode<
        TriTuple<OldA, OldB, OldC>, OutTuple_, GroupKey_, ResultContainer_, Result_> {

  private final int groupAccumulatorIndex;
  private final @Nullable TriConstraintCollectorAccumulator<ResultContainer_, OldA, OldB, OldC>
      incrementalAccumulator;

  protected AbstractGroupTriNode(
      int groupStoreIndex,
      int groupAccumulatorIndex,
      Function<TriTuple<OldA, OldB, OldC>, GroupKey_> groupKeyFunction,
      @NonNull TriConstraintCollector<OldA, OldB, OldC, ResultContainer_, Result_> collector,
      TupleLifecycle<OutTuple_> nextNodesTupleLifecycle,
      EnvironmentMode environmentMode) {
    super(
        groupStoreIndex,
        groupKeyFunction,
        collector.supplier(),
        collector.finisher(),
        nextNodesTupleLifecycle,
        environmentMode);
    this.groupAccumulatorIndex = groupAccumulatorIndex;
    this.incrementalAccumulator = collector.accumulator();
  }

  protected AbstractGroupTriNode(
      int groupStoreIndex,
      Function<TriTuple<OldA, OldB, OldC>, GroupKey_> groupKeyFunction,
      TupleLifecycle<OutTuple_> nextNodesTupleLifecycle,
      EnvironmentMode environmentMode) {
    super(groupStoreIndex, groupKeyFunction, nextNodesTupleLifecycle, environmentMode);
    this.groupAccumulatorIndex = -1;
    this.incrementalAccumulator = null;
  }

  @Override
  protected void groupInsert(ResultContainer_ resultContainer, TriTuple<OldA, OldB, OldC> tuple) {
    var groupElement = incrementalAccumulator.intoGroup(resultContainer);
    tuple.setStore(groupAccumulatorIndex, groupElement);
    groupElement.add(tuple.getA(), tuple.getB(), tuple.getC());
  }

  @Override
  protected void groupUpdate(ResultContainer_ resultContainer, TriTuple<OldA, OldB, OldC> tuple) {
    TriConstraintCollectorValueHandle<OldA, OldB, OldC> groupElement =
        tuple.getStore(groupAccumulatorIndex);
    groupElement.replaceWith(tuple.getA(), tuple.getB(), tuple.getC());
  }

  @Override
  protected void groupRetract(TriTuple<OldA, OldB, OldC> tuple) {
    TriConstraintCollectorValueHandle<OldA, OldB, OldC> groupElement =
        tuple.removeStore(groupAccumulatorIndex);
    groupElement.remove();
  }
}
