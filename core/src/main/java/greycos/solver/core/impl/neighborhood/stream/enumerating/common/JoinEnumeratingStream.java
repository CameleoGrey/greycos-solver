package greycos.solver.core.impl.neighborhood.stream.enumerating.common;

import greycos.solver.core.impl.bavet.common.TupleSource;

import org.jspecify.annotations.NullMarked;

@NullMarked
public non-sealed interface JoinEnumeratingStream<Solution_>
    extends EnumeratingStreamBinaryOperation<Solution_>, TupleSource {}
