package greycos.solver.core.impl.score.stream.bavet.common;

import greycos.solver.core.impl.bavet.common.TupleSource;

public interface BavetJoinConstraintStream<Solution_>
    extends BavetConstraintStreamBinaryOperation<Solution_>, TupleSource {}
