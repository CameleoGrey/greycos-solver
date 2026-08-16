package greycos.solver.core.impl.score.stream.bavet.common;

import greycos.solver.core.impl.bavet.common.BavetAbstractConstraintStream;
import greycos.solver.core.impl.bavet.common.BavetStreamBinaryOperation;
import greycos.solver.core.impl.score.stream.bavet.common.bridge.BavetForeBridgeUniConstraintStream;

public interface BavetConstraintStreamBinaryOperation<Solution_>
    extends BavetStreamBinaryOperation<BavetAbstractConstraintStream<Solution_>> {
  /**
   * @return An instance of {@link BavetForeBridgeUniConstraintStream}.
   */
  BavetAbstractConstraintStream<Solution_> getLeftParent();

  /**
   * @return An instance of {@link BavetForeBridgeUniConstraintStream}.
   */
  BavetAbstractConstraintStream<Solution_> getRightParent();
}
