package ai.greycos.solver.core.impl.cotwin.valuerange.util;

import java.util.Iterator;

public abstract class ValueRangeIterator<S> implements Iterator<S> {

  @Override
  public void remove() {
    throw new UnsupportedOperationException("The optional operation remove() is not supported.");
  }
}
