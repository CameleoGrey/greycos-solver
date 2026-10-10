package greycos.solver.core.impl.util;

import java.util.Iterator;
import java.util.function.Function;

import greycos.solver.core.impl.heuristic.selector.common.KnownExhaustionIterator;

public record MappingIterator<T, R>(Iterator<T> source, Function<T, R> mapper)
    implements KnownExhaustionIterator<R> {

  @Override
  public boolean isKnownExhausted() {
    return KnownExhaustionIterator.isExhausted(source);
  }

  @Override
  public boolean hasNext() {
    return source.hasNext();
  }

  @Override
  public R next() {
    return mapper.apply(source.next());
  }
}
