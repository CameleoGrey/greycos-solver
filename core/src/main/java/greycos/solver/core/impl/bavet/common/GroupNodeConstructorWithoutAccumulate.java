package greycos.solver.core.impl.bavet.common;

import java.util.List;
import java.util.function.IntSupplier;

import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.bavet.common.tuple.Tuple;
import greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;

final class GroupNodeConstructorWithoutAccumulate<Tuple_ extends Tuple>
    extends AbstractGroupNodeConstructor<Tuple_> {

  private final NodeConstructorWithoutAccumulate<Tuple_> nodeConstructorFunction;

  public GroupNodeConstructorWithoutAccumulate(
      Object equalityKey, NodeConstructorWithoutAccumulate<Tuple_> nodeConstructorFunction) {
    super(equalityKey);
    this.nodeConstructorFunction = nodeConstructorFunction;
  }

  @Override
  public <Stream_ extends BavetStream> void build(
      AbstractNodeBuildHelper<Stream_> buildHelper,
      Stream_ parentTupleSource,
      Stream_ aftStream,
      List<Stream_> aftStreamChildList,
      Stream_ bridgeStream,
      EnvironmentMode environmentMode) {
    IntSupplier storeIndexReserver = () -> buildHelper.reserveTupleStoreIndex(parentTupleSource);
    TupleLifecycle<Tuple_> tupleLifecycle =
        buildHelper.getAggregatedTupleLifecycle(aftStreamChildList);
    var outputStoreSize = buildHelper.extractTupleStoreSize(aftStream);
    var node =
        nodeConstructorFunction.apply(
            storeIndexReserver, tupleLifecycle, outputStoreSize, environmentMode);
    buildHelper.addNode(node, bridgeStream);
  }
}
