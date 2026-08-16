package greycos.solver.core.impl.score.stream.bavet.common.bridge;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintFactory;
import greycos.solver.core.impl.score.stream.bavet.common.ConstraintNodeBuildHelper;
import greycos.solver.core.impl.score.stream.bavet.uni.BavetAbstractUniConstraintStream;

public final class BavetForeBridgeUniConstraintStream<Solution_, A>
    extends BavetAbstractUniConstraintStream<Solution_, A> {

  public BavetForeBridgeUniConstraintStream(
      BavetConstraintFactory<Solution_> constraintFactory,
      BavetAbstractUniConstraintStream<Solution_, A> parent) {
    super(constraintFactory, parent);
  }

  // ************************************************************************
  // Node creation
  // ************************************************************************

  @Override
  public <Score_ extends Score<Score_>> void buildNode(
      ConstraintNodeBuildHelper<Solution_, Score_> buildHelper) {
    // Do nothing. The child stream builds everything.
  }

  @Override
  public String toString() {
    return "Generic bridge";
  }
}
