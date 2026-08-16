package greycos.solver.core.impl.neighborhood.stream.enumerating.common;

import greycos.solver.core.impl.bavet.common.BavetStreamBinaryOperation;

import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface EnumeratingStreamBinaryOperation<Solution_>
    extends BavetStreamBinaryOperation<AbstractEnumeratingStream<Solution_>>
    permits IfExistsEnumeratingStream, JoinEnumeratingStream {}
