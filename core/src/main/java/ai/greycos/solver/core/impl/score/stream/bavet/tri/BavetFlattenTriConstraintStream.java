package ai.greycos.solver.core.impl.score.stream.bavet.tri;

import java.util.Objects;

import ai.greycos.solver.core.api.function.TriFunction;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.bavet.tri.FlattenTriNode;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintFactory;
import ai.greycos.solver.core.impl.score.stream.bavet.common.ConstraintNodeBuildHelper;
import ai.greycos.solver.core.impl.score.stream.bavet.common.bridge.BavetAftBridgeQuadConstraintStream;

final class BavetFlattenTriConstraintStream<Solution_, A, B, C, NewD>
    extends BavetAbstractTriConstraintStream<Solution_, A, B, C> {

  private final TriFunction<A, B, C, Iterable<NewD>> mappingFunction;
  private BavetAftBridgeQuadConstraintStream<Solution_, A, B, C, NewD> flattenStream;

  public BavetFlattenTriConstraintStream(
      BavetConstraintFactory<Solution_> constraintFactory,
      BavetAbstractTriConstraintStream<Solution_, A, B, C> parent,
      TriFunction<A, B, C, Iterable<NewD>> mappingFunction) {
    super(constraintFactory, parent);
    this.mappingFunction = mappingFunction;
  }

  public void setAftBridge(
      BavetAftBridgeQuadConstraintStream<Solution_, A, B, C, NewD> flattenStream) {
    this.flattenStream = flattenStream;
  }

  @Override
  public boolean guaranteesDistinct() {
    return false;
  }

  @Override
  public <Score_ extends Score<Score_>> void buildNode(
      ConstraintNodeBuildHelper<Solution_, Score_> buildHelper) {
    assertEmptyChildStreamList();
    var node =
        new FlattenTriNode<>(
            buildHelper.reserveTupleStoreIndex(parent.getTupleSource()),
            mappingFunction,
            buildHelper.getAggregatedTupleLifecycle(flattenStream.getChildStreamList()),
            buildHelper.extractTupleStoreSize(flattenStream));
    buildHelper.addNode(node, this);
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || getClass() != object.getClass()) {
      return false;
    }
    var that = (BavetFlattenTriConstraintStream<?, ?, ?, ?, ?>) object;
    return Objects.equals(parent, that.parent)
        && Objects.equals(mappingFunction, that.mappingFunction);
  }

  @Override
  public int hashCode() {
    return Objects.hash(parent, mappingFunction);
  }

  @Override
  public String toString() {
    return "Flatten()";
  }
}
