package greycos.solver.core.impl.heuristic.selector;

import greycos.solver.core.impl.heuristic.selector.common.iterator.ListIterable;

public interface ListIterableSelector<Solution_, T>
    extends IterableSelector<Solution_, T>, ListIterable<T> {}
