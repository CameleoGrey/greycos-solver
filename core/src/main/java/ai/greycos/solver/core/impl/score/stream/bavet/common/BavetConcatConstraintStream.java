package ai.greycos.solver.core.impl.score.stream.bavet.common;

import ai.greycos.solver.core.impl.bavet.common.TupleSource;

public interface BavetConcatConstraintStream<Solution_>
    extends BavetConstraintStreamBinaryOperation<Solution_>, TupleSource {}
