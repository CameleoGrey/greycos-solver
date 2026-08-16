package greycos.solver.core.impl.score.stream.bavet.common.bridge;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintFactory;
import greycos.solver.core.impl.score.stream.bavet.common.ConstraintNodeBuildHelper;
import greycos.solver.core.impl.score.stream.bavet.quad.BavetAbstractQuadConstraintStream;

public final class BavetForeBridgeQuadConstraintStream<Solution_, A, B, C, D>
    extends BavetAbstractQuadConstraintStream<Solution_, A, B, C, D> {

  public BavetForeBridgeQuadConstraintStream(
      BavetConstraintFactory<Solution_> constraintFactory,
      BavetAbstractQuadConstraintStream<Solution_, A, B, C, D> parent) {
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

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

}
