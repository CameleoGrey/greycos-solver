package ai.greycos.solver.core.impl.score.stream.bavet.uni;

import java.util.Objects;
import java.util.function.Function;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.bavet.uni.FlattenUniNode;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintFactory;
import ai.greycos.solver.core.impl.score.stream.bavet.common.ConstraintNodeBuildHelper;
import ai.greycos.solver.core.impl.score.stream.bavet.common.bridge.BavetAftBridgeBiConstraintStream;

final class BavetFlattenUniConstraintStream<Solution_, A, NewB>
    extends BavetAbstractUniConstraintStream<Solution_, A> {

  private final Function<A, Iterable<NewB>> mappingFunction;
  private BavetAftBridgeBiConstraintStream<Solution_, A, NewB> flattenStream;

  public BavetFlattenUniConstraintStream(
      BavetConstraintFactory<Solution_> constraintFactory,
      BavetAbstractUniConstraintStream<Solution_, A> parent,
      Function<A, Iterable<NewB>> mappingFunction) {
    super(constraintFactory, parent);
    this.mappingFunction = mappingFunction;
  }

  public void setAftBridge(BavetAftBridgeBiConstraintStream<Solution_, A, NewB> flattenStream) {
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
        new FlattenUniNode<>(
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
    var that = (BavetFlattenUniConstraintStream<?, ?, ?>) object;
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
