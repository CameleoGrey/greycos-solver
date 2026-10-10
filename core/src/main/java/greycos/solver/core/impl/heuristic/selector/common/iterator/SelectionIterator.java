package greycos.solver.core.impl.heuristic.selector.common.iterator;

import greycos.solver.core.impl.heuristic.selector.common.KnownExhaustionIterator;

public abstract class SelectionIterator<S> implements KnownExhaustionIterator<S> {

  @Override
  public boolean isKnownExhausted() {
    return false;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("The optional operation remove() is not supported.");
  }
}
